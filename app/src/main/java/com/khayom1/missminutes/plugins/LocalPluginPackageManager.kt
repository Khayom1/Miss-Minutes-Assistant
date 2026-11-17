package com.khayom1.missminutes.plugins

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
        val root = json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
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
