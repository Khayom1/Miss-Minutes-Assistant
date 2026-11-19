package com.khayom1.missminutes.plugins

import com.khayom1.missminutes.device.DeviceToolAuthorization
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.DeviceToolHost
import com.khayom1.missminutes.device.DeviceToolAuditLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInDevicePluginManifestTest {
    private fun registry(): PluginRegistry {
        val engine = DeviceToolEngine(
            authorization = DeviceToolAuthorization { true },
            host = DeviceToolHost { "ok" },
            auditLog = DeviceToolAuditLog {}
        )
        return PluginRegistry(
            handlers = AndroidDeviceStatusPlugin.handlers(engine) + AndroidAccessibilityAutomationPlugin.handlers(),
            consentStore = PluginConsentStore { false },
            auditLog = PluginAuditLog {},
            accessChecker = PluginAccessChecker { _, _ -> PluginAccessDecision.Granted }
        )
    }

    @Test
    fun productionAndroidDeviceManifestInstallsAgainstItsCompiledHandlers() {
        val registry = registry()
        val manifest = AndroidDeviceStatusPlugin.manifest()

        assertTrue(registry.install(manifest))
        assertTrue(registry.install(AndroidAccessibilityAutomationPlugin.manifest()))
        assertEquals(
            setOf(AndroidDeviceStatusPlugin.PLUGIN_ID, AndroidAccessibilityAutomationPlugin.PLUGIN_ID),
            registry.installedPlugins().map { it.id }.toSet()
        )
        assertEquals(
            (manifest.actions + AndroidAccessibilityAutomationPlugin.manifest().actions)
                .map { it.id }.toSet(),
            registry.toolCatalog().map { it.actionId }.toSet()
        )
        val tools = registry.toolCatalog().associateBy { it.actionId }
        assertFalse(requireNotNull(tools["device_info"]).sideEffect)
        assertTrue(requireNotNull(tools["create_reminder"]).sideEffect)
        assertTrue(requireNotNull(tools["set_alarm"]).sideEffect)
        assertEquals(PluginCapability.ALARM_SCHEDULE, requireNotNull(tools["set_alarm"]).capability)
        assertTrue(requireNotNull(tools["open_app"]).sideEffect)
        assertFalse(requireNotNull(tools["inspect_screen"]).sideEffect)
        assertTrue(requireNotNull(tools["click_text"]).sideEffect)
        assertTrue(requireNotNull(tools["click_confirmed_text"]).sideEffect)
        assertTrue(requireNotNull(tools["type_text"]).sideEffect)
        assertTrue(requireNotNull(tools["scroll_screen"]).sideEffect)
        assertTrue(requireNotNull(tools["press_back"]).sideEffect)
    }

    @Test
    fun duplicateActionIdsAreRejectedWithoutPartiallyInstallingManifest() {
        val registry = registry()
        val manifest = AndroidDeviceStatusPlugin.manifest()
        val duplicate = manifest.copy(actions = manifest.actions + manifest.actions.first())

        assertFalse(runCatching { registry.install(duplicate) }.isSuccess)
        assertTrue(registry.installedPlugins().isEmpty())
    }

    @Test
    fun unsupportedCompiledActionIsRejectedWithoutPartiallyInstallingManifest() {
        val registry = registry()
        val manifest = AndroidDeviceStatusPlugin.manifest()
        val unsupported = manifest.copy(
            actions = manifest.actions + PluginActionSpec(
                id = "execute_shell",
                capability = PluginCapability.DEVICE_INFORMATION
            ),
            capabilities = manifest.capabilities
        )

        assertFalse(runCatching { registry.install(unsupported) }.isSuccess)
        assertTrue(registry.installedPlugins().isEmpty())
    }

    @Test
    fun malformedPluginIdentityIsRejectedWithoutPartiallyInstallingManifest() {
        val registry = registry()
        val malformed = AndroidDeviceStatusPlugin.manifest().copy(id = "Invalid Plugin ID")

        assertFalse(runCatching { registry.install(malformed) }.isSuccess)
        assertTrue(registry.installedPlugins().isEmpty())
    }
}
