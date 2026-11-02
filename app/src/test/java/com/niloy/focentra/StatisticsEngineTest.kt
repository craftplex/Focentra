package com.niloy.focentra

import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.data.local.entity.ChapterEntity
import com.niloy.focentra.engine.StatisticsEngine
import com.niloy.focentra.engine.TimePeriod
import com.niloy.focentra.data.repository.calculateStreak
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StatisticsEngineTest {
    @Test
    fun lastMonthExcludesTheFirstDayOfThisMonth() {
        val currentMonthStart = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lastMonthSession = session(currentMonthStart.timeInMillis - 1_000L, 60L)
        val currentMonthSession = session(currentMonthStart.timeInMillis + 60 * 60 * 1000L, 120L)

        val stats = StatisticsEngine.calculatePeriodStats(
            listOf(lastMonthSession, currentMonthSession),
            TimePeriod.LAST_MONTH
        )

        assertEquals(60L, stats.totalFocusedSeconds)
        assertEquals(1, stats.sessionCount)
    }

    @Test
    fun todayTotalsIncludeMultipleSessionsAndExcludeYesterday() {
        val todayNoon = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayNoon = todayNoon - 24 * 60 * 60 * 1000L

        val stats = StatisticsEngine.calculatePeriodStats(
            listOf(
                session(todayNoon, 1_200L),
                session(todayNoon + 60_000L, 1_800L),
                session(yesterdayNoon, 3_600L)
            ),
            TimePeriod.TODAY
        )

        assertEquals(3_000L, stats.totalFocusedSeconds)
        assertEquals(2, stats.sessionCount)
    }

    @Test
    fun thisMonthIncludesTheFirstDayAndExcludesThePreviousMonth() {
        val firstOfThisMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val stats = StatisticsEngine.calculatePeriodStats(
            listOf(
                session(firstOfThisMonth.timeInMillis, 600L),
                session(firstOfThisMonth.timeInMillis - 1_000L, 1_200L)
            ),
            TimePeriod.THIS_MONTH
        )

        assertEquals(600L, stats.totalFocusedSeconds)
        assertEquals(1, stats.sessionCount)
    }

    @Test
    fun weeklyPersonalRecordSumsSessionsAcrossTheWeek() {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.MARCH, 9, 12, 0, 0) // Monday
        }
        val firstDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 2) // Wednesday
        val thirdDay = calendar.timeInMillis

        val records = StatisticsEngine.calculatePersonalRecords(
            listOf(session(firstDay, 5 * 60 * 60L), session(thirdDay, 4 * 60 * 60L))
        )

        assertEquals(9 * 60L, records.maxWeekMinutes)
    }

    @Test
    fun dailyTotalsAccumulateSubMinuteSessionsBeforeConvertingToMinutes() {
        val now = System.currentTimeMillis()
        val sessions = List(120) { session(now, 30L) }

        val records = StatisticsEngine.calculatePersonalRecords(sessions)
        val todayKey = Calendar.getInstance().apply { timeInMillis = now }.let {
            String.format("%04d-%02d-%02d", it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
        val todayHeatmap = StatisticsEngine.buildHeatmapData(sessions)
            .single { it.dateKey == todayKey }

        assertEquals(60L, records.maxDayMinutes)
        assertEquals(60L, todayHeatmap.studyMinutes)
        assertEquals(1, todayHeatmap.level)
    }

    @Test
    fun longestStreakCountsConsecutiveDaysAcrossDaylightSavingTime() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            val sessions = listOf(
                session(dateMillis(2026, Calendar.MARCH, 7), 60L),
                session(dateMillis(2026, Calendar.MARCH, 8), 60L),
                session(dateMillis(2026, Calendar.MARCH, 9), 60L)
            )

            assertEquals(3, calculateStreak(sessions).longestStreak)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun chapterStudyTotalsUseSelectedChapterAndKeepSubMinuteSessions() {
        val chapters = listOf(
            ChapterEntity(id = 1, subjectId = 1, subjectName = "Physics", chapterName = "Vectors"),
            ChapterEntity(id = 2, subjectId = 1, subjectName = "Physics", chapterName = "Motion")
        )
        val sessions = listOf(
            session(1_000L, 30L).copy(subject = "Physics", topic = "not the chapter", selectedChapter = "Vectors"),
            session(2_000L, 45L).copy(subject = "Physics", topic = "Vectors - practice"),
            session(3_000L, 60L).copy(subject = "Physics", topic = "Vectors", selectedChapter = "Motion")
        )

        assertEquals(
            mapOf(1L to 75L, 2L to 60L),
            StatisticsEngine.buildChapterStudyTotalsSeconds(sessions, chapters)
        )
    }

    @Test
    fun lastWeekTrendBucketsStudyIntoTheCorrectWeekday() {
        val thisMonday = Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val lastTuesday = (thisMonday.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -6)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val stats = StatisticsEngine.calculatePeriodStats(
            listOf(session(lastTuesday.timeInMillis, 90 * 60L)),
            TimePeriod.LAST_WEEK
        )

        assertEquals(7, stats.chartData.size)
        assertEquals(90f, stats.chartData[1].studyMinutes, 0.01f)
        assertEquals(0f, stats.chartData[6].studyMinutes, 0.01f)
    }

    @Test
    fun thisWeekStartsOnMondayEvenWhenTheCurrentDayIsSunday() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = dateMillis(2026, Calendar.NOVEMBER, 8, 12, 0)
            val previousMonday = dateMillis(2026, Calendar.OCTOBER, 26, 10, 0)
            val previousSunday = dateMillis(2026, Calendar.NOVEMBER, 1, 23, 0)
            val thisMonday = dateMillis(2026, Calendar.NOVEMBER, 2, 0, 0)
            val thisSunday = dateMillis(2026, Calendar.NOVEMBER, 8, 10, 0)

            val thisWeek = StatisticsEngine.filterSessionsByPeriod(
                listOf(
                    session(previousMonday, 30 * 60L),
                    session(previousSunday, 40 * 60L),
                    session(thisMonday, 50 * 60L),
                    session(thisSunday, 60 * 60L)
                ),
                TimePeriod.THIS_WEEK,
                now
            )
            val chartData = StatisticsEngine.buildChartItems(thisWeek, TimePeriod.THIS_WEEK, 480f)

            assertEquals(2, thisWeek.size)
            assertEquals(7, chartData.size)
            assertEquals(50f, chartData[0].studyMinutes, 0.01f)
            assertEquals(60f, chartData[6].studyMinutes, 0.01f)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun lastWeekEndsExclusivelyAtThisWeeksMonday() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val now = dateMillis(2026, Calendar.NOVEMBER, 8, 12, 0)
            val lastMonday = dateMillis(2026, Calendar.OCTOBER, 26, 0, 0)
            val lastSunday = dateMillis(2026, Calendar.NOVEMBER, 1, 23, 0)
            val thisMonday = dateMillis(2026, Calendar.NOVEMBER, 2, 0, 0)

            val lastWeek = StatisticsEngine.filterSessionsByPeriod(
                listOf(
                    session(lastMonday, 30 * 60L),
                    session(lastSunday, 40 * 60L),
                    session(thisMonday, 50 * 60L)
                ),
                TimePeriod.LAST_WEEK,
                now
            )
            val chartData = StatisticsEngine.buildChartItems(lastWeek, TimePeriod.LAST_WEEK, 480f)

            assertEquals(2, lastWeek.size)
            assertEquals(30f, chartData[0].studyMinutes, 0.01f)
            assertEquals(40f, chartData[6].studyMinutes, 0.01f)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    @Test
    fun emptyWeeklyPeriodStillProvidesSevenTrendBuckets() {
        val stats = StatisticsEngine.calculatePeriodStats(emptyList(), TimePeriod.THIS_WEEK)

        assertEquals(listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), stats.chartData.map { it.label })
        assertEquals(listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f), stats.chartData.map { it.studyMinutes })
    }

    @Test
    fun yesterdayTrendIncludesYesterdaySessionsInTheCorrectTimeBucket() {
        val yesterdayAtNoon = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val stats = StatisticsEngine.calculatePeriodStats(
            listOf(session(yesterdayAtNoon, 90 * 60L)),
            TimePeriod.YESTERDAY
        )

        assertEquals(90 * 60L, stats.totalFocusedSeconds)
        assertEquals(6, stats.chartData.size)
        assertEquals(90f, stats.chartData[3].studyMinutes, 0.01f)
    }

    private fun session(startTime: Long, focusedSeconds: Long) = StudySessionEntity(
        startTime = startTime,
        endTime = startTime + focusedSeconds * 1_000L,
        durationSeconds = focusedSeconds,
        actualFocusedSeconds = focusedSeconds,
        pausedSeconds = 0L,
        sessionType = "COUNTDOWN",
        subject = "Study"
    )

    private fun dateMillis(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
