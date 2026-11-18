package com.khayom1.missminutes.plugins

import com.khayom1.missminutes.device.DeviceToolCommand
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.DeviceToolRequest

object AndroidDeviceStatusPlugin {
    const val PLUGIN_ID = "android_device_status"

    fun manifest() = PluginManifest(
        id = PLUGIN_ID,
        version = "1.1.0",
        displayName = "Android device tools",
        description = "Read device status, calendar, contacts summary, set alarms and create reminders. Write actions require explicit confirmation.",
        capabilities = setOf(
            PluginCapability.DEVICE_INFORMATION,
            PluginCapability.BATTERY_STATUS,
            PluginCapability.CALENDAR,
            PluginCapability.CONTACTS,
            PluginCapability.ALARM,
            PluginCapability.APP_LAUNCH
        ),
        actions = listOf(
            PluginActionSpec("device_info", PluginCapability.DEVICE_INFORMATION),
            PluginActionSpec("battery_status", PluginCapability.BATTERY_STATUS),
            PluginActionSpec("current_time", PluginCapability.DEVICE_INFORMATION),
            PluginActionSpec("calendar_summary", PluginCapability.CALENDAR),
            PluginActionSpec("create_reminder", PluginCapability.CALENDAR),
            PluginActionSpec("contacts_summary", PluginCapability.CONTACTS),
            PluginActionSpec("set_alarm", PluginCapability.ALARM),
            PluginActionSpec("open_app", PluginCapability.APP_LAUNCH)
        ),
        maxCallsPerMinute = 10,
        maxExecutionMillis = 2_500L
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
        },
        object : PluginActionHandler {
            override val actionId = "current_time"
            override val capability = PluginCapability.DEVICE_INFORMATION
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadCurrentTime))
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "calendar_summary"
            override val capability = PluginCapability.CALENDAR
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadCalendarSummary))
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "create_reminder"
            override val capability = PluginCapability.CALENDAR
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val title = arguments["title"]?.asString()?.takeIf { it.isNotBlank() }
                    ?: return PluginActionOutput(false, "title is required")
                val description = arguments["description"]?.asString().orEmpty()
                val start = arguments["startEpochMillis"]?.asLong()
                    ?: return PluginActionOutput(false, "startEpochMillis is required")
                val result = engine.execute(
                    DeviceToolRequest(
                        DeviceToolCommand.CreateReminder(title, description, start),
                        userConfirmed = true // agent path already gates confirmation
                    )
                )
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "contacts_summary"
            override val capability = PluginCapability.CONTACTS
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadContactsSummary))
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "set_alarm"
            override val capability = PluginCapability.ALARM
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val hour = arguments["hour"]?.asInt() ?: return PluginActionOutput(false, "hour is required")
                val minute = arguments["minute"]?.asInt() ?: return PluginActionOutput(false, "minute is required")
                val message = arguments["message"]?.asString() ?: "Miss Minutes reminder"
                val result = engine.execute(
                    DeviceToolRequest(
                        DeviceToolCommand.SetAlarm(hour, minute, message),
                        userConfirmed = true
                    )
                )
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "open_app"
            override val capability = PluginCapability.APP_LAUNCH
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val packageName = arguments["packageName"]?.asString()?.takeIf { it.isNotBlank() }
                    ?: return PluginActionOutput(false, "packageName is required")
                val result = engine.execute(
                    DeviceToolRequest(
                        DeviceToolCommand.OpenAppByPackage(packageName),
                        userConfirmed = true
                    )
                )
                return PluginActionOutput(result.success, result.message)
            }
        }
    )
}
