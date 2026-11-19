package com.khayom1.missminutes.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khayom1.missminutes.plugins.AndroidDeviceStatusPlugin
import com.khayom1.missminutes.plugins.AndroidPluginConsentStore
import com.khayom1.missminutes.plugins.AndroidPluginManifestStore
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.device.DeviceToolCapability
import com.khayom1.missminutes.device.SharedPreferencesDeviceToolAuthorization
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentTaskRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun shippedPluginManifestsRespectRuntimeValidationLimits() {
        val accessibility = com.khayom1.missminutes.plugins.AndroidAccessibilityAutomationPlugin.manifest()
        val device = AndroidDeviceStatusPlugin.manifest()

        assertTrue(accessibility.description.length <= 240)
        assertTrue(device.description.length <= 240)
        assertTrue(accessibility.actions.isNotEmpty())
        assertTrue(device.actions.isNotEmpty())

        val registry = AgentTaskRuntime.createPluginRegistry(context)
        val installedIds = registry.installedPlugins().map { it.id }.toSet()
        assertTrue(installedIds.contains(AndroidDeviceStatusPlugin.PLUGIN_ID))
        assertTrue(installedIds.contains(com.khayom1.missminutes.plugins.AndroidAccessibilityAutomationPlugin.PLUGIN_ID))
        assertTrue(registry.toolCatalog().any { it.pluginId == AndroidDeviceStatusPlugin.PLUGIN_ID })
        assertTrue(registry.toolCatalog().any {
            it.pluginId == com.khayom1.missminutes.plugins.AndroidAccessibilityAutomationPlugin.PLUGIN_ID
        })
    }

    @Test
    fun uiOrchestratorUsesTheExactPluginRegistrySuppliedByTheUi() {
        val registry = AgentTaskRuntime.createPluginRegistry(context)
        val manifest = AndroidDeviceStatusPlugin.manifest().copy(
            id = "in_memory_runtime_plugin",
            displayName = "In-memory runtime plugin",
            description = "An ephemeral manifest proves the injected registry is shared."
        )
        val consent = AndroidPluginConsentStore(context)
        val deviceAuthorization = SharedPreferencesDeviceToolAuthorization(context)
        consent.setGranted(manifest.id, false)
        deviceAuthorization.setGranted(DeviceToolCapability.DEVICE_INFO, true)

        try {
            assertTrue(registry.install(manifest))
            assertTrue(registry.connect(manifest.id))
            val orchestrator = AgentTaskRuntime.get(context, registry)

            assertTrue(orchestrator.availablePluginTools().any { it.pluginId == manifest.id })
            assertTrue(registry.revokeConsent(manifest.id))
            assertFalse(orchestrator.availablePluginTools().any { it.pluginId == manifest.id })
        } finally {
            consent.setGranted(manifest.id, false)
            deviceAuthorization.setGranted(DeviceToolCapability.DEVICE_INFO, false)
        }
    }

    @Test
    fun runtimeIsRebuiltForEachUiOrWorkerEntry() {
        val first = AgentTaskRuntime.get(context)
        val second = AgentTaskRuntime.get(context)
        assertNotSame(first, second)
    }

    @Test
    fun backgroundRuntimeLoadsPersistedManifestButNeverRestoresConsent() {
        val builtIn = AndroidDeviceStatusPlugin.manifest()
        val imported = builtIn.copy(
            id = "persisted_device_status",
            displayName = "Persisted device status",
            description = "Declarative manifest backed only by compiled handlers."
        )
        val invalid = imported.copy(
            id = "uncompiled_manifest",
            actions = imported.actions.map { it.copy(id = "uncompiled_action") }
        )
        val manifests = AndroidPluginManifestStore(context)
        val consent = AndroidPluginConsentStore(context)
        manifests.replace(listOf(imported, invalid))
        consent.setGranted(imported.id, false)

        try {
            val registry = AgentTaskRuntime.createPluginRegistry(context)
            assertNotNull(registry.installedPlugins().firstOrNull { it.id == imported.id })
            assertFalse(registry.installedPlugins().any { it.id == invalid.id })
            assertFalse(consent.isGranted(imported.id))
            val result = registry.execute(
                PluginExecutionRequest(imported.id, imported.actions.first().id)
            )
            assertFalse(result.success)
        } finally {
            manifests.replace(emptyList())
            consent.setGranted(imported.id, false)
        }
    }

    @Test
    fun pluginConnectionGrantPersistsAcrossRuntimeRecreationAndRevocation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val consentStore = AndroidPluginConsentStore(context)
        val pluginId = AndroidDeviceStatusPlugin.manifest().id
        consentStore.setGranted(pluginId, false)
        try {
            val first = AgentTaskRuntime.createPluginRegistry(context)
            assertTrue(first.connect(pluginId))

            val second = AgentTaskRuntime.createPluginRegistry(context)
            assertTrue(second.connectedTools().any { it.pluginId == pluginId })
            assertTrue(second.revokeConsent(pluginId))

            val third = AgentTaskRuntime.createPluginRegistry(context)
            assertFalse(third.connectedTools().any { it.pluginId == pluginId })
        } finally {
            consentStore.setGranted(pluginId, false)
        }
    }
}
