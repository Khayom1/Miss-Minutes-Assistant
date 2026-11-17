package com.khayom1.missminutes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.khayom1.missminutes.agent.AgentAction
import com.khayom1.missminutes.agent.AgentOrchestrator
import com.khayom1.missminutes.agent.AgentTaskNode
import com.khayom1.missminutes.agent.AgentTaskPlan
import com.khayom1.missminutes.agent.AgentTaskState
import com.khayom1.missminutes.agent.AgentTaskStatus
import com.khayom1.missminutes.agent.AgentTaskWorker
import com.khayom1.missminutes.plugins.AndroidDeviceStatusPlugin
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
internal fun AgentTaskPanel(
    orchestrator: AgentOrchestrator,
    language: AppLanguage,
    enabled: Boolean
) {
    val context = LocalContext.current
    var taskState by remember { mutableStateOf<AgentTaskState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(orchestrator) {
        taskState = withContext(Dispatchers.IO) { orchestrator.latestTask() }
    }

    LaunchedEffect(orchestrator, taskState?.plan?.id, taskState?.status) {
        val id = taskState?.plan?.id ?: return@LaunchedEffect
        while (true) {
            val latest = withContext(Dispatchers.IO) { orchestrator.state(id) }
            taskState = latest
            if (latest == null || latest.status !in setOf(AgentTaskStatus.READY, AgentTaskStatus.RUNNING)) {
                break
            }
            delay(500)
        }
    }

    fun perform(action: suspend () -> AgentTaskState?) {
        scope.launch {
            busy = true
            error = null
            try {
                taskState = withContext(Dispatchers.IO) { action() }
            } catch (_: Exception) {
                error = when (language) {
                    AppLanguage.Tajik -> "Вазифа иҷро нашуд. Иҷозат ва сабти вазифаро санҷед."
                    AppLanguage.Russian -> "Не удалось выполнить задачу. Проверьте разрешения и сохранение состояния."
                    AppLanguage.English -> "Task failed. Check permissions and task storage."
                }
            } finally {
                busy = false
            }
        }
    }

    val current = taskState
    val terminal = current?.status?.let {
        it in setOf(
            AgentTaskStatus.SUCCEEDED,
            AgentTaskStatus.FAILED,
            AgentTaskStatus.CANCELLED,
            AgentTaskStatus.EXPIRED,
            AgentTaskStatus.RECOVERY_REQUIRED
        )
    } == true
    val title = when (language) {
        AppLanguage.Tajik -> "Иҷрои вазифаҳои AI"
        AppLanguage.Russian -> "Выполнение задач ИИ"
        AppLanguage.English -> "AI task execution"
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("agent_task_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(
            when (language) {
                AppLanguage.Tajik -> "Вазифаи ташхисӣ дар WorkManager иҷро мешавад. Ҳолат рамзгузорӣ ва баъди бозоғозии барнома барқарор мешавад."
                AppLanguage.Russian -> "Диагностика выполняется через WorkManager. Зашифрованное состояние восстанавливается после перезапуска приложения."
                AppLanguage.English -> "Diagnostics run through WorkManager. Encrypted task state is restored after app restart."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            enabled = enabled && !busy && (current == null || terminal),
            onClick = {
                perform {
                    val id = UUID.randomUUID().toString()
                    val plan = AgentTaskPlan(
                        id = id,
                        nodes = listOf(
                            AgentTaskNode(
                                id = "device_info",
                                action = AgentAction(
                                    PluginExecutionRequest(AndroidDeviceStatusPlugin.PLUGIN_ID, "device_info")
                                )
                            ),
                            AgentTaskNode(
                                id = "battery_status",
                                dependsOn = setOf("device_info"),
                                action = AgentAction(
                                    PluginExecutionRequest(AndroidDeviceStatusPlugin.PLUGIN_ID, "battery_status")
                                )
                            )
                        ),
                        maxSteps = 2,
                        deadlineAtMillis = System.currentTimeMillis() + 120_000L
                    )
                    val created = orchestrator.create(plan)
                    AgentTaskWorker.enqueue(context, id)
                    created
                }
            }
        ) {
            Text(
                when (language) {
                    AppLanguage.Tajik -> "Оғози худкор"
                    AppLanguage.Russian -> "Запустить автоматически"
                    AppLanguage.English -> "Run automatically"
                }
            )
        }
        if (current != null) {
            Text(
                text = "${taskStatusLabel(current.status, language)} · ${current.completedSteps}/${current.plan.maxSteps}",
                modifier = Modifier.testTag("agent_task_status"),
                style = MaterialTheme.typography.bodyMedium
            )
            current.plan.nodes.forEach { node ->
                val nodeState = current.nodes.getValue(node.id)
                Text(
                    text = "${node.id}: ${nodeStatusLabel(nodeState.status, language)}" +
                        (nodeState.message?.let { " — $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !busy && !terminal && current.status != AgentTaskStatus.PAUSED,
                    onClick = {
                        perform {
                            val paused = orchestrator.pause(current.plan.id)
                            AgentTaskWorker.cancel(context, current.plan.id)
                            paused
                        }
                    }
                ) {
                    Text(if (language == AppLanguage.Tajik) "Таваққуф" else if (language == AppLanguage.Russian) "Пауза" else "Pause")
                }
                TextButton(
                    enabled = !busy && current.status == AgentTaskStatus.PAUSED,
                    onClick = {
                        perform {
                            val resumed = orchestrator.resume(current.plan.id)
                            AgentTaskWorker.enqueue(context, current.plan.id, replaceExisting = true)
                            resumed
                        }
                    }
                ) {
                    Text(if (language == AppLanguage.Tajik) "Идома" else if (language == AppLanguage.Russian) "Продолжить" else "Resume")
                }
                TextButton(
                    enabled = !busy && !terminal,
                    onClick = {
                        perform {
                            val cancelled = orchestrator.cancel(current.plan.id)
                            AgentTaskWorker.cancel(context, current.plan.id)
                            cancelled
                        }
                    }
                ) {
                    Text(if (language == AppLanguage.Tajik) "Бекор" else if (language == AppLanguage.Russian) "Отмена" else "Cancel")
                }
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!enabled) {
            Text(
                when (language) {
                    AppLanguage.Tajik -> "Барои оғоз ризоияти плагин ва иҷозатҳои маълумоти дастгоҳу батареяро фаъол кунед."
                    AppLanguage.Russian -> "Для запуска включите согласие на плагин и разрешения на сведения об устройстве и батарее."
                    AppLanguage.English -> "Enable plugin consent and device/battery permissions to start."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
