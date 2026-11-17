package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionResult
import com.khayom1.missminutes.plugins.PluginRegistry
import com.khayom1.missminutes.plugins.PluginToolCallDispatcher
import com.khayom1.missminutes.plugins.PluginToolDescriptor

/**
 * Adapter to the closed-world registry. Only compiled handlers can execute;
 * the currently shipped Android status actions are read-only.
 */
class PluginRegistryAgentActionExecutor(
    private val registry: PluginRegistry
) : AgentActionExecutor, AgentActionCatalogProvider {
    private val toolDispatcher = PluginToolCallDispatcher(registry)

    override fun availableTools(): List<PluginToolDescriptor> = registry.connectedTools()

    override fun executeToolCall(
        toolName: String,
        argumentsJson: String,
        userConfirmed: Boolean
    ): PluginExecutionResult = toolDispatcher.execute(toolName, argumentsJson, userConfirmed)

    @Suppress("UNUSED_PARAMETER")
    override fun execute(
        request: PluginExecutionRequest,
        idempotencyKey: String
    ): PluginExecutionResult = registry.execute(request)
}
