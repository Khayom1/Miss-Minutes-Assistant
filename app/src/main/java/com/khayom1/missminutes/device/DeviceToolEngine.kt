package com.khayom1.missminutes.device

/**
 * Deliberately small allowlist of Android actions. There is no arbitrary intent,
 * shell, file-system, accessibility, or reflection execution surface.
 */
enum class DeviceToolCapability {
    DEVICE_INFO,
    BATTERY_STATUS,
    OPEN_WIFI_SETTINGS,
    OPEN_APP_SETTINGS,
    SHARE_TEXT
}

sealed interface DeviceToolCommand {
    data object ReadDeviceInfo : DeviceToolCommand
    data object ReadBatteryStatus : DeviceToolCommand
    data object OpenWifiSettings : DeviceToolCommand
    data object OpenAppSettings : DeviceToolCommand
    data class ShareText(val text: String) : DeviceToolCommand
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
    private val executionBudgetMillis: Long = 1_500L
) {
    private val callTimes = ArrayDeque<Long>()

    @Synchronized
    fun execute(request: DeviceToolRequest): DeviceToolResult {
        val capability = request.command.capability()
        val now = clockMillis()

        fun reject(reason: String): DeviceToolResult {
            auditLog.append(DeviceToolAuditEntry(now, capability, "denied"))
            return DeviceToolResult(false, reason, capability)
        }

        if (!authorization.isGranted(capability)) {
            return reject("This Android capability has not been enabled in settings.")
        }
        if (request.command.requiresConfirmation() && !request.userConfirmed) {
            return reject("This action requires explicit confirmation.")
        }
        if (request.command is DeviceToolCommand.ShareText &&
            (request.command.text.isBlank() || request.command.text.length > 1_000)
        ) {
            return reject("Shared text must contain 1–1,000 characters.")
        }

        while (callTimes.isNotEmpty() && now - callTimes.first() >= 60_000L) {
            callTimes.removeFirst()
        }
        if (callTimes.size >= maxCallsPerMinute) {
            return reject("Android tool rate limit reached. Try again in a minute.")
        }
        callTimes.addLast(now)

        val started = System.nanoTime()
        return try {
            val message = host.execute(request.command)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000L
            if (elapsedMillis > executionBudgetMillis) {
                auditLog.append(DeviceToolAuditEntry(now, capability, "timeout"))
                DeviceToolResult(false, "Android action exceeded its execution budget.", capability)
            } else {
                auditLog.append(DeviceToolAuditEntry(now, capability, "success"))
                DeviceToolResult(true, message, capability)
            }
        } catch (_: SecurityException) {
            auditLog.append(DeviceToolAuditEntry(now, capability, "permission_denied"))
            DeviceToolResult(false, "Android denied this operation. Check the related system permission.", capability)
        } catch (_: Exception) {
            auditLog.append(DeviceToolAuditEntry(now, capability, "failed"))
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
}

private fun DeviceToolCommand.requiresConfirmation(): Boolean = when (this) {
    DeviceToolCommand.ReadDeviceInfo,
    DeviceToolCommand.ReadBatteryStatus -> false
    DeviceToolCommand.OpenWifiSettings,
    DeviceToolCommand.OpenAppSettings,
    is DeviceToolCommand.ShareText -> true
}
