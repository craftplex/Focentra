package com.niloy.focentra.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.niloy.focentra.data.local.dao.*
import com.niloy.focentra.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class SyllabusData(
    val name: String,
    val colorHex: String,
    val iconName: String,
    val targetHours: Float,
    val description: String,
    val chapters: List<String>
)

@Database(
    entities = [
        StudySessionEntity::class,
        SubjectEntity::class,
        TopicEntity::class,
        StudyGoalEntity::class,
        PresetEntity::class,
        AchievementEntity::class,
        ReminderEntity::class,
        SettingEntity::class,
        StudyTaskEntity::class,
        ChapterEntity::class,
        NotificationEntity::class,
        RevisionSkipEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): StudySessionDao
    abstract fun subjectDao(): SubjectDao
    abstract fun topicDao(): TopicDao
    abstract fun goalDao(): StudyGoalDao
    abstract fun presetDao(): PresetDao
    abstract fun achievementDao(): AchievementDao
    abstract fun reminderDao(): ReminderDao
    abstract fun settingDao(): SettingDao
    abstract fun taskDao(): StudyTaskDao
    abstract fun chapterDao(): ChapterDao
    abstract fun notificationDao(): NotificationDao
    abstract fun revisionSkipDao(): RevisionSkipDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_startTime` ON `study_sessions` (`startTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_subject` ON `study_sessions` (`subject`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_study_sessions_completionStatus` ON `study_sessions` (`completionStatus`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_tasks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `subject` TEXT NOT NULL, `dateEpochMs` INTEGER NOT NULL, `isCompleted` INTEGER NOT NULL, `createdTimestamp` INTEGER NOT NULL)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subjectId` INTEGER NOT NULL, `subjectName` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `totalClasses` INTEGER NOT NULL, `completedClassesCount` INTEGER NOT NULL, `isRevision1Completed` INTEGER NOT NULL, `isRevision2Completed` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_subjectId` ON `chapters` (`subjectId`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `tapCount` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `status` TEXT NOT NULL DEFAULT 'পড়া শুরু হয় নাই'")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `lastStudiedTimestamp` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `completedTimestamp` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `notifications` (`id` TEXT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `message` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isRead` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_timestamp` ON `notifications` (`timestamp`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `revision_skips` (`id` TEXT NOT NULL, `subjectName` TEXT NOT NULL, `chapterName` TEXT NOT NULL, `reason` TEXT NOT NULL, `skippedAtTimestamp` INTEGER NOT NULL, `scheduledRecapTimestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_revision_skips_subjectName` ON `revision_skips` (`subjectName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_revision_skips_chapterName` ON `revision_skips` (`chapterName`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_sessions` ADD COLUMN `targetId` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `study_sessions` ADD COLUMN `selectedChapter` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `chapters` ADD COLUMN `historicalStudySeconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE `chapters`
                    SET `historicalStudySeconds` = `historicalStudySeconds` + COALESCE((
                        SELECT SUM(s.`actualFocusedSeconds`)
                        FROM `study_sessions` s
                        WHERE s.`sessionType` = 'MANUAL'
                          AND s.`topic` = 'Previously studied'
                          AND LOWER(TRIM(s.`subject`)) = LOWER(TRIM(`chapters`.`subjectName`))
                          AND LOWER(TRIM(s.`selectedChapter`)) = LOWER(TRIM(`chapters`.`chapterName`))
                    ), 0)
                    WHERE EXISTS (
                        SELECT 1
                        FROM `study_sessions` s
                        WHERE s.`sessionType` = 'MANUAL'
                          AND s.`topic` = 'Previously studied'
                          AND LOWER(TRIM(s.`subject`)) = LOWER(TRIM(`chapters`.`subjectName`))
                          AND LOWER(TRIM(s.`selectedChapter`)) = LOWER(TRIM(`chapters`.`chapterName`))
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    DELETE FROM `study_sessions`
                    WHERE `sessionType` = 'MANUAL'
                      AND `topic` = 'Previously studied'
                      AND EXISTS (
                          SELECT 1
                          FROM `chapters` c
                          WHERE LOWER(TRIM(c.`subjectName`)) = LOWER(TRIM(`study_sessions`.`subject`))
                            AND LOWER(TRIM(c.`chapterName`)) = LOWER(TRIM(`study_sessions`.`selectedChapter`))
                      )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focentra_database.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val settingDao = database.settingDao()
            val achievementDao = database.achievementDao()

            // Achievements
            val achievements = listOf(
                AchievementEntity(id = "first_session", title = "First Step", description = "Complete your very first focus study session", iconName = "EmojiEvents", category = "MILESTONE", target = 1f),
                AchievementEntity(id = "streak_3", title = "Momentum", description = "Maintain a 3-day study streak", iconName = "LocalFireDepartment", category = "STREAK", target = 3f),
                AchievementEntity(id = "streak_7", title = "Habit Builder", description = "Maintain a 7-day study streak", iconName = "LocalFireDepartment", category = "STREAK", target = 7f),
                AchievementEntity(id = "streak_14", title = "Unstoppable", description = "Maintain a 14-day study streak", iconName = "LocalFireDepartment", category = "STREAK", target = 14f),
                AchievementEntity(id = "streak_30", title = "Iron Will", description = "Maintain a 30-day study streak", iconName = "Whatshot", category = "STREAK", target = 30f),
                AchievementEntity(id = "hours_10", title = "Bronze Scholar", description = "Accumulate 10 hours of focused study time", iconName = "Timer", category = "TIME", target = 10f),
                AchievementEntity(id = "hours_50", title = "Silver Scholar", description = "Accumulate 50 hours of focused study time", iconName = "WorkspacePremium", category = "TIME", target = 50f),
                AchievementEntity(id = "hours_100", title = "Gold Scholar", description = "Accumulate 100 hours of focused study time", iconName = "School", category = "TIME", target = 100f),
                AchievementEntity(id = "hours_500", title = "Diamond Scholar", description = "Accumulate 500 hours of focused study time", iconName = "Diamond", category = "TIME", target = 500f),
                AchievementEntity(id = "hours_1000", title = "Grandmaster Mind", description = "Accumulate 1,000 hours of focused study time", iconName = "MilitaryTech", category = "TIME", target = 1000f),
                AchievementEntity(id = "daily_goals_30", title = "Goal Crusher", description = "Complete daily study goals on 30 separate days", iconName = "GpsFixed", category = "GOAL", target = 30f),
                AchievementEntity(id = "night_owl", title = "Night Owl", description = "Complete a study session past midnight (12 AM - 4 AM)", iconName = "NightsStay", category = "SPECIAL", target = 1f),
                AchievementEntity(id = "early_bird", title = "Early Bird", description = "Complete a study session in early morning (5 AM - 7 AM)", iconName = "WbSunny", category = "SPECIAL", target = 1f),
                AchievementEntity(id = "deep_work_master", title = "Deep Work Master", description = "Complete a continuous 90+ minute focused session with 5/5 productivity", iconName = "Psychology", category = "SPECIAL", target = 1f)
            )
            achievementDao.insertAchievements(achievements)

            // Default Settings
            settingDao.setSetting(SettingEntity("theme", "midnight"))
            settingDao.setSetting(SettingEntity("screen_awake", "true"))
            settingDao.setSetting(SettingEntity("sound_enabled", "true"))
            settingDao.setSetting(SettingEntity("vibration_intensity", "MEDIUM"))
            settingDao.setSetting(SettingEntity("anim_intensity", "STANDARD"))
            settingDao.setSetting(SettingEntity("language", "en"))
            settingDao.setSetting(SettingEntity("onboarding_completed", "true"))
        }
    }
}
