package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginExecutionResult
import com.khayom1.missminutes.plugins.PluginToolDescriptor

/** Supplies connected tools and dispatches calls through the closed-world registry. */
interface AgentActionCatalogProvider {
    fun availableTools(): List<PluginToolDescriptor>

    fun executeToolCall(
        toolName: String,
        argumentsJson: String,
        userConfirmed: Boolean = false
    ): PluginExecutionResult
}
