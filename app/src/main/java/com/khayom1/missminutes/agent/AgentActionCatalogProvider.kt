package com.khayom1.missminutes.agent

import com.khayom1.missminutes.plugins.PluginToolDescriptor

/** Supplies the connected plugin tool catalog to the model/agent request builder. */
interface AgentActionCatalogProvider {
    fun availableTools(): List<PluginToolDescriptor>
}
