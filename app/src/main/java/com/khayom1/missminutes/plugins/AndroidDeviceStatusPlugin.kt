package com.khayom1.missminutes.plugins

import com.khayom1.missminutes.device.DeviceToolCommand
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.DeviceToolRequest

object AndroidDeviceStatusPlugin {
    const val PLUGIN_ID = "android_device_status"

    fun manifest() = PluginManifest(
        id = PLUGIN_ID,
        version = "1.0.0",
        displayName = "Android device status",
        description = "Read-only device and battery information using existing Android permissions.",
        capabilities = setOf(
            PluginCapability.DEVICE_INFORMATION,
            PluginCapability.BATTERY_STATUS,
            PluginCapability.ANDROID_SETTINGS_OPEN,
            PluginCapability.ANDROID_TEXT_SHARE
        ),
        actions = listOf(
            PluginActionSpec("device_info", PluginCapability.DEVICE_INFORMATION),
            PluginActionSpec("battery_status", PluginCapability.BATTERY_STATUS),
            PluginActionSpec(
                "open_wifi_settings",
                PluginCapability.ANDROID_SETTINGS_OPEN,
                requiresConfirmation = true
            ),
            PluginActionSpec(
                "open_app_settings",
                PluginCapability.ANDROID_SETTINGS_OPEN,
                requiresConfirmation = true
            ),
            PluginActionSpec(
                "share_text",
                PluginCapability.ANDROID_TEXT_SHARE,
                arguments = mapOf(
                    "text" to PluginFieldSpec(
                        name = "text",
                        type = PluginValueType.STRING,
                        maxLength = 1_000
                    )
                ),
                requiresConfirmation = true
            )
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
,
        object : PluginActionHandler {
            override val actionId = "open_wifi_settings"
            override val capability = PluginCapability.ANDROID_SETTINGS_OPEN
            override val requiresConfirmation = true
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(
                    DeviceToolRequest(DeviceToolCommand.OpenWifiSettings, userConfirmed = true)
                )
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "open_app_settings"
            override val capability = PluginCapability.ANDROID_SETTINGS_OPEN
            override val requiresConfirmation = true
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(
                    DeviceToolRequest(DeviceToolCommand.OpenAppSettings, userConfirmed = true)
                )
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "share_text"
            override val capability = PluginCapability.ANDROID_TEXT_SHARE
            override val requiresConfirmation = true
            override val argumentSchema = mapOf(
                "text" to PluginFieldSpec(
                    name = "text",
                    type = PluginValueType.STRING,
                    maxLength = 1_000
                )
            )
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val text = (arguments["text"] as? PluginValue.Text)?.value
                    ?: return PluginActionOutput(false, "Text is required.")
                val result = engine.execute(
                    DeviceToolRequest(DeviceToolCommand.ShareText(text), userConfirmed = true)
                )
                return PluginActionOutput(result.success, result.message)
            }
        }
    )
}
