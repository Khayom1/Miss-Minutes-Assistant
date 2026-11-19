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
    fun workerProgressIncludesStepBudgetStatusAndActiveNode() {
        val state = AgentTaskState(
            plan = AgentTaskPlan(
                id = "progress_task",
                nodes = listOf(
                    AgentTaskNode(
                        id = "first",
                        action = AgentAction(
                            PluginExecutionRequest(AndroidDeviceStatusPlugin.PLUGIN_ID, "device_info")
                        )
                    ),
                    AgentTaskNode(
                        id = "second",
                        dependsOn = setOf("first"),
                        action = AgentAction(
                            PluginExecutionRequest(AndroidDeviceStatusPlugin.PLUGIN_ID, "device_info")
                        )
                    )
                ),
                maxSteps = 2,
                deadlineAtMillis = System.currentTimeMillis() + 60_000L
            ),
            nodes = emptyMap(),
            status = AgentTaskStatus.RUNNING,
            completedSteps = 1,
            updatedAtMillis = System.currentTimeMillis()
        )

        val progress = AgentTaskWorker.progressData(state, "second")
        assertEquals(1, progress.getInt(AgentTaskWorker.KEY_PROGRESS_COMPLETED, -1))
        assertEquals(2, progress.getInt(AgentTaskWorker.KEY_PROGRESS_TOTAL, -1))
        assertEquals(AgentTaskStatus.RUNNING.name, progress.getString(AgentTaskWorker.KEY_PROGRESS_STATUS))
        assertEquals("second", progress.getString(AgentTaskWorker.KEY_PROGRESS_NODE))
    }

    @Test
    fun workerWaitsForApprovalAndDoesNotExecuteWithoutPluginConsent() = runBlocking {
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
        val state = orchestrator.state(taskId)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(AgentTaskStatus.WAITING_APPROVAL, state?.status)
        assertEquals(AgentNodeStatus.WAITING_APPROVAL, state?.nodes?.get("device_info")?.status)
        assertEquals(0, state?.completedSteps)
        assertFalse(AndroidPluginConsentStore(context).isGranted(AndroidDeviceStatusPlugin.PLUGIN_ID))
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
            val state = orchestrator.state(taskId)

            assertTrue(result is ListenableWorker.Result.Success)
            assertEquals(AgentTaskStatus.WAITING_APPROVAL, state?.status)
            assertEquals(
                AgentNodeStatus.WAITING_APPROVAL,
                state?.nodes?.get("imported_device_info")?.status
            )
            assertEquals(0, state?.completedSteps)
            assertFalse(consents.isGranted(imported.id))
        } finally {
            manifests.replace(emptyList())
            consents.setGranted(imported.id, false)
        }
    }
}
