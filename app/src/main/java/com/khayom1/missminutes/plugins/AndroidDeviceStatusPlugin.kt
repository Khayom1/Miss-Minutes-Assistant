package com.khayom1.missminutes.plugins

import com.khayom1.missminutes.device.DeviceToolCommand
import com.khayom1.missminutes.device.DeviceToolEngine
import com.khayom1.missminutes.device.DeviceToolRequest

object AndroidDeviceStatusPlugin {
    const val PLUGIN_ID = "android_device_status"

    fun manifest() = PluginManifest(
        id = PLUGIN_ID,
        version = "1.2.0",
        displayName = "Android device tools",
        description = "Read device status, calendar, contacts summary, set alarms and create reminders. Write actions require explicit confirmation.",
        capabilities = setOf(
            PluginCapability.DEVICE_INFORMATION,
            PluginCapability.BATTERY_STATUS,
            PluginCapability.CALENDAR_READ,
            PluginCapability.CALENDAR_WRITE,
            PluginCapability.ALARM_SCHEDULE,
            PluginCapability.CONTACTS_READ
        ),
        actions = listOf(
            PluginActionSpec("device_info", PluginCapability.DEVICE_INFORMATION),
            PluginActionSpec("battery_status", PluginCapability.BATTERY_STATUS),
            PluginActionSpec("current_time", PluginCapability.DEVICE_INFORMATION),
            PluginActionSpec("calendar_summary", PluginCapability.CALENDAR_READ),
            PluginActionSpec(
                "create_reminder",
                PluginCapability.CALENDAR_WRITE,
                arguments = mapOf(
                    "title" to PluginFieldSpec("title", PluginValueType.STRING, required = true, maxLength = 200),
                    "description" to PluginFieldSpec("description", PluginValueType.STRING, required = false, maxLength = 500),
                    "startEpochMillis" to PluginFieldSpec("startEpochMillis", PluginValueType.INTEGER, required = true)
                ),
                requiresConfirmation = true
            ),
            PluginActionSpec("contacts_summary", PluginCapability.CONTACTS_READ),
            PluginActionSpec(
                "set_alarm",
                PluginCapability.ALARM_SCHEDULE,
                arguments = mapOf(
                    "hour" to PluginFieldSpec("hour", PluginValueType.INTEGER, required = true, minimum = 0, maximum = 23),
                    "minute" to PluginFieldSpec("minute", PluginValueType.INTEGER, required = true, minimum = 0, maximum = 59),
                    "message" to PluginFieldSpec("message", PluginValueType.STRING, required = false, maxLength = 100)
                ),
                requiresConfirmation = true
            ),
            PluginActionSpec(
                "open_app",
                PluginCapability.DEVICE_INFORMATION,
                arguments = mapOf(
                    "packageName" to PluginFieldSpec("packageName", PluginValueType.STRING, required = true, maxLength = 200)
                ),
                requiresConfirmation = true
            )
        ),
        maxCallsPerMinute = 10,
        maxExecutionMillis = 2_500L
    )

    private fun argumentsFor(actionId: String): Map<String, PluginFieldSpec> =
        manifest().actions.first { it.id == actionId }.arguments

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
            override val capability = PluginCapability.CALENDAR_READ
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadCalendarSummary))
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "create_reminder"
            override val sideEffect = true
            override val argumentSchema = argumentsFor("create_reminder")
            override val capability = PluginCapability.CALENDAR_WRITE
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val title = (arguments["title"] as? PluginValue.Text)?.value?.takeIf { it.isNotBlank() }
                    ?: return PluginActionOutput(false, "title is required")
                val description = (arguments["description"] as? PluginValue.Text)?.value.orEmpty()
                val start = (arguments["startEpochMillis"] as? PluginValue.Number)?.value
                    ?: return PluginActionOutput(false, "startEpochMillis is required")
                val result = engine.execute(
                    DeviceToolRequest(
                        DeviceToolCommand.CreateReminder(title, description, start),
                        userConfirmed = true
                    )
                )
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "contacts_summary"
            override val capability = PluginCapability.CONTACTS_READ
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val result = engine.execute(DeviceToolRequest(DeviceToolCommand.ReadContactsSummary))
                return PluginActionOutput(result.success, result.message)
            }
        },
        object : PluginActionHandler {
            override val actionId = "set_alarm"
            override val sideEffect = true
            override val argumentSchema = argumentsFor("set_alarm")
            override val capability = PluginCapability.ALARM_SCHEDULE
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val hour = (arguments["hour"] as? PluginValue.Number)?.value?.toInt()
                    ?: return PluginActionOutput(false, "hour is required")
                val minute = (arguments["minute"] as? PluginValue.Number)?.value?.toInt()
                    ?: return PluginActionOutput(false, "minute is required")
                val message = (arguments["message"] as? PluginValue.Text)?.value ?: "Miss Minutes reminder"
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
            override val sideEffect = true
            override val argumentSchema = argumentsFor("open_app")
            override val capability = PluginCapability.DEVICE_INFORMATION
            override fun execute(arguments: Map<String, PluginValue>): PluginActionOutput {
                val packageName = (arguments["packageName"] as? PluginValue.Text)?.value?.takeIf { it.isNotBlank() }
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
