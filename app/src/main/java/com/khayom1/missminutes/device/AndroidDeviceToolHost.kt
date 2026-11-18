package com.khayom1.missminutes.device

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AndroidDeviceToolHost(context: Context) : DeviceToolHost {
    private val appContext = context.applicationContext
    private val calendarReminderWriter = CalendarReminderWriter(AndroidCalendarReminderGateway(appContext))

    override fun execute(command: DeviceToolCommand): String = when (command) {
        DeviceToolCommand.ReadDeviceInfo ->
            "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        DeviceToolCommand.ReadBatteryStatus -> {
            val batteryManager = appContext.getSystemService(BatteryManager::class.java)
            val percent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (percent in 0..100) "$percent%" else "unavailable"
        }

        DeviceToolCommand.OpenWifiSettings -> {
            launch(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            "Opened Wi-Fi settings"
        }

        DeviceToolCommand.OpenAppSettings -> {
            val intent = Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${appContext.packageName}")
            )
            launch(intent)
            "Opened Miss Minutes app settings"
        }

        is DeviceToolCommand.ShareText -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, command.text)
            }
            launch(Intent.createChooser(intent, "Share with"))
            "Share sheet opened"
        }

        DeviceToolCommand.ReadCurrentTime -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()
            sdf.format(Date())
        }

        DeviceToolCommand.ReadCalendarSummary -> readCalendarSummary()

        is DeviceToolCommand.CreateReminder -> createReminder(command)

        DeviceToolCommand.ReadContactsSummary -> readContactsSummary()

        is DeviceToolCommand.SetAlarm -> setAlarm(command)

        is DeviceToolCommand.OpenAppByPackage -> openApp(command.packageName)
    }

    private fun launch(intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun readCalendarSummary(): String {
        val now = System.currentTimeMillis()
        val end = now + 7L * 24 * 60 * 60 * 1000 // next 7 days
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val args = arrayOf(now.toString(), end.toString())

        val cursor = appContext.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: return "No calendar access or no upcoming events."

        return cursor.use {
            val titles = mutableListOf<String>()
            val titleIdx = it.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = it.getColumnIndex(CalendarContract.Events.DTSTART)
            var count = 0
            while (it.moveToNext() && count < 8) {
                val title = it.getString(titleIdx) ?: "(no title)"
                val start = it.getLong(startIdx)
                val time = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(start))
                titles.add("$time — $title")
                count++
            }
            if (titles.isEmpty()) "No upcoming events in the next 7 days."
            else "Upcoming events:\n" + titles.joinToString("\n")
        }
    }

    private fun createReminder(command: DeviceToolCommand.CreateReminder): String =
        calendarReminderWriter.create(command)

    private fun readContactsSummary(): String {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val cursor = appContext.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC LIMIT 15"
        ) ?: return "No contacts access or no contacts found."

        return cursor.use {
            val names = mutableListOf<String>()
            val nameIdx = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (it.moveToNext() && names.size < 15) {
                val name = it.getString(nameIdx)
                if (!name.isNullOrBlank()) names.add(name)
            }
            if (names.isEmpty()) "No contacts found."
            else "Contacts (sample): " + names.joinToString(", ")
        }
    }

    private fun setAlarm(command: DeviceToolCommand.SetAlarm): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, command.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, command.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, command.message)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false) // show UI so user can confirm
        }
        launch(intent)
        return "Alarm intent opened for ${command.hour}:${command.minute.toString().padStart(2, '0')}"
    }

    private fun openApp(packageName: String): String {
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
            ?: return "App not found or cannot be launched: $packageName"
        launch(launchIntent)
        return "Opened $packageName"
    }
}

/** App-private, bounded, content-free audit trail. Never stores command arguments or message text. */
class FileDeviceToolAuditLog(context: Context) : DeviceToolAuditLog {
    private val file = File(context.applicationContext.filesDir, "device-tool-audit.log")

    @Synchronized
    override fun append(entry: DeviceToolAuditEntry) {
        val line = "${entry.timestampMillis}\t${entry.capability.name}\t${entry.outcome}\n"
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (file.exists() && file.length() + bytes.size > MAX_BYTES && !file.delete()) {
            throw java.io.IOException("Unable to rotate device audit log")
        }
        FileOutputStream(file, true).use { it.write(bytes) }
    }

    companion object {
        private const val MAX_BYTES = 64 * 1024L
    }
}
