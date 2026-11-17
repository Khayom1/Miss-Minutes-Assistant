package com.khayom1.missminutes.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khayom1.missminutes.plugins.AndroidDeviceStatusPlugin
import com.khayom1.missminutes.plugins.AndroidPluginConsentStore
import com.khayom1.missminutes.plugins.AndroidPluginManifestStore
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgentTaskRuntimeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

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
}
