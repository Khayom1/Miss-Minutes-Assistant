package com.khayom1.missminutes.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.khayom1.missminutes.plugins.AndroidDeviceStatusPlugin
import com.khayom1.missminutes.plugins.AndroidPluginConsentStore
import com.khayom1.missminutes.plugins.AndroidPluginManifestStore
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AgentTaskWorkerTest {
    @Test
    fun workerExecutesPersistedTaskAndFailsClosedWithoutPluginConsent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AndroidPluginConsentStore(context).revokeAll()
        val orchestrator = AgentTaskRuntime.get(context)
        val taskId = "worker_${UUID.randomUUID().toString().replace("-", "")}"
        orchestrator.create(
            AgentTaskPlan(
                id = taskId,
                nodes = listOf(
                    AgentTaskNode(
                        id = "device_info",
                        action = AgentAction(
                            PluginExecutionRequest(AndroidDeviceStatusPlugin.PLUGIN_ID, "device_info")
                        )
                    )
                ),
                maxSteps = 1,
                deadlineAtMillis = System.currentTimeMillis() + 60_000L
            )
        )

        val worker = TestListenableWorkerBuilder<AgentTaskWorker>(context)
            .setInputData(workDataOf(AgentTaskWorker.KEY_TASK_ID to taskId))
            .build()
        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(AgentTaskStatus.FAILED, orchestrator.state(taskId)?.status)
        assertNotNull(orchestrator.latestTask())
    }

    @Test
    fun workerReloadsImportedManifestAndStillRequiresExplicitConsent() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builtIn = AndroidDeviceStatusPlugin.manifest()
        val imported = builtIn.copy(
            id = "worker_imported_device",
            displayName = "Worker imported device",
            description = "Persisted declarative manifest for worker integration test."
        )
        val manifests = AndroidPluginManifestStore(context)
        val consents = AndroidPluginConsentStore(context)
        manifests.replace(listOf(imported))
        consents.setGranted(imported.id, false)
        try {
            val orchestrator = AgentTaskRuntime.get(context)
            val taskId = "worker_imported_${UUID.randomUUID().toString().replace("-", "")}"
            orchestrator.create(
                AgentTaskPlan(
                    id = taskId,
                    nodes = listOf(
                        AgentTaskNode(
                            id = "imported_device_info",
                            action = AgentAction(
                                PluginExecutionRequest(imported.id, imported.actions.first().id)
                            )
                        )
                    ),
                    maxSteps = 1,
                    deadlineAtMillis = System.currentTimeMillis() + 60_000L
                )
            )
            val worker = TestListenableWorkerBuilder<AgentTaskWorker>(context)
                .setInputData(workDataOf(AgentTaskWorker.KEY_TASK_ID to taskId))
                .build()
            val result = worker.doWork()

            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(AgentTaskStatus.FAILED, orchestrator.state(taskId)?.status)
            assertFalse(consents.isGranted(imported.id))
        } finally {
            manifests.replace(emptyList())
            consents.setGranted(imported.id, false)
        }
    }
}
