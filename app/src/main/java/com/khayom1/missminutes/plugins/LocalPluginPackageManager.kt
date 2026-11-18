package com.khayom1.missminutes.plugins

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Persistence boundary for local declarative plugin manifests. */
interface PluginManifestPersistence {
    fun load(): List<PluginManifest>
    fun replace(manifests: Collection<PluginManifest>)
}

data class PluginPackageImportReport(
    val installedIds: List<String>,
    val rejectedIds: List<String>
)

/**
 * Imports and exports metadata-only plugin packages.
 *
 * Imported packages never carry consent. A package can only bind to handlers
 * already compiled into the application, and an existing plugin id is never
 * silently replaced by an imported package.
 */
class LocalPluginPackageManager(
    private val registry: PluginRegistry,
    private val consentStore: MutablePluginConsentStore,
    private val persistence: PluginManifestPersistence,
    private val json: Json = Json
) {
    /**
     * Creates a metadata-only plugin from executable actions already compiled
     * into this app. It is never connected implicitly and never contains code.
     */
    @Synchronized
    fun createLocalPlugin(
        id: String,
        version: String,
        displayName: String,
        description: String,
        actionIds: Collection<String>
    ): PluginManifest {
        val selectedIds = actionIds.toList()
        require(selectedIds.isNotEmpty()) { "Select at least one verified action." }
        require(selectedIds.size <= 16 && selectedIds.distinct().size == selectedIds.size) {
            "Plugin actions must be unique and within the 16-action limit."
        }
        require(id !in registry.installedPlugins().map { it.id }) {
            "A plugin with this id is already installed."
        }

        val compiled = registry.compiledActionCatalog().associateBy { it.id }
        val actions = selectedIds.map { actionId ->
            compiled[actionId] ?: throw IllegalArgumentException(
                "Action '$actionId' is not implemented by this app version."
            )
        }
        val manifest = PluginManifest(
            id = id.trim(),
            version = version.trim(),
            displayName = displayName.trim(),
            description = description.trim(),
            capabilities = actions.map { it.capability }.toSet(),
            actions = actions,
            maxCallsPerMinute = 6,
            maxExecutionMillis = 1_500L
        )

        // Validate and register the closed-world manifest before persisting it.
        val previous = registry.installedPlugins()
        consentStore.setGranted(manifest.id, false)
        try {
            check(registry.install(manifest)) { "Plugin id is already installed." }
            persistence.replace(registry.installedPlugins())
        } catch (error: Exception) {
            runCatching { registry.uninstall(manifest.id) }
            runCatching { persistence.replace(previous) }
            throw IllegalStateException(
                "Plugin creation failed; no connected plugin was created.",
                error
            )
        }
        return manifest
    }

    fun exportPackage(pluginId: String): ByteArray {
        val manifest = registry.installedPlugins().firstOrNull { it.id == pluginId }
            ?: throw IllegalArgumentException("The selected plugin is not installed.")
        return PluginManifestArchive.encode(listOf(manifest))
            .toString()
            .toByteArray(Charsets.UTF_8)
    }

    /** Parse and validate a package without installing it or changing consent. */
    fun previewImport(bytes: ByteArray): List<PluginManifest> {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PACKAGE_BYTES) {
            "Plugin package is empty or exceeds the size limit."
        }
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        val root = json.parseToJsonElement(decoded.toString()).jsonObject
        return PluginManifestArchive.decode(root).also {
            require(it.isNotEmpty()) { "Plugin package does not contain any manifests." }
        }
    }

    /**
     * Installs only new ids, explicitly resets their persisted consent, then
     * commits the resulting allowlisted registry snapshot to local storage.
     */
    @Synchronized
    fun importManifests(manifests: List<PluginManifest>): PluginPackageImportReport {
        require(manifests.isNotEmpty()) { "No plugin manifests were selected." }
        PluginManifestArchive.encode(manifests)

        val previous = registry.installedPlugins()
        val existingIds = previous.map { it.id }.toSet()
        val installed = mutableListOf<PluginManifest>()
        val rejected = mutableListOf<String>()

        try {
            for (manifest in manifests) {
                if (manifest.id in existingIds) {
                    rejected += manifest.id
                    continue
                }
                consentStore.setGranted(manifest.id, false)
                try {
                    if (registry.install(manifest)) {
                        installed += manifest
                    } else {
                        rejected += manifest.id
                    }
                } catch (_: IllegalArgumentException) {
                    rejected += manifest.id
                }
            }
            persistence.replace(registry.installedPlugins())
        } catch (error: Exception) {
            installed.forEach { registry.uninstall(it.id) }
            runCatching { persistence.replace(previous) }
            throw IllegalStateException("Plugin package import failed; newly installed plugins were rolled back.", error)
        }

        return PluginPackageImportReport(
            installedIds = installed.map { it.id },
            rejectedIds = rejected
        )
    }

    /**
     * Uninstall first revokes durable consent. If manifest persistence fails,
     * the manifest is restored in memory but remains disconnected.
     */
    @Synchronized
    fun uninstall(pluginId: String): Boolean {
        val existing = registry.installedPlugins().firstOrNull { it.id == pluginId } ?: return false
        if (!registry.uninstall(pluginId)) return false
        return try {
            persistence.replace(registry.installedPlugins())
            true
        } catch (_: Exception) {
            runCatching { registry.install(existing) }
            false
        }
    }

    @Synchronized
    fun connect(pluginId: String): Boolean = registry.connect(pluginId)

    @Synchronized
    fun revoke(pluginId: String): Boolean = registry.revokeConsent(pluginId)

    companion object {
        const val MAX_PACKAGE_BYTES = 256_000
    }
}
