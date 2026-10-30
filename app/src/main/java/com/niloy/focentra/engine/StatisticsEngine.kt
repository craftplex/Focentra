package com.niloy.focentra.engine

import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.data.repository.calculateStreak
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

enum class TimePeriod(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}

data class SubjectStudyStat(
    val subject: String,
    val colorHex: String,
    val focusedMinutes: Long,
    val percentage: Float,
    val sessionCount: Int
)

data class BarChartItem(
    val label: String,
    val sublabel: String = "",
    val studyMinutes: Float,
    val goalMinutes: Float = 0f,
    val isHighlighted: Boolean = false
)

data class HeatmapCell(
    val dateEpochMs: Long,
    val dateKey: String, // "YYYY-MM-DD"
    val dayOfMonth: Int,
    val dayOfWeek: Int, // 1 = Sun, 2 = Mon ...
    val studyMinutes: Long,
    val level: Int, // 0 = none, 1 = low (1-60m), 2 = med (61-180m), 3 = high (181-360m), 4 = max (360m+)
    val sessionCount: Int,
    val goalMinutes: Long,
    val topSubject: String = ""
)

data class CognitiveFatigueMetrics(
    val brainEnergyPercentage: Int = 100, // 0 - 100%
    val fatigueLevel: String = "OPTIMAL", // OPTIMAL, MODERATE, HIGH, EXHAUSTED
    val recommendedBreakMinutes: Int = 15,
    val burnoutRiskScore: Int = 10 // 0 - 100
)

data class SubjectMemoryRetention(
    val subject: String,
    val daysSinceLastStudied: Int,
    val retentionPercentage: Int // 0 - 100%
)

data class PersonalRecords(
    val longestSessionMinutes: Long = 0L,
    val longestSessionSubject: String = "-",
    val maxDayMinutes: Long = 0L,
    val maxDayDate: String = "-",
    val maxWeekMinutes: Long = 0L,
    val longestStreakDays: Int = 0,
    val totalAllTimeMinutes: Long = 0L,
    val totalAllTimeSessions: Int = 0
)

data class PeriodStatistics(
    val period: TimePeriod,
    val totalFocusedSeconds: Long = 0L,
    val totalPausedSeconds: Long = 0L,
    val sessionCount: Int = 0,
    val averageSessionMinutes: Long = 0L,
    val longestSessionMinutes: Long = 0L,
    val shortestSessionMinutes: Long = 0L,
    val averageProductivityRating: Float = 0f,
    val totalDistractions: Int = 0,
    val focusScore: Int = 0, // 0 - 100
    val goalCompletionRate: Float = 0f, // 0 - 100%
    val mostProductiveDay: String = "-",
    val mostProductiveHour: String = "-",
    val mostStudiedSubject: String = "-",
    val mostStudiedTopic: String = "-",
    val chartData: List<BarChartItem> = emptyList(),
    val hourlyDistribution: List<Float> = List(24) { 0f }, // 00h to 23h
    val subjectStats: List<SubjectStudyStat> = emptyList(),
    val cognitiveFatigue: CognitiveFatigueMetrics = CognitiveFatigueMetrics(),
    val memoryRetentions: List<SubjectMemoryRetention> = emptyList(),
    val smartInsights: List<String> = emptyList()
)

object StatisticsEngine {

    fun calculatePeriodStats(
        sessions: List<StudySessionEntity>,
        period: TimePeriod,
        dailyGoalMinutes: Int = 480
    ): PeriodStatistics {
        val filtered = filterSessionsByPeriod(sessions, period)
        if (filtered.isEmpty()) {
            return PeriodStatistics(
                period = period,
                smartInsights = listOf("Start your first study session to generate insights.")
            )
        }

        val totalFocusedSec = filtered.sumOf { it.actualFocusedSeconds }
        val totalPausedSec = filtered.sumOf { it.pausedSeconds }
        val count = filtered.size
        val avgSec = if (count > 0) totalFocusedSec / count else 0L
        val longestSec = filtered.maxOfOrNull { it.actualFocusedSeconds } ?: 0L
        val shortestSec = filtered.minOfOrNull { it.actualFocusedSeconds } ?: 0L

        val avgRating = filtered.map { it.productivityRating }.average().toFloat()
        val totalDistractions = filtered.sumOf { it.distractionCount }

        // Focus Score calculation (0 to 100)
        // Factors: Productivity rating (40%), Distraction penalty (20%), Pause ratio (20%), Completion status (20%)
        val ratingScore = (avgRating / 5.0f) * 40f
        val distractionPenalty = (totalDistractions.toFloat() / count.coerceAtLeast(1) * 5f).coerceAtMost(20f)
        val pauseRatio = if (totalFocusedSec + totalPausedSec > 0) {
            1.0f - (totalPausedSec.toFloat() / (totalFocusedSec + totalPausedSec))
        } else 1.0f
        val pauseScore = (pauseRatio * 20f).coerceIn(0f, 20f)
        val completionRate = filtered.count { it.completionStatus == "COMPLETED" }.toFloat() / count
        val completionScore = completionRate * 20f

        val calculatedFocusScore = (ratingScore + (20f - distractionPenalty) + pauseScore + completionScore).roundToInt().coerceIn(0, 100)

        // Subject breakdown
        val subjectGroups = filtered.groupBy { it.subject }
        val colorPalette = listOf("#6366F1", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#06B6D4", "#EF4444", "#3B82F6", "#14B8A6")
        val subjectStats = subjectGroups.entries.mapIndexed { idx, entry ->
            val subFocusedSec = entry.value.sumOf { it.actualFocusedSeconds }
            val pct = if (totalFocusedSec > 0) (subFocusedSec.toFloat() / totalFocusedSec) * 100f else 0f
            SubjectStudyStat(
                subject = entry.key,
                colorHex = colorPalette[idx % colorPalette.size],
                focusedMinutes = subFocusedSec / 60,
                percentage = pct,
                sessionCount = entry.value.size
            )
        }.sortedByDescending { it.focusedMinutes }

        val mostStudiedSubject = subjectStats.firstOrNull()?.subject ?: "-"

        // Topic breakdown
        val mostStudiedTopic = filtered.filter { it.topic.isNotBlank() }
            .groupBy { it.topic }
            .maxByOrNull { entry -> entry.value.sumOf { it.actualFocusedSeconds } }
            ?.key ?: "-"

        // Hourly productivity (0 to 23)
        val hourlyMins = FloatArray(24) { 0f }
        val hourProductivityMap = mutableMapOf<Int, MutableList<Int>>()
        val cal = Calendar.getInstance()
        filtered.forEach { s ->
            cal.timeInMillis = s.startTime
            val h = cal.get(Calendar.HOUR_OF_DAY)
            hourlyMins[h] += (s.actualFocusedSeconds / 60f)
            hourProductivityMap.getOrPut(h) { mutableListOf() }.add(s.productivityRating)
        }

        val bestHourIdx = hourlyMins.indices.maxByOrNull { hourlyMins[it] } ?: 14
        val bestHourStr = String.format("%02d:00 - %02d:00", bestHourIdx, (bestHourIdx + 1) % 24)

        // Most productive day of week
        val dayMinsMap = mutableMapOf<Int, Long>()
        val dayRatingMap = mutableMapOf<Int, MutableList<Int>>()
        filtered.forEach { s ->
            cal.timeInMillis = s.startTime
            val day = cal.get(Calendar.DAY_OF_WEEK)
            dayMinsMap[day] = (dayMinsMap[day] ?: 0L) + s.actualFocusedSeconds
            dayRatingMap.getOrPut(day) { mutableListOf() }.add(s.productivityRating)
        }

        val bestDayOfWeekInt = dayMinsMap.maxByOrNull { it.value }?.key ?: Calendar.MONDAY
        val dayNames = arrayOf("", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        val mostProductiveDay = if (bestDayOfWeekInt in 1..7) dayNames[bestDayOfWeekInt] else "Monday"

        // Cognitive Fatigue & Brain Energy calculation
        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySessions = filtered.filter { it.startTime >= todayStartMs }
        val todayFocusedMins = todaySessions.sumOf { it.actualFocusedSeconds } / 60
        val fatigueDeduction = ((todayFocusedMins / 45) * 8).toInt().coerceAtMost(70)
        val brainEnergyPct = (100 - fatigueDeduction).coerceIn(15, 100)

        val fatigueLevelStr = when {
            brainEnergyPct >= 80 -> "OPTIMAL"
            brainEnergyPct >= 50 -> "MODERATE"
            brainEnergyPct >= 30 -> "HIGH"
            else -> "EXHAUSTED"
        }

        val recommendedBreakMins = when {
            todayFocusedMins >= 240 -> 30
            todayFocusedMins >= 120 -> 20
            else -> 10
        }

        val burnoutRisk = ((todayFocusedMins / 360f) * 100f).toInt().coerceIn(5, 95)
        val cognitiveFatigue = CognitiveFatigueMetrics(
            brainEnergyPercentage = brainEnergyPct,
            fatigueLevel = fatigueLevelStr,
            recommendedBreakMinutes = recommendedBreakMins,
            burnoutRiskScore = burnoutRisk
        )

        // Subject Memory Decay calculation
        val memoryRetentions = subjectGroups.map { entry ->
            val lastSessionTime = entry.value.maxOfOrNull { it.startTime } ?: System.currentTimeMillis()
            val daysAgo = ((System.currentTimeMillis() - lastSessionTime) / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)
            val retention = (100 - (daysAgo * 12)).coerceIn(20, 100)
            SubjectMemoryRetention(
                subject = entry.key,
                daysSinceLastStudied = daysAgo,
                retentionPercentage = retention
            )
        }.sortedBy { it.retentionPercentage }

        // Build Bar Chart Data based on period
        val chartData = buildChartItems(filtered, period, dailyGoalMinutes.toFloat())

        // Smart Offline Insights
        val insights = mutableListOf<String>()
        insights.add("Overall Focus Score: $calculatedFocusScore/100 across $count focus sessions.")

        if (subjectStats.isNotEmpty()) {
            val totalSubHoursStr = subjectStats.take(4).joinToString(", ") { "${it.subject}: ${String.format(Locale.getDefault(), "%.1fh", it.focusedMinutes / 60f)}" }
            insights.add("Subject Study Time: $totalSubHoursStr")

            val topSub = subjectStats.first()
            insights.add("Most Studied Subject This Week / Period: «${topSub.subject}» with ${String.format(Locale.getDefault(), "%.1f", topSub.focusedMinutes / 60f)} hours (${topSub.percentage.toInt()}% of total study time).")
        }

        // Chapter level breakdown
        val chapterMap = filtered.filter { it.topic.isNotBlank() }
            .groupBy { "${it.subject} - ${it.topic}" }
            .mapValues { entry -> entry.value.sumOf { it.actualFocusedSeconds } / 60 }

        val topChapter = chapterMap.maxByOrNull { it.value }
        if (topChapter != null && topChapter.value > 0) {
            insights.add("Top Focused Chapter: «${topChapter.key}» (${topChapter.value / 60}h ${topChapter.value % 60}m studied).")
        }

        if (totalFocusedSec >= 3600) {
            insights.add("Peak Productivity Window: Highest concentration around $bestHourStr.")
        }
        if (mostProductiveDay != "-") {
            insights.add("Most Active Day: $mostProductiveDay recorded highest study volume.")
        }
        if (totalDistractions == 0) {
            insights.add("Flawless Focus: Zero interruptions or distractions logged during this period!")
        } else {
            insights.add("Logged $totalDistractions distractions total (${String.format(Locale.getDefault(), "%.1f", totalDistractions.toFloat() / count)} avg/session).")
        }

        return PeriodStatistics(
            period = period,
            totalFocusedSeconds = totalFocusedSec,
            totalPausedSeconds = totalPausedSec,
            sessionCount = count,
            averageSessionMinutes = avgSec / 60,
            longestSessionMinutes = longestSec / 60,
            shortestSessionMinutes = shortestSec / 60,
            averageProductivityRating = avgRating,
            totalDistractions = totalDistractions,
            focusScore = calculatedFocusScore,
            goalCompletionRate = (completionRate * 100f).coerceIn(0f, 100f),
            mostProductiveDay = mostProductiveDay,
            mostProductiveHour = bestHourStr,
            mostStudiedSubject = mostStudiedSubject,
            mostStudiedTopic = mostStudiedTopic,
            chartData = chartData,
            hourlyDistribution = hourlyMins.toList(),
            subjectStats = subjectStats,
            cognitiveFatigue = cognitiveFatigue,
            memoryRetentions = memoryRetentions,
            smartInsights = insights
        )
    }

    private fun filterSessionsByPeriod(sessions: List<StudySessionEntity>, period: TimePeriod): List<StudySessionEntity> {
        val now = Calendar.getInstance()
        val startCal = Calendar.getInstance()

        fun setStartOfDay(calendar: Calendar) {
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }

        when (period) {
            TimePeriod.TODAY -> {
                setStartOfDay(startCal)
                return sessions.filter { it.startTime in startCal.timeInMillis..now.timeInMillis }
            }
            TimePeriod.YESTERDAY -> {
                setStartOfDay(startCal)
                startCal.add(Calendar.DAY_OF_YEAR, -1)
                val end = startCal.timeInMillis - 1L
                return sessions.filter { it.startTime in startCal.timeInMillis..end }
            }
            TimePeriod.THIS_WEEK -> {
                startCal.firstDayOfWeek = Calendar.MONDAY
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                setStartOfDay(startCal)
                return sessions.filter { it.startTime in startCal.timeInMillis..now.timeInMillis }
            }
            TimePeriod.LAST_WEEK -> {
                startCal.firstDayOfWeek = Calendar.MONDAY
                startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                setStartOfDay(startCal)
                val end = startCal.timeInMillis - 1L
                startCal.add(Calendar.WEEK_OF_YEAR, -1)
                val start = startCal.timeInMillis
                return sessions.filter { it.startTime in start..end }
            }
            TimePeriod.THIS_MONTH -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                setStartOfDay(startCal)
                return sessions.filter { it.startTime in startCal.timeInMillis..now.timeInMillis }
            }
            TimePeriod.LAST_MONTH -> {
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                setStartOfDay(startCal)
                val end = startCal.timeInMillis - 1L
                startCal.add(Calendar.MONTH, -1)
                startCal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startCal.timeInMillis
                return sessions.filter { it.startTime in start..end }
            }
            TimePeriod.THIS_YEAR -> {
                startCal.set(Calendar.DAY_OF_YEAR, 1)
                setStartOfDay(startCal)
                return sessions.filter { it.startTime in startCal.timeInMillis..now.timeInMillis }
            }
            TimePeriod.ALL_TIME -> return sessions
        }
    }

    private fun buildChartItems(
        sessions: List<StudySessionEntity>,
        period: TimePeriod,
        goalMinutes: Float
    ): List<BarChartItem> {
        val items = mutableListOf<BarChartItem>()
        val cal = Calendar.getInstance()
        val dfDay = SimpleDateFormat("EEE", Locale.getDefault())
        val dfMonth = SimpleDateFormat("MMM", Locale.getDefault())

        when (period) {
            TimePeriod.TODAY, TimePeriod.YESTERDAY -> {
                // Group by 4-hour intervals: 00-04, 04-08, 08-12, 12-16, 16-20, 20-24
                val intervals = arrayOf("00-04", "04-08", "08-12", "12-16", "16-20", "20-24")
                val sums = FloatArray(6) { 0f }
                sessions.forEach { s ->
                    cal.timeInMillis = s.startTime
                    val h = cal.get(Calendar.HOUR_OF_DAY)
                    val idx = (h / 4).coerceIn(0, 5)
                    sums[idx] += (s.actualFocusedSeconds / 60f)
                }
                intervals.forEachIndexed { idx, label ->
                    items.add(BarChartItem(label = label, studyMinutes = sums[idx], goalMinutes = goalMinutes / 6))
                }
            }
            TimePeriod.THIS_WEEK, TimePeriod.LAST_WEEK -> {
                // 7 days (Mon to Sun)
                val dayMins = FloatArray(7) { 0f }
                val dayLabels = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                sessions.forEach { s ->
                    cal.timeInMillis = s.startTime
                    val d = cal.get(Calendar.DAY_OF_WEEK)
                    // Convert Calendar.DAY_OF_WEEK (1=Sun, 2=Mon ... 7=Sat) to index (0=Mon ... 6=Sun)
                    val idx = if (d == Calendar.SUNDAY) 6 else d - 2
                    if (idx in 0..6) {
                        dayMins[idx] += (s.actualFocusedSeconds / 60f)
                    }
                }
                dayLabels.forEachIndexed { idx, label ->
                    items.add(BarChartItem(label = label, studyMinutes = dayMins[idx], goalMinutes = goalMinutes))
                }
            }
            TimePeriod.THIS_MONTH, TimePeriod.LAST_MONTH -> {
                // Group into 4 weeks
                val weekMins = FloatArray(4) { 0f }
                val weekLabels = arrayOf("W1 (1-7)", "W2 (8-14)", "W3 (15-21)", "W4 (22+)")
                sessions.forEach { s ->
                    cal.timeInMillis = s.startTime
                    val day = cal.get(Calendar.DAY_OF_MONTH)
                    val idx = when {
                        day <= 7 -> 0
                        day <= 14 -> 1
                        day <= 21 -> 2
                        else -> 3
                    }
                    weekMins[idx] += (s.actualFocusedSeconds / 60f)
                }
                weekLabels.forEachIndexed { idx, label ->
                    items.add(BarChartItem(label = label, studyMinutes = weekMins[idx], goalMinutes = goalMinutes * 7))
                }
            }
            TimePeriod.THIS_YEAR, TimePeriod.ALL_TIME -> {
                // 12 months (Jan to Dec)
                val monthMins = FloatArray(12) { 0f }
                val monthLabels = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                sessions.forEach { s ->
                    cal.timeInMillis = s.startTime
                    val m = cal.get(Calendar.MONTH)
                    if (m in 0..11) {
                        monthMins[m] += (s.actualFocusedSeconds / 60f)
                    }
                }
                monthLabels.forEachIndexed { idx, label ->
                    items.add(BarChartItem(label = label, studyMinutes = monthMins[idx], goalMinutes = goalMinutes * 30))
                }
            }
        }
        return items
    }

    fun buildHeatmapData(
        sessions: List<StudySessionEntity>,
        weeksBack: Int = 16,
        dailyGoalMinutes: Int = 480
    ): List<HeatmapCell> {
        val focusedSecondsByDate = mutableMapOf<String, Long>()
        val countByDate = mutableMapOf<String, Int>()
        val topSubByDate = mutableMapOf<String, String>()

        val cal = Calendar.getInstance()
        sessions.forEach { s ->
            cal.timeInMillis = s.startTime
            val k = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            focusedSecondsByDate[k] = (focusedSecondsByDate[k] ?: 0L) + s.actualFocusedSeconds
            countByDate[k] = (countByDate[k] ?: 0) + 1
            topSubByDate[k] = s.subject
        }

        val cells = mutableListOf<HeatmapCell>()
        val startCal = Calendar.getInstance()
        // Align to start of week (Sunday or Monday)
        startCal.add(Calendar.WEEK_OF_YEAR, -weeksBack)
        startCal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        val endCal = Calendar.getInstance()
        val totalDays = weeksBack * 7 + 7

        val temp = Calendar.getInstance()
        temp.timeInMillis = startCal.timeInMillis

        for (i in 0 until totalDays) {
            val y = temp.get(Calendar.YEAR)
            val m = temp.get(Calendar.MONTH) + 1
            val d = temp.get(Calendar.DAY_OF_MONTH)
            val dow = temp.get(Calendar.DAY_OF_WEEK)
            val k = String.format("%04d-%02d-%02d", y, m, d)

            val studyMins = (focusedSecondsByDate[k] ?: 0L) / 60
            val sessionCnt = countByDate[k] ?: 0
            val topSubject = topSubByDate[k] ?: ""

            val level = when {
                studyMins == 0L -> 0
                studyMins <= 60L -> 1
                studyMins <= 180L -> 2
                studyMins <= 360L -> 3
                else -> 4
            }

            cells.add(
                HeatmapCell(
                    dateEpochMs = temp.timeInMillis,
                    dateKey = k,
                    dayOfMonth = d,
                    dayOfWeek = dow,
                    studyMinutes = studyMins,
                    level = level,
                    sessionCount = sessionCnt,
                    goalMinutes = dailyGoalMinutes.toLong(),
                    topSubject = topSubject
                )
            )
            temp.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cells
    }

    fun calculatePersonalRecords(sessions: List<StudySessionEntity>): PersonalRecords {
        if (sessions.isEmpty()) return PersonalRecords()

        val longestSession = sessions.maxByOrNull { it.actualFocusedSeconds }
        val longestMin = (longestSession?.actualFocusedSeconds ?: 0L) / 60
        val longestSub = longestSession?.subject ?: "-"

        // Day with max study
        val dayMap = mutableMapOf<String, Long>()
        val weekMap = mutableMapOf<String, Long>()
        val cal = Calendar.getInstance()
        sessions.forEach { s ->
            cal.timeInMillis = s.startTime
            val k = String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            dayMap[k] = (dayMap[k] ?: 0L) + s.actualFocusedSeconds

            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val weekKey = String.format(
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            weekMap[weekKey] = (weekMap[weekKey] ?: 0L) + s.actualFocusedSeconds
        }
        val maxDayEntry = dayMap.maxByOrNull { it.value }
        val maxDayMins = (maxDayEntry?.value ?: 0L) / 60
        val maxDayDate = maxDayEntry?.key ?: "-"
        val maxWeekMinutes = (weekMap.values.maxOrNull() ?: 0L) / 60

        val streak = calculateStreak(sessions)
        val totalAllTimeMinutes = sessions.sumOf { it.actualFocusedSeconds } / 60

        return PersonalRecords(
            longestSessionMinutes = longestMin,
            longestSessionSubject = longestSub,
            maxDayMinutes = maxDayMins,
            maxDayDate = maxDayDate,
            maxWeekMinutes = maxWeekMinutes,
            longestStreakDays = streak.longestStreak,
            totalAllTimeMinutes = totalAllTimeMinutes,
            totalAllTimeSessions = sessions.size
        )
    }
}
