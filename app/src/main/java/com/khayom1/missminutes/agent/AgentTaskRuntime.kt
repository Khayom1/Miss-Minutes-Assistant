package com.khayom1.missminutes.agent

import android.content.Context
import com.khayom1.missminutes.device.AndroidDeviceToolHost
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.FileDeviceToolAuditLog
import com.khayom1.missminutes.device.SharedPreferencesDeviceToolAuthorization
import com.khayom1.missminutes.memory.AndroidKeystoreConversationCipher
import com.khayom1.missminutes.plugins.AndroidDeviceStatusPlugin
import com.khayom1.missminutes.plugins.AndroidAccessibilityAutomationPlugin
import com.khayom1.missminutes.plugins.AndroidPluginConsentStore
import com.khayom1.missminutes.plugins.AndroidPluginManifestStore
import com.khayom1.missminutes.plugins.AndroidPluginAccessChecker
import com.khayom1.missminutes.plugins.FilePluginAuditLog
import com.khayom1.missminutes.plugins.PluginRegistry

/** Builds the same restricted, consent-gated runtime for both UI and background work. */
internal object AgentTaskRuntime {
    // Rebuild the lightweight runtime for each UI/worker entry. Task state is
    // durable, while rebuilding refreshes manifests imported since the last run.
    fun get(context: Context): AgentOrchestrator =
        get(context, createPluginRegistry(context.applicationContext))

    /**
     * UI callers inject their existing registry so plugin management, AI tool
     * discovery and dispatch share one in-process source of truth. Background
     * workers may still build a fresh registry from persisted manifests.
     */
    fun get(context: Context, registry: PluginRegistry): AgentOrchestrator {
        val appContext = context.applicationContext
        val store = AndroidAgentTaskStateStore(
            appContext,
            AndroidKeystoreConversationCipher("miss_minutes_agent_tasks_aes_v1")
        )
        return AgentOrchestrator(store, PluginRegistryAgentActionExecutor(registry))
    }

    /** Shared closed-world registry factory, also exercised by Android instrumentation tests. */
    internal fun createPluginRegistry(context: Context): PluginRegistry {
        val appContext = context.applicationContext
        val authorization = SharedPreferencesDeviceToolAuthorization(appContext)
        val deviceEngine = DeviceToolEngine(
            authorization = authorization,
            host = AndroidDeviceToolHost(appContext),
            auditLog = FileDeviceToolAuditLog(appContext)
        )
        val consentStore = AndroidPluginConsentStore(appContext)
        return PluginRegistry(
            handlers = AndroidDeviceStatusPlugin.handlers(deviceEngine) +
                AndroidAccessibilityAutomationPlugin.handlers(),
            consentStore = consentStore,
            auditLog = FilePluginAuditLog(appContext),
            accessChecker = AndroidPluginAccessChecker(appContext)
        ).apply {
            install(AndroidDeviceStatusPlugin.manifest())
            install(AndroidAccessibilityAutomationPlugin.manifest())
            // Imported manifests remain declarative and can only bind to compiled
            // handlers. A persisted manifest never restores a user's consent grant.
            AndroidPluginManifestStore(appContext).load().forEach { manifest ->
                runCatching { install(manifest) }
            }
        }
    }
}
