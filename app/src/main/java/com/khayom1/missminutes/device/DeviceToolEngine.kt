package com.khayom1.missminutes.device

/**
 * Deliberately bounded allowlist of Android actions.
 * No arbitrary intent, shell, accessibility, reflection, or unrestricted execution.
 * Consequential actions always require explicit user confirmation.
 */
enum class DeviceToolCapability {
    DEVICE_INFO,
    BATTERY_STATUS,
    OPEN_WIFI_SETTINGS,
    OPEN_APP_SETTINGS,
    SHARE_TEXT,
    READ_CALENDAR,
    CREATE_REMINDER,
    READ_CONTACTS_SUMMARY,
    SET_ALARM,
    OPEN_APP_BY_PACKAGE,
    CURRENT_TIME
}

sealed interface DeviceToolCommand {
    data object ReadDeviceInfo : DeviceToolCommand
    data object ReadBatteryStatus : DeviceToolCommand
    data object OpenWifiSettings : DeviceToolCommand
    data object OpenAppSettings : DeviceToolCommand
    data class ShareText(val text: String) : DeviceToolCommand
    data object ReadCalendarSummary : DeviceToolCommand
    data class CreateReminder(
        val title: String,
        val description: String = "",
        val startEpochMillis: Long
    ) : DeviceToolCommand
    data object ReadContactsSummary : DeviceToolCommand
    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val message: String = "Miss Minutes reminder"
    ) : DeviceToolCommand
    data class OpenAppByPackage(val packageName: String) : DeviceToolCommand
    data object ReadCurrentTime : DeviceToolCommand
}

data class DeviceToolRequest(
    val command: DeviceToolCommand,
    /** Must only be true after a visible, per-action user confirmation. */
    val userConfirmed: Boolean = false
)

data class DeviceToolResult(
    val success: Boolean,
    val message: String,
    val capability: DeviceToolCapability
)

data class DeviceToolAuditEntry(
    val timestampMillis: Long,
    val capability: DeviceToolCapability,
    val outcome: String
)

fun interface DeviceToolAuthorization {
    /** Per-capability opt-in. All capabilities must be disabled by default. */
    fun isGranted(capability: DeviceToolCapability): Boolean
}

fun interface DeviceToolHost {
    fun execute(command: DeviceToolCommand): String
}

fun interface DeviceToolAuditLog {
    fun append(entry: DeviceToolAuditEntry)
}

class DeviceToolEngine(
    private val authorization: DeviceToolAuthorization,
    private val host: DeviceToolHost,
    private val auditLog: DeviceToolAuditLog,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxCallsPerMinute: Int = 12,
    private val executionBudgetMillis: Long = 2_000L
) {
    private val callTimes = ArrayDeque<Long>()

    init {
        require(maxCallsPerMinute > 0) { "maxCallsPerMinute must be positive" }
        require(executionBudgetMillis > 0L) { "executionBudgetMillis must be positive" }
    }

    private fun record(entry: DeviceToolAuditEntry): Boolean = try {
        auditLog.append(entry)
        true
    } catch (_: Exception) {
        false
    }

    @Synchronized
    fun execute(request: DeviceToolRequest): DeviceToolResult {
        val capability = request.command.capability()
        val now = clockMillis()

        fun reject(reason: String): DeviceToolResult {
            record(DeviceToolAuditEntry(now, capability, "denied"))
            return DeviceToolResult(false, reason, capability)
        }

        if (!authorization.isGranted(capability)) {
            return reject("This Android capability has not been enabled in settings.")
        }
        if (request.command.requiresConfirmation() && !request.userConfirmed) {
            return reject("This action requires explicit confirmation.")
        }

        // Argument validation
        when (val cmd = request.command) {
            is DeviceToolCommand.ShareText -> {
                if (cmd.text.isBlank() || cmd.text.length > 1_000) {
                    return reject("Shared text must contain 1–1,000 characters.")
                }
            }
            is DeviceToolCommand.CreateReminder -> {
                if (cmd.title.isBlank() || cmd.title.length > 200) {
                    return reject("Reminder title must be 1–200 characters.")
                }
                if (cmd.startEpochMillis < now - 60_000L) {
                    return reject("Reminder time must be in the future.")
                }
            }
            is DeviceToolCommand.SetAlarm -> {
                if (cmd.hour !in 0..23 || cmd.minute !in 0..59) {
                    return reject("Alarm hour must be 0–23 and minute 0–59.")
                }
            }
            is DeviceToolCommand.OpenAppByPackage -> {
                if (cmd.packageName.isBlank() || !cmd.packageName.matches(Regex("^[a-zA-Z0-9._]+$"))) {
                    return reject("Invalid package name.")
                }
            }
            else -> Unit
        }

        while (callTimes.isNotEmpty() && now - callTimes.first() >= 60_000L) {
            callTimes.removeFirst()
        }
        if (callTimes.size >= maxCallsPerMinute) {
            return reject("Android tool rate limit reached. Try again in a minute.")
        }
        callTimes.addLast(now)
        if (!record(DeviceToolAuditEntry(now, capability, "requested"))) {
            return DeviceToolResult(false, "Audit logging is unavailable; the Android action was blocked.", capability)
        }

        val started = System.nanoTime()
        return try {
            val message = host.execute(request.command)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000L
            if (elapsedMillis > executionBudgetMillis) {
                record(DeviceToolAuditEntry(now, capability, "timeout"))
                DeviceToolResult(false, "Android action exceeded its execution budget.", capability)
            } else {
                record(DeviceToolAuditEntry(now, capability, "success"))
                DeviceToolResult(true, message, capability)
            }
        } catch (_: SecurityException) {
            record(DeviceToolAuditEntry(now, capability, "permission_denied"))
            DeviceToolResult(false, "Android denied this operation. Grant the related permission in system settings.", capability)
        } catch (_: Exception) {
            record(DeviceToolAuditEntry(now, capability, "failed"))
            DeviceToolResult(false, "Android could not complete this operation.", capability)
        }
    }
}

private fun DeviceToolCommand.capability(): DeviceToolCapability = when (this) {
    DeviceToolCommand.ReadDeviceInfo -> DeviceToolCapability.DEVICE_INFO
    DeviceToolCommand.ReadBatteryStatus -> DeviceToolCapability.BATTERY_STATUS
    DeviceToolCommand.OpenWifiSettings -> DeviceToolCapability.OPEN_WIFI_SETTINGS
    DeviceToolCommand.OpenAppSettings -> DeviceToolCapability.OPEN_APP_SETTINGS
    is DeviceToolCommand.ShareText -> DeviceToolCapability.SHARE_TEXT
    DeviceToolCommand.ReadCalendarSummary -> DeviceToolCapability.READ_CALENDAR
    is DeviceToolCommand.CreateReminder -> DeviceToolCapability.CREATE_REMINDER
    DeviceToolCommand.ReadContactsSummary -> DeviceToolCapability.READ_CONTACTS_SUMMARY
    is DeviceToolCommand.SetAlarm -> DeviceToolCapability.SET_ALARM
    is DeviceToolCommand.OpenAppByPackage -> DeviceToolCapability.OPEN_APP_BY_PACKAGE
    DeviceToolCommand.ReadCurrentTime -> DeviceToolCapability.CURRENT_TIME
}

private fun DeviceToolCommand.requiresConfirmation(): Boolean = when (this) {
    DeviceToolCommand.ReadDeviceInfo,
    DeviceToolCommand.ReadBatteryStatus,
    DeviceToolCommand.ReadCurrentTime,
    DeviceToolCommand.ReadCalendarSummary,
    DeviceToolCommand.ReadContactsSummary -> false
    DeviceToolCommand.OpenWifiSettings,
    DeviceToolCommand.OpenAppSettings,
    is DeviceToolCommand.ShareText,
    is DeviceToolCommand.CreateReminder,
    is DeviceToolCommand.SetAlarm,
    is DeviceToolCommand.OpenAppByPackage -> true
}
