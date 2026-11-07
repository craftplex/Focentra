package com.niloy.focentra.service

import com.niloy.focentra.data.local.entity.ReminderEntity
import java.util.Calendar
import java.util.TimeZone

internal data class ScheduledAlertTime(
    val id: String,
    val hour: Int,
    val minute: Int,
    val days: Set<Int>
)

internal val motivationalDays: Set<Int> =
    (Calendar.SUNDAY..Calendar.SATURDAY).toSet() - Calendar.MONDAY

internal val dailyTargetDays: Set<Int> =
    (Calendar.SUNDAY..Calendar.SATURDAY).toSet() - setOf(Calendar.SUNDAY, Calendar.WEDNESDAY)

internal val scheduledAlertTimes = listOf(
    ScheduledAlertTime("mot_0730", 7, 30, motivationalDays),
    ScheduledAlertTime("mot_0900", 9, 0, motivationalDays),
    ScheduledAlertTime("mot_1100", 11, 0, motivationalDays),
    ScheduledAlertTime("mot_1230", 12, 30, motivationalDays),
    ScheduledAlertTime("evening_1830", 18, 30, (Calendar.SUNDAY..Calendar.SATURDAY).toSet()),
    ScheduledAlertTime("mot_2000", 20, 0, motivationalDays),
    ScheduledAlertTime("mot_2230", 22, 30, motivationalDays),
    ScheduledAlertTime("mot_2330", 23, 30, motivationalDays)
)

internal data class RevisionStage(
    val id: String,
    val title: String,
    val displayName: String,
    val daysAfterCompletion: Int,
    val intervalDaysFromPrevious: Int = daysAfterCompletion
)

internal val revisionStages = listOf(
    RevisionStage("1d", "1st Day Quick Recap Revision", "1st Day (Quick Recap)", 1, intervalDaysFromPrevious = 1),
    RevisionStage("3d", "3rd Day Scientific Revision", "3rd Day (Detailed Review)", 3, intervalDaysFromPrevious = 3),
    RevisionStage("7d", "7th Day Problem Solving Revision", "7th Day (Problem Solving)", 7, intervalDaysFromPrevious = 7),
    RevisionStage("15d", "15th Day Final Revision Test", "15th Day (Final Master Test)", 15, intervalDaysFromPrevious = 15)
)

internal fun revisionDueAt(
    completedAt: Long,
    stage: RevisionStage,
    timeZone: TimeZone = TimeZone.getDefault()
): Long = Calendar.getInstance(timeZone).apply {
    timeInMillis = completedAt
    add(Calendar.DAY_OF_YEAR, stage.intervalDaysFromPrevious)
}.timeInMillis

internal data class StageScheduleInfo(
    val stage: RevisionStage,
    val isCompleted: Boolean,
    val dueDateMs: Long?,
    val isUpcoming: Boolean
)

/**
 * Calculates sequential revision schedule for a completed chapter given its revision history.
 * - Stage 1 (1d): dueDate is chapter completion time + 1 day.
 * - Stage 2 (3d): unlocked only when Stage 1 is completed. Due date is Stage 1 completion time + 3 days.
 * - Stage 3 (7d): unlocked only when Stage 2 is completed. Due date is Stage 2 completion time + 7 days.
 * - Stage 4 (15d): unlocked only when Stage 3 is completed. Due date is Stage 3 completion time + 15 days.
 * If a stage's prerequisite stage is not yet completed, its dueDate is null and isUpcoming = true.
 */
internal fun calculateSequentialRevisionStages(
    chapterCompletedAt: Long,
    historyForChapter: List<com.niloy.focentra.viewmodel.RevisionRecord>,
    timeZone: TimeZone = TimeZone.getDefault()
): List<StageScheduleInfo> {
    if (chapterCompletedAt <= 0L) return emptyList()

    val result = mutableListOf<StageScheduleInfo>()
    var previousStageCompletionTime: Long? = chapterCompletedAt

    for (stage in revisionStages) {
        val completedRecord = historyForChapter
            .filter {
                it.startTime >= chapterCompletedAt &&
                    it.durationSeconds >= 60L * 60L &&
                    revisionStageId(it.revisionStage) == stage.id
            }
            .maxByOrNull { it.endTime }

        val isCompleted = completedRecord != null

        if (isCompleted) {
            result.add(
                StageScheduleInfo(
                    stage = stage,
                    isCompleted = true,
                    dueDateMs = null,
                    isUpcoming = false
                )
            )
            // The completion time of this stage will be the baseline for the next stage
            previousStageCompletionTime = completedRecord.endTime
        } else {
            if (previousStageCompletionTime != null) {
                // Preceding stage is complete (or this is stage 1 based on chapter completion)
                val due = revisionDueAt(previousStageCompletionTime, stage, timeZone)
                result.add(
                    StageScheduleInfo(
                        stage = stage,
                        isCompleted = false,
                        dueDateMs = due,
                        isUpcoming = false
                    )
                )
                // Since this stage is not yet completed, any subsequent stages are upcoming
                previousStageCompletionTime = null
            } else {
                // Previous stage hasn't completed yet; this stage is Upcoming
                result.add(
                    StageScheduleInfo(
                        stage = stage,
                        isCompleted = false,
                        dueDateMs = null,
                        isUpcoming = true
                    )
                )
            }
        }
    }

    return result
}

internal fun revisionStageId(stage: String): String? {
    val normalized = stage.lowercase(java.util.Locale.ROOT)
    return when {
        normalized.contains("15") || normalized.contains("fifteenth") -> "15d"
        normalized.contains("7") || normalized.contains("seventh") -> "7d"
        normalized.contains("3") || normalized.contains("third") -> "3d"
        normalized.contains("1") || normalized.contains("first") -> "1d"
        else -> null
    }
}

internal fun nextScheduleTimeMillis(
    hour: Int,
    minute: Int,
    selectedDays: Set<Int>,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long? {
    if (hour !in 0..23 || minute !in 0..59 || selectedDays.isEmpty()) return null

    for (daysAhead in 0..7) {
        val candidate = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, daysAhead)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (candidate.get(Calendar.DAY_OF_WEEK) in selectedDays && candidate.timeInMillis > nowMillis) {
            return candidate.timeInMillis
        }
    }
    return null
}

internal fun nextReminderTimeMillis(
    reminder: ReminderEntity,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Long? {
    if (!reminder.enabled || reminder.hour !in 0..23 || reminder.minute !in 0..59) return null

    val selectedDays = parseReminderDays(reminder.daysOfWeek)
    return nextScheduleTimeMillis(reminder.hour, reminder.minute, selectedDays, nowMillis, timeZone)
}

private fun parseReminderDays(daysOfWeek: String): Set<Int> {
    val normalized = daysOfWeek.trim().lowercase()
    if (normalized.isEmpty() || normalized in setOf("every day", "everyday", "daily")) {
        return (Calendar.SUNDAY..Calendar.SATURDAY).toSet()
    }
    if (normalized in setOf("weekdays", "weekday", "mon-fri")) {
        return (Calendar.MONDAY..Calendar.FRIDAY).toSet()
    }
    if (normalized in setOf("weekends", "weekend", "sat-sun")) {
        return setOf(Calendar.SATURDAY, Calendar.SUNDAY)
    }
    val tokens = normalized.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }

    val dayByToken = mapOf(
        "sun" to Calendar.SUNDAY, "sunday" to Calendar.SUNDAY,
        "mon" to Calendar.MONDAY, "monday" to Calendar.MONDAY,
        "tue" to Calendar.TUESDAY, "tues" to Calendar.TUESDAY, "tuesday" to Calendar.TUESDAY,
        "wed" to Calendar.WEDNESDAY, "weds" to Calendar.WEDNESDAY, "wednesday" to Calendar.WEDNESDAY,
        "thu" to Calendar.THURSDAY, "thur" to Calendar.THURSDAY, "thurs" to Calendar.THURSDAY, "thursday" to Calendar.THURSDAY,
        "fri" to Calendar.FRIDAY, "friday" to Calendar.FRIDAY,
        "sat" to Calendar.SATURDAY, "saturday" to Calendar.SATURDAY
    )
    return tokens.mapNotNull(dayByToken::get).toSet()
}
