package com.niloy.focentra.service

import com.niloy.focentra.data.local.entity.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun sequentialRevisionSchedulingUnlocksDatesSequentially() {
        val chapterCompletedAt = timeMillis(2026, Calendar.NOVEMBER, 1, 10, 0)
        // With no revision history: Stage 1 is unlocked with due date = completedAt + 1d
        // Stages 2, 3, 4 are Upcoming (dueDate = null)
        val initialStages = calculateSequentialRevisionStages(chapterCompletedAt, emptyList(), utc)
        assertEquals(4, initialStages.size)

        assertEquals("1d", initialStages[0].stage.id)
        assertFalse(initialStages[0].isCompleted)
        assertFalse(initialStages[0].isUpcoming)
        assertEquals(timeMillis(2026, Calendar.NOVEMBER, 2, 10, 0), initialStages[0].dueDateMs)

        assertEquals("3d", initialStages[1].stage.id)
        assertFalse(initialStages[1].isCompleted)
        assertTrue(initialStages[1].isUpcoming)
        assertNull(initialStages[1].dueDateMs)

        assertEquals("7d", initialStages[2].stage.id)
        assertTrue(initialStages[2].isUpcoming)
        assertNull(initialStages[2].dueDateMs)

        assertEquals("15d", initialStages[3].stage.id)
        assertTrue(initialStages[3].isUpcoming)
        assertNull(initialStages[3].dueDateMs)

        // Now complete Stage 1 on Nov 7 (delayed recap)
        val stage1Finish = timeMillis(2026, Calendar.NOVEMBER, 7, 11, 0)
        val stage1Record = com.niloy.focentra.viewmodel.RevisionRecord(
            subjectName = "English 2nd Paper",
            chapterName = "13. Basic Grammar (ACS)",
            durationMinutes = 60,
            startTime = stage1Finish - 3600_000L,
            endTime = stage1Finish,
            revisionStage = "1st Day (Quick Recap)",
            durationSeconds = 3600L
        )

        val stagesAfterStage1 = calculateSequentialRevisionStages(chapterCompletedAt, listOf(stage1Record), utc)
        assertEquals(4, stagesAfterStage1.size)
        // Stage 1 is complete
        assertTrue(stagesAfterStage1[0].isCompleted)

        // Stage 2 is unlocked with due date = Nov 7 + 3 days = Nov 10!
        assertEquals("3d", stagesAfterStage1[1].stage.id)
        assertFalse(stagesAfterStage1[1].isCompleted)
        assertFalse(stagesAfterStage1[1].isUpcoming)
        assertEquals(timeMillis(2026, Calendar.NOVEMBER, 10, 11, 0), stagesAfterStage1[1].dueDateMs)

        // Stage 3 & 4 remain upcoming
        assertTrue(stagesAfterStage1[2].isUpcoming)
        assertNull(stagesAfterStage1[2].dueDateMs)
        assertTrue(stagesAfterStage1[3].isUpcoming)
        assertNull(stagesAfterStage1[3].dueDateMs)
    }

    private fun timeMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(utc).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
