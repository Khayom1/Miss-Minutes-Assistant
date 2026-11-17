package com.khayom1.missminutes.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPluginPackageManagerTest {
    private class ConsentStore : MutablePluginConsentStore {
        private val grants = mutableMapOf<String, Boolean>()
        var failForPluginId: String? = null
        override fun isGranted(pluginId: String): Boolean = grants[pluginId] == true
        override fun setGranted(pluginId: String, granted: Boolean) {
            if (pluginId == failForPluginId) {
                failForPluginId = null
                throw IllegalStateException("simulated consent storage failure")
            }
            grants[pluginId] = granted
        }
    }

    private class ManifestStore(
        initial: Collection<PluginManifest> = emptyList()
    ) : PluginManifestPersistence {
        var manifests = initial.toList()
        var failNextReplace = false

        override fun load(): List<PluginManifest> = manifests

        override fun replace(manifests: Collection<PluginManifest>) {
            if (failNextReplace) {
                failNextReplace = false
                throw IllegalStateException("simulated storage failure")
            }
            this.manifests = manifests.toList()
        }
    }

    private fun manifest(displayName: String = "Local test plugin") = PluginManifest(
        id = "local_test_plugin",
        version = "1.0.0",
        displayName = displayName,
        description = "Read a bounded local status value.",
        capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
        actions = listOf(
            PluginActionSpec(
                id = "read_info",
                capability = PluginCapability.DEVICE_INFORMATION
            )
        )
    )

    private fun registry(consent: ConsentStore) = PluginRegistry(
        handlers = listOf(object : PluginActionHandler {
            override val actionId = "read_info"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override fun execute(arguments: Map<String, PluginValue>) =
                PluginActionOutput(true, "read-only")
        }),
        consentStore = consent,
        auditLog = PluginAuditLog {},
        accessChecker = PluginAccessChecker { _, _ -> PluginAccessDecision.Granted }
    )

    private data class Fixture(
        val manager: LocalPluginPackageManager,
        val registry: PluginRegistry,
        val consent: ConsentStore,
        val store: ManifestStore
    )

    private fun fixture(initial: Collection<PluginManifest> = emptyList()): Fixture {
        val consent = ConsentStore()
        val registry = registry(consent)
        initial.forEach(registry::install)
        val store = ManifestStore(initial)
        return Fixture(
            LocalPluginPackageManager(registry, consent, store),
            registry,
            consent,
            store
        )
    }

    @Test
    fun exportContainsOnlyDeclarativeManifestMetadata() {
        val fixture = fixture(listOf(manifest()))
        val exported = fixture.manager.exportPackage("local_test_plugin")
        val json = exported.toString(Charsets.UTF_8)

        assertEquals(listOf(manifest()), fixture.manager.previewImport(exported))
        assertTrue(json.contains("local_test_plugin"))
        assertFalse(json.contains("consent"))
        assertFalse(json.contains("credential"))
        assertFalse(json.contains("apiKey"))
        assertFalse(json.contains("permissions"))
        assertFalse(json.contains("handlerCode"))
    }

    @Test
    fun previewDoesNotInstallOrChangeExistingConsent() {
        val fixture = fixture()
        fixture.consent.setGranted("local_test_plugin", true)

        val preview = fixture.manager.previewImport(
            PluginManifestArchive.encode(listOf(manifest())).toString().toByteArray()
        )

        assertEquals(listOf(manifest()), preview)
        assertTrue(fixture.registry.installedPlugins().isEmpty())
        assertTrue(fixture.consent.isGranted("local_test_plugin"))
    }

    @Test
    fun importedPluginIsPersistedButAlwaysStartsDisconnected() {
        val fixture = fixture()
        // Simulate a stale grant left by a previous installation of the same id.
        fixture.consent.setGranted("local_test_plugin", true)

        val report = fixture.manager.importManifests(listOf(manifest()))

        assertEquals(listOf("local_test_plugin"), report.installedIds)
        assertTrue(report.rejectedIds.isEmpty())
        assertEquals(listOf(manifest()), fixture.registry.installedPlugins())
        assertEquals(listOf(manifest()), fixture.store.load())
        assertFalse(fixture.consent.isGranted("local_test_plugin"))
    }

    @Test
    fun unsupportedManifestCannotBindToAnUncompiledHandler() {
        val fixture = fixture()
        val unsupported = manifest().copy(
            actions = listOf(
                PluginActionSpec(
                    id = "calendar_read",
                    capability = PluginCapability.CALENDAR_READ
                )
            ),
            capabilities = setOf(PluginCapability.CALENDAR_READ)
        )

        val report = fixture.manager.importManifests(listOf(unsupported))

        assertTrue(report.installedIds.isEmpty())
        assertEquals(listOf("local_test_plugin"), report.rejectedIds)
        assertTrue(fixture.registry.installedPlugins().isEmpty())
        assertTrue(fixture.store.load().isEmpty())
    }

    @Test
    fun duplicatePluginIdNeverOverwritesManifestOrExistingConsent() {
        val original = manifest("Original")
        val fixture = fixture(listOf(original))
        assertTrue(fixture.manager.connect(original.id))

        val report = fixture.manager.importManifests(listOf(manifest("Imported replacement")))

        assertTrue(report.installedIds.isEmpty())
        assertEquals(listOf(original.id), report.rejectedIds)
        assertEquals(listOf(original), fixture.registry.installedPlugins())
        assertTrue(fixture.consent.isGranted(original.id))
    }

    @Test
    fun uninstallRevokesGrantAndRemovesManifestFromRegistryAndStorage() {
        val fixture = fixture()
        fixture.manager.importManifests(listOf(manifest()))
        assertTrue(fixture.manager.connect("local_test_plugin"))

        assertTrue(fixture.manager.uninstall("local_test_plugin"))

        assertFalse(fixture.consent.isGranted("local_test_plugin"))
        assertTrue(fixture.registry.installedPlugins().isEmpty())
        assertTrue(fixture.store.load().isEmpty())
        assertFalse(fixture.manager.uninstall("local_test_plugin"))
    }

    @Test
    fun uninstallStorageFailureRestoresManifestButKeepsItDisconnected() {
        val fixture = fixture(listOf(manifest()))
        assertTrue(fixture.manager.connect("local_test_plugin"))
        fixture.store.failNextReplace = true

        assertFalse(fixture.manager.uninstall("local_test_plugin"))

        assertEquals(listOf(manifest()), fixture.registry.installedPlugins())
        assertEquals(listOf(manifest()), fixture.store.load())
        assertFalse(fixture.consent.isGranted("local_test_plugin"))
    }

    @Test
    fun failedImportPersistenceRollsBackNewManifestAndConsent() {
        val fixture = fixture()
        fixture.store.failNextReplace = true

        val error = runCatching {
            fixture.manager.importManifests(listOf(manifest()))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(fixture.registry.installedPlugins().isEmpty())
        assertTrue(fixture.store.load().isEmpty())
        assertFalse(fixture.consent.isGranted("local_test_plugin"))
    }

    @Test
    fun consentStorageFailureRollsBackEarlierManifestsInSamePackage() {
        val fixture = fixture()
        val second = manifest().copy(id = "second_local_plugin")
        fixture.consent.failForPluginId = second.id

        val error = runCatching {
            fixture.manager.importManifests(listOf(manifest(), second))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(fixture.registry.installedPlugins().isEmpty())
        assertTrue(fixture.store.load().isEmpty())
        assertFalse(fixture.consent.isGranted("local_test_plugin"))
        assertFalse(fixture.consent.isGranted(second.id))
    }

    @Test
    fun previewRejectsMalformedAndOversizedPackages() {
        val fixture = fixture()

        assertTrue(runCatching { fixture.manager.previewImport(byteArrayOf()) }.isFailure)
        assertTrue(runCatching { fixture.manager.previewImport("not-json".toByteArray()) }.isFailure)
        assertTrue(
            runCatching {
                fixture.manager.previewImport(byteArrayOf(0x7B, 0x22, 0xFF.toByte(), 0x22, 0x3A, 0x31, 0x7D))
            }.isFailure
        )
        assertTrue(
            runCatching {
                fixture.manager.previewImport(ByteArray(LocalPluginPackageManager.MAX_PACKAGE_BYTES + 1))
            }.isFailure
        )
    }
}
