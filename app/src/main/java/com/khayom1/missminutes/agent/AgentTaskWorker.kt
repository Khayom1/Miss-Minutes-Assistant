package com.khayom1.missminutes.agent

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes only the bounded task graph already persisted by the UI.
 * WorkManager owns scheduling/retries; interrupted RUNNING actions are never replayed.
 */
class AgentTaskWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getString(KEY_TASK_ID)
            ?.takeIf { it.matches(TASK_ID_PATTERN) }
            ?: return@withContext Result.failure()

        try {
            val orchestrator = AgentTaskRuntime.get(applicationContext)
            orchestrator.state(taskId)?.let { state ->
                setProgress(progressData(state, null))
            }
            repeat(MAX_ADVANCES_PER_RUN) {
                if (isStopped) return@withContext Result.success()
                when (val result = orchestrator.advance(taskId)) {
                    is AgentAdvanceResult.Progress -> {
                        setProgress(progressData(result.state, result.nodeId))
                        if (result.state.status != AgentTaskStatus.READY &&
                            result.state.status != AgentTaskStatus.RUNNING
                        ) return@withContext Result.success()
                    }
                    is AgentAdvanceResult.BlockedForApproval -> {
                        setProgress(progressData(result.state, result.nodeId))
                        return@withContext Result.success()
                    }
                    is AgentAdvanceResult.Finished -> {
                        setProgress(progressData(result.state, null))
                        return@withContext Result.success()
                    }
                    is AgentAdvanceResult.Rejected -> return@withContext Result.failure()
                }
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val KEY_TASK_ID = "agent_task_id"
        private const val MAX_ADVANCES_PER_RUN = 24
        const val KEY_PROGRESS_COMPLETED = "completed_steps"
        const val KEY_PROGRESS_TOTAL = "total_steps"
        const val KEY_PROGRESS_STATUS = "task_status"
        const val KEY_PROGRESS_NODE = "active_node"
        private val TASK_ID_PATTERN = Regex("^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,63}$")

        internal fun progressData(state: AgentTaskState, activeNodeId: String?) = workDataOf(
            KEY_PROGRESS_COMPLETED to state.completedSteps,
            KEY_PROGRESS_TOTAL to state.plan.nodes.size,
            KEY_PROGRESS_STATUS to state.status.name,
            KEY_PROGRESS_NODE to activeNodeId
        )

        fun enqueue(context: Context, taskId: String, replaceExisting: Boolean = false) {
            require(TASK_ID_PATTERN.matches(taskId))
            val request = OneTimeWorkRequestBuilder<AgentTaskWorker>()
                .setInputData(workDataOf(KEY_TASK_ID to taskId))
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                uniqueWorkName(taskId),
                if (replaceExisting) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context, taskId: String) {
            if (TASK_ID_PATTERN.matches(taskId)) {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(uniqueWorkName(taskId))
            }
        }

        private fun uniqueWorkName(taskId: String) = "miss_minutes_agent_$taskId"
    }
}
