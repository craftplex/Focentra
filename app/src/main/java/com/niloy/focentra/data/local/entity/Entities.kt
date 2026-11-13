package com.niloy.focentra.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "study_sessions",
    indices = [
        Index(value = ["startTime"]),
        Index(value = ["subject"]),
        Index(value = ["completionStatus"])
    ]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val actualFocusedSeconds: Long,
    val pausedSeconds: Long,
    val sessionType: String, // "COUNTDOWN", "STOPWATCH", "POMODORO"
    val subject: String,
    val topic: String = "",
    @ColumnInfo(defaultValue = "''")
    val selectedChapter: String = "",
    val category: String = "Study",
    val goal: String = "",
    val notes: String = "",
    val tags: String = "", // Comma-separated
    val productivityRating: Int = 4, // 1 - 5
    val distractionCount: Int = 0,
    val distractionDetails: String = "", // e.g. "Phone: 2, Talking: 1"
    val mood: String = "GOOD", // TERRIBLE, BAD, NORMAL, GOOD, EXCELLENT
    val energyLevel: String = "HIGH", // VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    val location: String = "Desk",
    val completionStatus: String = "COMPLETED", // COMPLETED, EARLY_FINISH, CANCELLED
    val targetId: String = "",
    val createdTimestamp: Long = System.currentTimeMillis(),
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val colorHex: String = "#6366F1",
    val iconName: String = "School",
    val targetHours: Float = 20.0f,
    val description: String = "",
    val orderIndex: Int = 0
)

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val name: String,
    val description: String = ""
)

@Entity(tableName = "study_goals")
data class StudyGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val periodType: String, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    val targetMinutes: Int,
    val subjectFilter: String = "",
    val active: Boolean = true
)

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val type: String, // "COUNTDOWN", "STOPWATCH", "POMODORO"
    val durationMinutes: Int = 25,
    val subject: String = "General",
    val topic: String = "",
    val pomodoroFocusMin: Int = 25,
    val pomodoroShortBreakMin: Int = 5,
    val pomodoroLongBreakMin: Int = 15,
    val pomodoroRounds: Int = 4,
    val soundEnabled: Boolean = true,
    val vibrateEnabled: Boolean = true,
    val keepScreenAwake: Boolean = true,
    val tags: String = ""
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val iconName: String,
    val category: String = "MILESTONE",
    val unlocked: Boolean = false,
    val unlockedTimestamp: Long = 0L,
    val progress: Float = 0f,
    val target: Float = 1f
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String = "MON,TUE,WED,THU,FRI,SAT,SUN",
    val enabled: Boolean = true,
    val message: String = "Time for focus study session"
)

@Entity(tableName = "user_settings")
data class SettingEntity(
    @PrimaryKey
    val key: String,
    val value: String
)

@Entity(tableName = "daily_tasks")
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val subject: String = "General",
    val dateEpochMs: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = false,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chapters",
    indices = [Index(value = ["subjectId"])]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subjectId: Long,
    val subjectName: String,
    val chapterName: String,
    val tapCount: Int = 0, // 0 to 3 (3 taps = completely finished)
    val status: String = "পড়া শুরু হয় নাই", // Status dropdown value
    val lastStudiedTimestamp: Long = System.currentTimeMillis(),
    val completedTimestamp: Long = 0L,
    val totalClasses: Int = 10,
    val completedClassesCount: Int = 0,
    val isRevision1Completed: Boolean = false,
    val isRevision2Completed: Boolean = false,
    val orderIndex: Int = 0,
    val historicalStudySeconds: Long = 0L
)

@Entity(
    tableName = "notifications",
    indices = [Index(value = ["timestamp"])]
)
data class NotificationEntity(
    @PrimaryKey
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val subjectName: String = "",
    val chapterName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

@Entity(
    tableName = "revision_skips",
    indices = [
        Index(value = ["subjectName"]),
        Index(value = ["chapterName"])
    ]
)
data class RevisionSkipEntity(
    @PrimaryKey
    val id: String,
    val subjectName: String,
    val chapterName: String,
    val reason: String,
    val skippedAtTimestamp: Long = System.currentTimeMillis(),
    val scheduledRecapTimestamp: Long
)

@Entity(
    tableName = "trash_items",
    indices = [Index(value = ["deletedAt"])]
)
data class TrashItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val itemType: String,
    val payloadJson: String,
    val deletedAt: Long = System.currentTimeMillis()
)

object TrashItemType {
    const val DATABASE_ROWS = "DATABASE_ROWS"
    const val APP_SNAPSHOT = "APP_SNAPSHOT"
    const val TODAYS_STUDY_DATA = "TODAYS_STUDY_DATA"
    const val REVISION_RECORD = "REVISION_RECORD"
    const val BRAIN_DUMP_NOTE = "BRAIN_DUMP_NOTE"
    const val BRAIN_DUMP_NOTES = "BRAIN_DUMP_NOTES"
    const val EXAM_TARGET = "EXAM_TARGET"
}
