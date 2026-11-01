package com.niloy.focentra.service

import com.niloy.focentra.data.local.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderScheduleTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun weekdayReminderMovesToNextSelectedDayWhenTodaysTimeHasPassed() {
        val now = timeMillis(2026, Calendar.OCTOBER, 19, 9, 1)
        val reminder = ReminderEntity(title = "Weekday study", hour = 9, minute = 0, daysOfWeek = "Mon,Tue,Wed,Thu,Fri")

        assertEquals(
            timeMillis(2026, Calendar.OCTOBER, 20, 9, 0),
            nextReminderTimeMillis(reminder, now, utc)
        )
    }

    @Test
    fun reminderSchedulesTodayWhenItsSelectedTimeIsStillAhead() {
        val now = timeMillis(2026, Calendar.OCTOBER, 19, 8, 59)
        val reminder = ReminderEntity(title = "Morning study", hour = 9, minute = 0, daysOfWeek = "MON")

        assertEquals(
            timeMillis(2026, Calendar.OCTOBER, 19, 9, 0),
            nextReminderTimeMillis(reminder, now, utc)
        )
    }

    @Test
    fun weekendAliasSchedulesSaturday() {
        val now = timeMillis(2026, Calendar.OCTOBER, 23, 18, 0)
        val reminder = ReminderEntity(title = "Weekend study", hour = 9, minute = 0, daysOfWeek = "Weekends")

        assertEquals(
            timeMillis(2026, Calendar.OCTOBER, 24, 9, 0),
            nextReminderTimeMillis(reminder, now, utc)
        )
    }

    @Test
    fun disabledOrInvalidReminderHasNoNextAlarm() {
        val now = timeMillis(2026, Calendar.OCTOBER, 19, 8, 0)

        assertNull(nextReminderTimeMillis(ReminderEntity(title = "Disabled", hour = 9, minute = 0, enabled = false), now, utc))
        assertNull(nextReminderTimeMillis(ReminderEntity(title = "Invalid", hour = 24, minute = 0), now, utc))
        assertNull(nextReminderTimeMillis(ReminderEntity(title = "Unknown", hour = 9, minute = 0, daysOfWeek = "holiday"), now, utc))
    }

    @Test
    fun motivationalScheduleUsesRequestedTimesAndExcludesMonday() {
        assertEquals(
            listOf(7 to 30, 9 to 0, 11 to 0, 12 to 30, 18 to 30, 20 to 0, 22 to 30, 23 to 30),
            scheduledAlertTimes.map { it.hour to it.minute }
        )
        assertEquals(
            setOf(Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY),
            motivationalDays
        )
    }

    @Test
    fun eveningDailyTargetExcludesSundayAndWednesday() {
        assertEquals(
            setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY),
            dailyTargetDays
        )
        assertEquals(18 to 30, scheduledAlertTimes.first { it.id == "evening_1830" }.let { it.hour to it.minute })
    }

    @Test
    fun selectedScheduleTimeUsesTheNextAllowedWeekday() {
        val monday = timeMillis(2026, Calendar.OCTOBER, 19, 8, 0)
        assertEquals(
            timeMillis(2026, Calendar.OCTOBER, 20, 7, 30),
            nextScheduleTimeMillis(7, 30, motivationalDays, monday, utc)
        )
    }

    @Test
    fun revisionStagesHaveConsistentDatesAndKeys() {
        val completedAt = timeMillis(2026, Calendar.MARCH, 1, 10, 15)
        assertEquals(
            listOf(1, 3, 7, 15),
            revisionStages.map { it.daysAfterCompletion }
        )
        revisionStages.forEach { stage ->
            val due = Calendar.getInstance(utc).apply { timeInMillis = completedAt }
            due.add(Calendar.DAY_OF_YEAR, stage.daysAfterCompletion)
            assertEquals(due.timeInMillis, revisionDueAt(completedAt, stage, utc))
            assertEquals(stage.id, revisionStageId(stage.title))
            assertEquals(stage.id, revisionStageId(stage.displayName))
        }
    }

    private fun timeMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
