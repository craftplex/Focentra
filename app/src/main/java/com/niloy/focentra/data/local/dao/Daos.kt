package com.niloy.focentra.data.local.dao

import androidx.room.*
import com.niloy.focentra.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC")
    fun getAllSessionsFlow(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions WHERE startTime >= :startEpochMs AND endTime <= :endEpochMs ORDER BY startTime DESC")
    fun getSessionsBetweenFlow(startEpochMs: Long, endEpochMs: Long): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions WHERE startTime >= :startEpochMs AND endTime <= :endEpochMs ORDER BY startTime DESC")
    suspend fun getSessionsBetween(startEpochMs: Long, endEpochMs: Long): List<StudySessionEntity>

    @Query("SELECT * FROM study_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): StudySessionEntity?

    @Query("UPDATE study_sessions SET subject = :newName WHERE subject = :oldName")
    suspend fun updateSubjectName(oldName: String, newName: String)

    @Query("SELECT * FROM study_sessions WHERE notes LIKE '%' || :query || '%' OR subject LIKE '%' || :query || '%' OR topic LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY startTime DESC")
    fun searchSessionsFlow(query: String): Flow<List<StudySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySessionEntity>)

    @Update
    suspend fun updateSession(session: StudySessionEntity)

    @Delete
    suspend fun deleteSession(session: StudySessionEntity)

    @Query("DELETE FROM study_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM study_sessions")
    suspend fun clearAllSessions()

    @Query("SELECT COUNT(*) FROM study_sessions")
    suspend fun getSessionCount(): Int
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY orderIndex ASC, name ASC")
    fun getAllSubjectsFlow(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY orderIndex ASC, name ASC")
    suspend fun getAllSubjects(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :subjectId")
    suspend fun getSubjectById(subjectId: Long): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubjectById(id: Long)

    @Query("DELETE FROM subjects")
    suspend fun clearAllSubjects()
}

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE subjectId = :subjectId ORDER BY name ASC")
    fun getTopicsForSubjectFlow(subjectId: Long): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY name ASC")
    fun getAllTopicsFlow(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics ORDER BY name ASC")
    suspend fun getAllTopics(): List<TopicEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: TopicEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopics(topics: List<TopicEntity>)

    @Delete
    suspend fun deleteTopic(topic: TopicEntity)

    @Query("UPDATE topics SET subjectName = :subjectName WHERE subjectId = :subjectId")
    suspend fun updateSubjectName(subjectId: Long, subjectName: String)

    @Query("DELETE FROM topics WHERE subjectId = :subjectId")
    suspend fun deleteTopicsForSubject(subjectId: Long)

    @Query("DELETE FROM topics WHERE id = :id")
    suspend fun deleteTopicById(id: Long)

    @Query("DELETE FROM topics")
    suspend fun clearAllTopics()
}

@Dao
interface StudyGoalDao {
    @Query("SELECT * FROM study_goals WHERE active = 1")
    fun getActiveGoalsFlow(): Flow<List<StudyGoalEntity>>

    @Query("SELECT * FROM study_goals")
    suspend fun getAllGoals(): List<StudyGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: StudyGoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoals(goals: List<StudyGoalEntity>)

    @Update
    suspend fun updateGoal(goal: StudyGoalEntity)

    @Delete
    suspend fun deleteGoal(goal: StudyGoalEntity)

    @Query("DELETE FROM study_goals")
    suspend fun clearAllGoals()
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY id ASC")
    fun getAllPresetsFlow(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY id ASC")
    suspend fun getAllPresets(): List<PresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: PresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<PresetEntity>)

    @Update
    suspend fun updatePreset(preset: PresetEntity)

    @Query("UPDATE presets SET subject = :newName WHERE subject = :oldName")
    suspend fun updateSubjectName(oldName: String, newName: String)

    @Delete
    suspend fun deletePreset(preset: PresetEntity)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deletePresetById(id: Long)

    @Query("DELETE FROM presets")
    suspend fun clearAllPresets()
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY unlocked DESC, id ASC")
    fun getAllAchievementsFlow(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements")
    suspend fun getAllAchievements(): List<AchievementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)

    @Query("DELETE FROM achievements")
    suspend fun clearAllAchievements()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour ASC, minute ASC")
    fun getAllRemindersFlow(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getReminderById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminders(reminders: List<ReminderEntity>)

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()
}

@Dao
interface StudyTaskDao {
    @Query("SELECT * FROM daily_tasks ORDER BY isCompleted ASC, id DESC")
    fun getAllTasksFlow(): Flow<List<StudyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTaskEntity): Long

    @Update
    suspend fun updateTask(task: StudyTaskEntity)

    @Query("UPDATE daily_tasks SET subject = :newName WHERE subject = :oldName")
    suspend fun updateSubjectName(oldName: String, newName: String)

    @Delete
    suspend fun deleteTask(task: StudyTaskEntity)

    @Query("DELETE FROM daily_tasks WHERE isCompleted = 1")
    suspend fun clearCompletedTasks()
}

@Dao
interface ChapterDao {
    @Query("SELECT * FROM chapters ORDER BY orderIndex ASC, id ASC")
    fun getAllChaptersFlow(): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE id = :id LIMIT 1")
    suspend fun getChapterById(id: Long): ChapterEntity?

    @Query("SELECT * FROM chapters WHERE subjectId = :subjectId ORDER BY orderIndex ASC, id ASC")
    fun getChaptersForSubjectFlow(subjectId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE subjectId = :subjectId ORDER BY orderIndex ASC, id ASC")
    suspend fun getChaptersForSubject(subjectId: Long): List<ChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterEntity)

    @Query("DELETE FROM chapters WHERE subjectId = :subjectId")
    suspend fun deleteChaptersForSubject(subjectId: Long)

    @Query("DELETE FROM chapters")
    suspend fun deleteAllChapters()

    @Query("UPDATE chapters SET subjectName = :subjectName WHERE subjectId = :subjectId")
    suspend fun updateSubjectName(subjectId: Long, subjectName: String)
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM user_settings")
    fun getAllSettingsFlow(): Flow<List<SettingEntity>>

    @Query("SELECT value FROM user_settings WHERE `key` = :key LIMIT 1")
    suspend fun getSettingValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSetting(setting: SettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSettings(settings: List<SettingEntity>)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotificationsFlow(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotifications(notifications: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()
}

@Dao
interface RevisionSkipDao {
    @Query("SELECT * FROM revision_skips ORDER BY skippedAtTimestamp DESC")
    fun getAllSkipsFlow(): Flow<List<RevisionSkipEntity>>

    @Query("SELECT COUNT(*) FROM revision_skips WHERE subjectName = :subjectName AND chapterName = :chapterName")
    suspend fun getSkipCountForChapter(subjectName: String, chapterName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSkip(skip: RevisionSkipEntity)

    @Delete
    suspend fun deleteSkip(skip: RevisionSkipEntity)

    @Query("DELETE FROM revision_skips")
    suspend fun clearAllSkips()
}

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash_items ORDER BY deletedAt DESC")
    fun getAllTrashItemsFlow(): Flow<List<TrashItemEntity>>

    @Query("SELECT * FROM trash_items WHERE id = :id LIMIT 1")
    suspend fun getTrashItem(id: String): TrashItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrashItem(item: TrashItemEntity)

    @Query("DELETE FROM trash_items WHERE id = :id")
    suspend fun deleteTrashItem(id: String)

    @Query("DELETE FROM trash_items")
    suspend fun emptyTrash()
}
