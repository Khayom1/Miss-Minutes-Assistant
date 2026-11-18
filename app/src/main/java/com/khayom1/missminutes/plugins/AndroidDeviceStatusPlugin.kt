package com.khayom1.missminutes.plugins

import com.khayom1.missminutes.device.DeviceToolCommand
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.DeviceToolRequest

/**
 * The currently exposed built-in plugin surface is read-only.
 * Settings launches and sharing remain absent until the fresh, request-bound
 * approval UI is integrated into the same mainline execution path.
 */
object AndroidDeviceStatusPlugin {
    const val PLUGIN_ID = "android_device_status"

    fun manifest() = PluginManifest(
        id = PLUGIN_ID,
        version = "1.0.0",
        displayName = "Android device status",
        description = "Read-only device and battery information using existing Android permissions.",
        capabilities = setOf(
            PluginCapability.DEVICE_INFORMATION,
            PluginCapability.BATTERY_STATUS
        ),
        actions = listOf(
            PluginActionSpec("device_info", PluginCapability.DEVICE_INFORMATION),
            PluginActionSpec("battery_status", PluginCapability.BATTERY_STATUS)
        ),
        maxCallsPerMinute = 6,
        maxExecutionMillis = 1_500L
    )

    fun handlers(engine: DeviceToolEngine): List<PluginActionHandler> = listOf(
        object : PluginActionHandler {
            override val actionId = "device_info"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadDeviceInfo))
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "battery_status"
            override val capability = PluginCapability.BATTERY_STATUS
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadBatteryStatus))
                return PluginActionOutput(result.success, result.message)
            }
        }
    )
}
