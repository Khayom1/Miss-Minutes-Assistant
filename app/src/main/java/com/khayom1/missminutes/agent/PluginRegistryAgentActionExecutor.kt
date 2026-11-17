package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionResult
import com.khayom1.missminutes.plugins.PluginRegistry
import com.khayom1.missminutes.plugins.PluginToolDescriptor

/**
 * Adapter to the P6 closed-world registry. The currently shipped device-status
 * plugin exposes read-only capabilities; any future mutating handler must add
 * an idempotency-aware adapter before it can be scheduled by an agent.
 */
class PluginRegistryAgentActionExecutor(
    private val registry: PluginRegistry
) : AgentActionExecutor, AgentActionCatalogProvider {
    override fun availableTools(): List<PluginToolDescriptor> = registry.connectedTools()

    @Suppress("UNUSED_PARAMETER")
    override fun execute(
        request: PluginExecutionRequest,
        idempotencyKey: String
    ): PluginExecutionResult = registry.execute(request)
}
