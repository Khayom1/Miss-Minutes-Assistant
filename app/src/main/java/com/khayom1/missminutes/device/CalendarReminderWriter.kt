package com.khayom1.missminutes.device

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import java.util.TimeZone

internal data class WritableCalendar(
    val id: Long,
    val accessLevel: Int,
    val isVisible: Boolean,
    val isPrimary: Boolean,
    val maxReminders: Int,
    val allowedReminderMethods: Set<Int>
)

internal object CalendarWritePolicy {
    private val supportedAlertMethods = listOf(
        CalendarContract.Reminders.METHOD_ALERT,
        CalendarContract.Reminders.METHOD_DEFAULT,
        CalendarContract.Reminders.METHOD_ALARM
    )

    fun preferredReminderMethod(calendar: WritableCalendar): Int? {
        if (calendar.accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) return null
        if (!calendar.isVisible || calendar.maxReminders < 1) return null
        return supportedAlertMethods.firstOrNull { it in calendar.allowedReminderMethods }
    }

    fun selectWritableCalendar(calendars: Collection<WritableCalendar>): WritableCalendar? =
        calendars.asSequence()
            .filter { preferredReminderMethod(it) != null }
            .sortedWith(
                compareByDescending<WritableCalendar> { it.isPrimary }
                    .thenByDescending { it.accessLevel }
                    .thenBy { it.id }
            )
            .firstOrNull()
}

internal interface CalendarReminderGateway {
    fun writableCalendars(): List<WritableCalendar>
    fun insertEvent(
        calendarId: Long,
        title: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        timeZoneId: String
    ): Long?
    fun insertReminder(eventId: Long, minutesBefore: Int, method: Int): Boolean
    fun deleteEvent(eventId: Long): Boolean
}

internal class CalendarReminderWriter(
    private val gateway: CalendarReminderGateway,
    private val timeZoneId: () -> String = { TimeZone.getDefault().id }
) {
    fun create(command: DeviceToolCommand.CreateReminder): String {
        val calendar = CalendarWritePolicy.selectWritableCalendar(gateway.writableCalendars())
            ?: return "No visible writable calendar supports an alert reminder."
        val method = requireNotNull(CalendarWritePolicy.preferredReminderMethod(calendar))
        val eventId = gateway.insertEvent(
            calendarId = calendar.id,
            title = command.title,
            description = command.description,
            startEpochMillis = command.startEpochMillis,
            endEpochMillis = command.startEpochMillis + EVENT_DURATION_MILLIS,
            timeZoneId = timeZoneId()
        ) ?: return "The selected calendar could not create the event."

        val reminderSaved = runCatching {
            gateway.insertReminder(eventId, REMINDER_MINUTES_BEFORE, method)
        }.getOrDefault(false)
        if (!reminderSaved) {
            val removed = runCatching { gateway.deleteEvent(eventId) }.getOrDefault(false)
            return if (removed) {
                "Calendar reminder could not be scheduled. The incomplete event was removed."
            } else {
                "Calendar reminder could not be scheduled. Event " + eventId +
                    " could not be removed automatically."
            }
        }
        return "Reminder created: " + command.title
    }

    private companion object {
        const val EVENT_DURATION_MILLIS = 30L * 60L * 1_000L
        const val REMINDER_MINUTES_BEFORE = 10
    }
}

internal class AndroidCalendarReminderGateway(
    private val context: Context
) : CalendarReminderGateway {
    private val resolver = context.contentResolver

    override fun writableCalendars(): List<WritableCalendar> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.MAX_REMINDERS,
            CalendarContract.Calendars.ALLOWED_REMINDERS
        )
        val selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + " >= ? AND " +
            CalendarContract.Calendars.VISIBLE + " = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val cursor = resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null
        ) ?: return emptyList()

        return cursor.use { rows ->
            val idIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val accessIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)
            val visibleIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)
            val primaryIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            val maxIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars.MAX_REMINDERS)
            val allowedIndex = rows.getColumnIndexOrThrow(CalendarContract.Calendars.ALLOWED_REMINDERS)
            buildList {
                while (rows.moveToNext()) {
                    val methods = rows.getString(allowedIndex).orEmpty()
                        .split(',')
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()
                    add(
                        WritableCalendar(
                            id = rows.getLong(idIndex),
                            accessLevel = rows.getInt(accessIndex),
                            isVisible = rows.getInt(visibleIndex) == 1,
                            isPrimary = rows.getInt(primaryIndex) == 1,
                            maxReminders = rows.getInt(maxIndex),
                            allowedReminderMethods = methods
                        )
                    )
                }
            }
        }
    }

    override fun insertEvent(
        calendarId: Long,
        title: String,
        description: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        timeZoneId: String
    ): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startEpochMillis)
            put(CalendarContract.Events.DTEND, endEpochMillis)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return runCatching { ContentUris.parseId(uri) }.getOrElse {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    override fun insertReminder(eventId: Long, minutesBefore: Int, method: Int): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, method)
        }
        return resolver.insert(CalendarContract.Reminders.CONTENT_URI, values) != null
    }

    override fun deleteEvent(eventId: Long): Boolean {
        val uri: Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        return resolver.delete(uri, null, null) > 0
    }
}
