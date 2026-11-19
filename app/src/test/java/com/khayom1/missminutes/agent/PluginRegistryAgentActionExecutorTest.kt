package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.MutablePluginConsentStore
import com.khayom1.missminutes.plugins.PluginAccessChecker
import com.khayom1.missminutes.plugins.PluginAccessDecision
import com.khayom1.missminutes.plugins.PluginActionHandler
import com.khayom1.missminutes.plugins.PluginActionOutput
import com.khayom1.missminutes.plugins.PluginActionSpec
import com.khayom1.missminutes.plugins.PluginAuditLog
import com.khayom1.missminutes.plugins.PluginCapability
import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionStatus
import com.khayom1.missminutes.plugins.PluginFieldSpec
import com.khayom1.missminutes.plugins.PluginManifest
import com.khayom1.missminutes.plugins.PluginRegistry
import com.khayom1.missminutes.plugins.PluginValue
import com.khayom1.missminutes.plugins.PluginValueType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRegistryAgentActionExecutorTest {
    private class ConsentStore : MutablePluginConsentStore {
        private val grants = mutableMapOf<String, Boolean>()

        override fun isGranted(pluginId: String): Boolean = grants[pluginId] == true

        override fun setGranted(pluginId: String, granted: Boolean) {
            grants[pluginId] = granted
        }
    }

    private val arguments = mapOf(
        "locale" to PluginFieldSpec(
            name = "locale",
            type = PluginValueType.STRING,
            maxLength = 2,
            allowedValues = setOf("tg", "ru", "en")
        )
    )

    private fun registry(consent: ConsentStore): PluginRegistry {
        val handler = object : PluginActionHandler {
            override val actionId = "read_status"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override val argumentSchema = arguments

            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val locale = (arguments["locale"] as PluginValue.Text).value
                return PluginActionOutput(true, "status:$locale")
            }
        }
        return PluginRegistry(
            handlers = listOf(handler),
            consentStore = consent,
            auditLog = PluginAuditLog {},
            accessChecker = PluginAccessChecker { _, _ -> PluginAccessDecision.Granted }
        )
    }

    private fun manifest() = PluginManifest(
        id = "agent_status",
        version = "1.0.0",
        displayName = "Agent status",
        description = "A test action bound to the production plugin registry.",
        capabilities = setOf(PluginCapability.DEVICE_INFORMATION),
        actions = listOf(
            PluginActionSpec(
                id = "read_status",
                capability = PluginCapability.DEVICE_INFORMATION,
                arguments = arguments
            )
        )
    )

    @Test
    fun agentToolCatalogAndBothExecutionPathsUseTheSameConsentGatedRegistry() {
        val consent = ConsentStore()
        val registry = registry(consent)
        assertTrue(registry.install(manifest()))
        val executor = PluginRegistryAgentActionExecutor(registry)

        assertTrue(executor.availableTools().isEmpty())
        assertTrue(registry.connect("agent_status"))
        val catalog = executor.availableTools()
        assertEquals(1, catalog.size)
        val descriptor = catalog.single()

        val toolCallResult = executor.executeToolCall(
            descriptor.toolName,
            """{"locale":"tg"}""",
            userConfirmed = false
        )
        assertEquals(PluginExecutionStatus.SUCCEEDED, toolCallResult.status)
        assertEquals("status:tg", toolCallResult.message)

        val directResult = executor.execute(
            PluginExecutionRequest(
                pluginId = "agent_status",
                actionId = "read_status",
                arguments = mapOf("locale" to PluginValue.Text("ru"))
            ),
            idempotencyKey = "task_1:read_status"
        )
        assertEquals(PluginExecutionStatus.SUCCEEDED, directResult.status)
        assertEquals("status:ru", directResult.message)

        assertTrue(registry.revokeConsent("agent_status"))
        assertTrue(executor.availableTools().isEmpty())
        assertEquals(
            PluginExecutionStatus.ACTION_NOT_ALLOWED,
            executor.executeToolCall(
                descriptor.toolName,
                """{"locale":"en"}""",
                userConfirmed = false
            ).status
        )
        assertEquals(
            PluginExecutionStatus.PLUGIN_NOT_CONNECTED,
            executor.execute(
                PluginExecutionRequest("agent_status", "read_status"),
                idempotencyKey = "task_1:read_status:retry"
            ).status
        )
    }
}
