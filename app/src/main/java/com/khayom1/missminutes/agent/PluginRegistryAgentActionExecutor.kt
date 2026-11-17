package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginExecutionRequest
import com.khayom1.missminutes.plugins.PluginExecutionResult
import com.khayom1.missminutes.plugins.PluginRegistry

/**
 * Adapter to the P6 closed-world registry. The currently shipped device-status
 * plugin exposes read-only capabilities; any future mutating handler must add
 * an idempotency-aware adapter before it can be scheduled by an agent.
 */
class PluginRegistryAgentActionExecutor(
    private val registry: PluginRegistry
) : AgentActionExecutor {
    @Suppress("UNUSED_PARAMETER")
    override fun execute(
        request: PluginExecutionRequest,
        idempotencyKey: String
    ): PluginExecutionResult = registry.execute(request)
}
