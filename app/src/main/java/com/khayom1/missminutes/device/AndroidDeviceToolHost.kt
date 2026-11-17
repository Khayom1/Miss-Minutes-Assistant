package com.khayom1.missminutes.device

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import java.io.File
import java.io.FileOutputStream

class AndroidDeviceToolHost(context: Context) : DeviceToolHost {
    private val appContext = context.applicationContext

    override fun execute(command: DeviceToolCommand): String = when (command) {
        DeviceToolCommand.ReadDeviceInfo ->
            "Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})."
        DeviceToolCommand.ReadBatteryStatus -> {
            val batteryManager = appContext.getSystemService(BatteryManager::class.java)
            val percent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (percent in 0..100) "Battery: $percent%." else "Battery level is unavailable."
        }
        DeviceToolCommand.OpenWifiSettings -> {
            launch(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            "Wi-Fi settings opened."
        }
        DeviceToolCommand.OpenAppSettings -> {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}")
            )
            launch(intent)
            "Miss Minutes app settings opened."
        }
        is DeviceToolCommand.ShareText -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, command.text)
            }
            launch(Intent.createChooser(intent, "Share with"))
            "Android share sheet opened."
        }
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }
}

/** App-private, bounded, content-free audit trail. Never stores command arguments or message text. */
class FileDeviceToolAuditLog(context: Context) : DeviceToolAuditLog {
    private val file = File(context.applicationContext.filesDir, "device-tool-audit.log")

    @Synchronized
    override fun append(entry: DeviceToolAuditEntry) {
        if (file.exists() && file.length() > MAX_BYTES) {
            file.delete()
        }
        val line = "${entry.timestampMillis}\t${entry.capability.name}\t${entry.outcome}\n"
        FileOutputStream(file, true).bufferedWriter(Charsets.UTF_8).use { it.append(line) }
    }

    companion object {
        private const val MAX_BYTES = 64 * 1024L
    }
}
