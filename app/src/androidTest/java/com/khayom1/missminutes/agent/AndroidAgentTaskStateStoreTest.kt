package com.khayom1.missminutes.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.khayom1.missminutes.memory.AndroidKeystoreConversationCipher
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAgentTaskStateStoreTest {
    @Test
    fun persistsTaskGraphAsKeystoreEncryptedCiphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("miss_minutes_agent_tasks", Context.MODE_PRIVATE)
            .edit().clear().commit()
        val store = AndroidAgentTaskStateStore(context, AndroidKeystoreConversationCipher())
        val plan = AgentTaskPlan(
            id = "persisted_task",
            nodes = listOf(
                AgentTaskNode(
                    id = "read_device",
                    action = AgentAction(
                        request = PluginExecutionRequest(
                            pluginId = "device_status",
                            actionId = "device_info",
                            arguments = mapOf("locale" to PluginValue.Text("tg"))
                        ),
                        approvalRequired = true
                    )
                )
            ),
            maxSteps = 1,
            deadlineAtMillis = System.currentTimeMillis() + 60_000
        )
        val state = AgentTaskState(
            plan = plan,
            nodes = mapOf(
                "read_device" to AgentNodeState(
                    nodeId = "read_device",
                    status = AgentNodeStatus.WAITING_APPROVAL,
                    idempotencyKey = "persisted_task:read_device"
                )
            ),
            status = AgentTaskStatus.WAITING_APPROVAL,
            updatedAtMillis = System.currentTimeMillis()
        )

        assertEquals(true, store.create(state))
        val restored = store.load("persisted_task")
        assertNotNull(restored)
        assertEquals(state, restored)
        assertEquals(listOf(state), store.all())
        val ciphertext = context.getSharedPreferences("miss_minutes_agent_tasks", Context.MODE_PRIVATE)
            .getString("encrypted_task_graphs_v1", null)
        assertNotNull(ciphertext)
        assertFalse(ciphertext!!.contains("persisted_task"))
        assertFalse(ciphertext.contains("device_status"))
    }
}
