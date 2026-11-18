package com.khayom1.missminutes.device

import android.provider.CalendarContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarReminderWriterTest {
    private fun calendar(
        id: Long,
        primary: Boolean = false,
        access: Int = CalendarContract.Calendars.CAL_ACCESS_OWNER,
        visible: Boolean = true,
        maxReminders: Int = 1,
        methods: Set<Int> = setOf(CalendarContract.Reminders.METHOD_ALERT)
    ) = WritableCalendar(id, access, visible, primary, maxReminders, methods)

    @Test
    fun choosesPrimaryWritableCalendarAndDoesNotAssumeItsId() {
        val chosen = CalendarWritePolicy.selectWritableCalendar(
            listOf(
                calendar(id = 1),
                calendar(id = 42, primary = true),
                calendar(id = 7)
            )
        )
        assertEquals(42L, chosen?.id)
    }

    @Test
    fun readOnlyHiddenAndNoReminderCalendarsAreRejected() {
        val chosen = CalendarWritePolicy.selectWritableCalendar(
            listOf(
                calendar(id = 1, access = CalendarContract.Calendars.CAL_ACCESS_READ),
                calendar(id = 2, visible = false),
                calendar(id = 3, maxReminders = 0),
                calendar(id = 4, methods = setOf(CalendarContract.Reminders.METHOD_EMAIL))
            )
        )
        assertNull(chosen)
        assertNull(CalendarWritePolicy.preferredReminderMethod(
            calendar(id = 5, methods = setOf(CalendarContract.Reminders.METHOD_EMAIL))
        ))
    }

    @Test
    fun methodSelectionUsesOnlyCalendarSupportedAlertMethods() {
        val defaultOnly = calendar(
            id = 1,
            methods = setOf(CalendarContract.Reminders.METHOD_DEFAULT)
        )
        val emailOnly = calendar(
            id = 2,
            methods = setOf(CalendarContract.Reminders.METHOD_EMAIL)
        )
        assertEquals(
            CalendarContract.Reminders.METHOD_DEFAULT,
            CalendarWritePolicy.preferredReminderMethod(defaultOnly)
        )
        assertNull(CalendarWritePolicy.preferredReminderMethod(emailOnly))
    }

    @Test
    fun failedReminderInsertRollsBackOnlyTheNewEvent() {
        val gateway = FakeCalendarGateway(reminderResult = false)
        val result = CalendarReminderWriter(gateway).create(command())
        assertFalse(result.startsWith("Reminder created"))
        assertEquals(listOf(42L), gateway.deletedEventIds)
        assertEquals(42L, gateway.lastCalendarId)
        assertEquals(1, gateway.insertedEvents)
    }

    @Test
    fun successfulReminderUsesChosenCalendarAndSelectedMethod() {
        val gateway = FakeCalendarGateway(reminderResult = true)
        val result = CalendarReminderWriter(gateway) { "Asia/Dushanbe" }.create(command())
        assertEquals("Reminder created: dentist visit", result)
        assertEquals(42L, gateway.lastCalendarId)
        assertEquals(CalendarContract.Reminders.METHOD_ALERT, gateway.lastReminderMethod)
        assertEquals("Asia/Dushanbe", gateway.lastTimeZone)
        assertTrue(gateway.deletedEventIds.isEmpty())
    }

    @Test
    fun noWritableCalendarDoesNotCreateAnEvent() {
        val gateway = FakeCalendarGateway(
            calendars = listOf(
                WritableCalendar(
                    id = 4,
                    accessLevel = CalendarContract.Calendars.CAL_ACCESS_READ,
                    isVisible = true,
                    isPrimary = false,
                    maxReminders = 1,
                    allowedReminderMethods = setOf(CalendarContract.Reminders.METHOD_ALERT)
                )
            )
        )
        val result = CalendarReminderWriter(gateway).create(command())
        assertFalse(result.startsWith("Reminder created"))
        assertEquals(0, gateway.insertedEvents)
        assertTrue(gateway.deletedEventIds.isEmpty())
    }

    private fun command() = DeviceToolCommand.CreateReminder(
        title = "dentist visit",
        description = "check-up",
        startEpochMillis = 2_000_000L
    )

    private class FakeCalendarGateway(
        private val reminderResult: Boolean,
        private val calendars: List<WritableCalendar> = listOf(
            WritableCalendar(
                id = 1,
                accessLevel = CalendarContract.Calendars.CAL_ACCESS_OWNER,
                isVisible = true,
                isPrimary = false,
                maxReminders = 1,
                allowedReminderMethods = setOf(CalendarContract.Reminders.METHOD_ALERT)
            ),
            WritableCalendar(
                id = 42,
                accessLevel = CalendarContract.Calendars.CAL_ACCESS_OWNER,
                isVisible = true,
                isPrimary = true,
                maxReminders = 1,
                allowedReminderMethods = setOf(CalendarContract.Reminders.METHOD_ALERT)
            )
        )
    ) : CalendarReminderGateway {
        var insertedEvents = 0
        var lastCalendarId: Long? = null
        var lastReminderMethod: Int? = null
        var lastTimeZone: String? = null
        val deletedEventIds = mutableListOf<Long>()

        override fun writableCalendars() = calendars

        override fun insertEvent(
            calendarId: Long,
            title: String,
            description: String,
            startEpochMillis: Long,
            endEpochMillis: Long,
            timeZoneId: String
        ): Long {
            insertedEvents++
            lastCalendarId = calendarId
            lastTimeZone = timeZoneId
            return 42L
        }

        override fun insertReminder(eventId: Long, minutesBefore: Int, method: Int): Boolean {
            lastReminderMethod = method
            return reminderResult
        }

        override fun deleteEvent(eventId: Long): Boolean {
            deletedEventIds += eventId
            return true
        }
    }
}
