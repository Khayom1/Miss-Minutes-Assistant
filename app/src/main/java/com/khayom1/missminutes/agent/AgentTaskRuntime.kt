package com.khayom1.missminutes.agent

import android.content.Context
import com.khayom1.missminutes.device.AndroidDeviceToolHost
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.FileDeviceToolAuditLog
import com.khayom1.missminutes.device.SharedPreferencesDeviceToolAuthorization
import com.khayom1.missminutes.memory.AndroidKeystoreConversationCipher
import com.khayom1.missminutes.plugins.AndroidDeviceStatusPlugin
import com.khayom1.missminutes.plugins.AndroidPluginConsentStore
import com.khayom1.missminutes.plugins.FilePluginAuditLog
import com.khayom1.missminutes.plugins.PluginRegistry

/** Builds the same restricted, consent-gated runtime for both UI and background work. */
internal object AgentTaskRuntime {
    @Volatile
    private var cached: AgentOrchestrator? = null

    fun get(context: Context): AgentOrchestrator = cached ?: synchronized(this) {
        cached ?: build(context).also { cached = it }
    }

    private fun build(context: Context): AgentOrchestrator {
        val appContext = context.applicationContext
        val authorization = SharedPreferencesDeviceToolAuthorization(appContext)
        val deviceEngine = DeviceToolEngine(
            authorization = authorization,
            host = AndroidDeviceToolHost(appContext),
            auditLog = FileDeviceToolAuditLog(appContext)
        )
        val consentStore = AndroidPluginConsentStore(appContext)
        val registry = PluginRegistry(
            handlers = AndroidDeviceStatusPlugin.handlers(deviceEngine),
            consentStore = consentStore,
            auditLog = FilePluginAuditLog(appContext)
        ).apply { install(AndroidDeviceStatusPlugin.manifest()) }
        val store = AndroidAgentTaskStateStore(
            appContext,
            AndroidKeystoreConversationCipher("miss_minutes_agent_tasks_aes_v1")
        )
        return AgentOrchestrator(store, PluginRegistryAgentActionExecutor(registry))
    }
}
