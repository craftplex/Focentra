package com.niloy.focentra.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.niloy.focentra.data.local.AppDatabase
import com.niloy.focentra.data.local.entity.ChapterEntity
import com.niloy.focentra.data.local.entity.PresetEntity
import com.niloy.focentra.data.local.entity.ReminderEntity
import com.niloy.focentra.data.local.entity.SettingEntity
import com.niloy.focentra.data.local.entity.SubjectEntity
import com.niloy.focentra.data.local.entity.StudyGoalEntity
import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.data.local.entity.StudyTaskEntity
import com.niloy.focentra.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyRepositorySubjectTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: StudyRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = StudyRepository(database, context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun renamingSubjectKeepsTopicsAndChaptersAttached() = runBlocking {
        val subject = SubjectEntity(name = "Physics")
        val subjectId = repository.insertSubject(subject)
        database.topicDao().insertTopic(TopicEntity(subjectId = subjectId, subjectName = "Physics", name = "Motion"))
        repository.insertChapter(ChapterEntity(subjectId = subjectId, subjectName = "Physics", chapterName = "Vectors"))
        repository.insertSession(
            StudySessionEntity(
                startTime = 1L,
                endTime = 2L,
                durationSeconds = 1L,
                actualFocusedSeconds = 1L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Physics"
            )
        )
        repository.insertTask(StudyTaskEntity(title = "Review vectors", subject = "Physics"))
        repository.insertPreset(PresetEntity(title = "Physics focus", type = "COUNTDOWN", subject = "Physics"))

        repository.updateSubject(subject.copy(id = subjectId, name = "Mechanics"))

        assertEquals("Mechanics", database.topicDao().getAllTopics().single().subjectName)
        assertEquals("Mechanics", database.chapterDao().getAllChaptersFlow().first().single().subjectName)
        assertEquals("Mechanics", database.sessionDao().getAllSessions().single().subject)
        assertEquals("Mechanics", database.taskDao().getAllTasksFlow().first().single().subject)
        assertEquals("Mechanics", database.presetDao().getAllPresets().single().subject)
    }

    @Test
    fun deletingSubjectRemovesItsTopicsAndChapters() = runBlocking {
        val subject = SubjectEntity(name = "Chemistry")
        val subjectId = repository.insertSubject(subject)
        database.topicDao().insertTopic(TopicEntity(subjectId = subjectId, subjectName = "Chemistry", name = "Atoms"))
        repository.insertChapter(ChapterEntity(subjectId = subjectId, subjectName = "Chemistry", chapterName = "Matter"))

        repository.deleteSubject(subject.copy(id = subjectId))

        assertTrue(database.topicDao().getAllTopics().isEmpty())
        assertTrue(database.chapterDao().getAllChaptersFlow().first().isEmpty())
        assertTrue(database.subjectDao().getAllSubjects().isEmpty())
    }

    @Test
    fun csvExportAndImportPreserveQuotedAndMultilineFields() = runBlocking {
        repository.insertSession(
            StudySessionEntity(
                startTime = 1_000L,
                endTime = 2_000L,
                durationSeconds = 1L,
                actualFocusedSeconds = 1L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Chemistry, Advanced",
                topic = "Bonding \"Ionic\"",
                notes = "First line,\nsecond line says \"hello\""
            )
        )

        val csv = repository.exportSessionsCsv()
        database.sessionDao().clearAllSessions()

        assertTrue(repository.importSessionsCsvStream(csv.byteInputStream(Charsets.UTF_8)))
        val imported = database.sessionDao().getAllSessions().single()
        assertEquals("Chemistry, Advanced", imported.subject)
        assertEquals("Bonding \"Ionic\"", imported.topic)
        assertEquals("First line,\nsecond line says \"hello\"", imported.notes)
    }

    @Test
    fun largeCsvReplacementClearsExistingSessionsAndImportsAllRows() = runBlocking {
        repository.insertSession(
            StudySessionEntity(
                startTime = 1L,
                endTime = 2L,
                durationSeconds = 1L,
                actualFocusedSeconds = 1L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Old session"
            )
        )
        val header = "ID,Start Time,End Time,Total Duration (s),Focused Duration (s),Paused Duration (s),Type,Subject,Topic,Productivity,Distractions,Mood,Energy,Notes"
        val rows = (1..501).joinToString("\n") { index ->
            "$index,$index,${index + 1},60,60,0,COUNTDOWN,Imported $index,,4,0,GOOD,HIGH,"
        }

        assertTrue(
            repository.importSessionsCsvStream(
                "$header\n$rows".byteInputStream(Charsets.UTF_8),
                replaceExisting = true
            )
        )

        val sessions = database.sessionDao().getAllSessions()
        assertEquals(501, sessions.size)
        assertTrue(sessions.none { it.subject == "Old session" })
        assertEquals("Imported 501", sessions.first().subject)
    }

    @Test
    fun invalidReplacementImportsDoNotDeleteExistingSessions() = runBlocking {
        repository.insertSession(
            StudySessionEntity(
                startTime = 1L,
                endTime = 2L,
                durationSeconds = 1L,
                actualFocusedSeconds = 1L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Keep me"
            )
        )

        assertFalse(repository.importDataJson("{invalid json", replaceExisting = true))
        assertFalse(
            repository.importSessionsCsvStream(
                "ID,Start Time,End Time,Total Duration (s),Focused Duration (s),Paused Duration (s),Type,Subject\n\"broken"
                    .byteInputStream(Charsets.UTF_8),
                replaceExisting = true
            )
        )
        assertEquals("Keep me", database.sessionDao().getAllSessions().single().subject)
    }

    @Test
    fun jsonReplacementClearsExistingCollectionsIncludedInBackup() = runBlocking {
        val subjectId = repository.insertSubject(SubjectEntity(name = "Old subject"))
        database.topicDao().insertTopic(
            TopicEntity(subjectId = subjectId, subjectName = "Old subject", name = "Old topic")
        )
        repository.insertChapter(
            ChapterEntity(subjectId = subjectId, subjectName = "Old subject", chapterName = "Old chapter")
        )
        repository.insertPreset(PresetEntity(title = "Old preset", type = "COUNTDOWN"))
        repository.insertGoal(StudyGoalEntity(periodType = "DAILY", targetMinutes = 60))
        repository.insertReminder(ReminderEntity(title = "Old reminder", hour = 9, minute = 0))
        repository.insertSession(
            StudySessionEntity(
                startTime = 1L,
                endTime = 2L,
                durationSeconds = 1L,
                actualFocusedSeconds = 1L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Old session"
            )
        )
        val emptyBackup = """
            {
              "app":"Focentra",
              "sessions":[],
              "subjects":[],
              "topics":[],
              "chapters":[],
              "presets":[],
              "goals":[],
              "reminders":[]
            }
        """.trimIndent()

        assertTrue(repository.importDataJson(emptyBackup, replaceExisting = true))

        assertTrue(database.sessionDao().getAllSessions().isEmpty())
        assertTrue(database.subjectDao().getAllSubjects().isEmpty())
        assertTrue(database.topicDao().getAllTopics().isEmpty())
        assertTrue(database.chapterDao().getAllChaptersFlow().first().isEmpty())
        assertTrue(database.presetDao().getAllPresets().isEmpty())
        assertTrue(database.goalDao().getAllGoals().isEmpty())
        assertTrue(database.reminderDao().getAllReminders().isEmpty())
    }

    @Test
    fun jsonImportDeduplicatesSubjectsCaseInsensitivelyAndRemapsTopicLinks() = runBlocking {
        val backup = """
            {
              "subjects": [
                {"id": 10, "name": "Physics"},
                {"id": 11, "name": "physics"}
              ],
              "topics": [
                {"subjectId": 11, "subjectName": "physics", "name": "Motion"}
              ]
            }
        """.trimIndent()

        assertTrue(repository.importDataJson(backup, replaceExisting = true))

        val importedSubject = database.subjectDao().getAllSubjects().single()
        val importedTopic = database.topicDao().getAllTopics().single()
        assertEquals("Physics", importedSubject.name)
        assertEquals(importedSubject.id, importedTopic.subjectId)
    }

    @Test
    fun largeJsonSessionArchiveImportsEveryRecord() = runBlocking {
        val sessions = (1..2_500).joinToString(",") { index ->
            """{"startTime":$index,"endTime":${index + 60_000},"durationSeconds":60,"actualFocusedSeconds":60,"pausedSeconds":0,"sessionType":"COUNTDOWN","subject":"Subject $index"}"""
        }
        val backup = """{"sessions":[$sessions]}"""

        assertTrue(
            repository.importDataJsonStream(
                backup.byteInputStream(Charsets.UTF_8),
                replaceExisting = true
            )
        )

        val importedSessions = database.sessionDao().getAllSessions()
        assertEquals(2_500, importedSessions.size)
        assertEquals("Subject 2500", importedSessions.first().subject)
    }

    @Test
    fun streamedBackupRestoresChaptersAndTheirSubjectLinks() = runBlocking {
        val subjectId = repository.insertSubject(SubjectEntity(name = "Physics"))
        database.topicDao().insertTopic(
            TopicEntity(
                subjectId = subjectId,
                subjectName = "Physics",
                name = "Motion",
                description = "Velocity and acceleration"
            )
        )
        repository.insertPreset(
            PresetEntity(
                title = "Physics focus",
                type = "POMODORO",
                subject = "Physics",
                topic = "Motion",
                pomodoroFocusMin = 40,
                soundEnabled = false,
                vibrateEnabled = false,
                keepScreenAwake = false,
                tags = "exam,practice"
            )
        )
        repository.insertGoal(
            StudyGoalEntity(
                periodType = "DAILY",
                targetMinutes = 90,
                subjectFilter = "Physics",
                active = false
            )
        )
        repository.insertChapter(
            ChapterEntity(
                subjectId = subjectId,
                subjectName = "Physics",
                chapterName = "Vectors",
                tapCount = 2,
                status = "পড়া শেষ",
                completedClassesCount = 7
            )
        )
        database.settingDao().setSetting(
            SettingEntity(
                "exam_targets_json",
                """[{"id":"exam-1","title":"Finals","targetDateEpochMs":2000000000000,"targetHours":40,"subject":"Physics","notes":"Review"}]"""
            )
        )
        database.settingDao().setSetting(
            SettingEntity("flashcards_json", """[{"front":"Question","back":"Answer"}]""")
        )

        val output = ByteArrayOutputStream()
        repository.exportDataJsonStream(output)
        database.clearAllTables()

        assertTrue(
            repository.importDataJsonStream(
                output.toByteArray().inputStream(),
                replaceExisting = true
            )
        )
        val restoredSubject = database.subjectDao().getAllSubjects().single()
        val restoredTopic = database.topicDao().getAllTopics().single()
        val restoredChapter = database.chapterDao().getAllChaptersFlow().first().single()

        assertEquals(restoredSubject.id, restoredTopic.subjectId)
        assertEquals("Velocity and acceleration", restoredTopic.description)
        assertEquals(restoredSubject.id, restoredChapter.subjectId)
        assertEquals(2, restoredChapter.tapCount)
        assertEquals("পড়া শেষ", restoredChapter.status)
        val restoredPreset = database.presetDao().getAllPresets().single()
        assertEquals("Motion", restoredPreset.topic)
        assertEquals(40, restoredPreset.pomodoroFocusMin)
        assertFalse(restoredPreset.soundEnabled)
        assertFalse(restoredPreset.vibrateEnabled)
        assertFalse(restoredPreset.keepScreenAwake)
        assertEquals("exam,practice", restoredPreset.tags)
        val restoredGoal = database.goalDao().getAllGoals().single()
        assertEquals("Physics", restoredGoal.subjectFilter)
        assertFalse(restoredGoal.active)
        assertTrue(database.settingDao().getSettingValue("exam_targets_json")?.contains("exam-1") == true)
        assertEquals(
            """[{"front":"Question","back":"Answer"}]""",
            database.settingDao().getSettingValue("flashcards_json")
        )
    }

    @Test
    fun ensureBanglaSyllabusSeededMigratesBasicGrammar() = runBlocking {
        // Pre-insert English 2nd Paper with old chapters
        val subjectId = database.subjectDao().insertSubject(
            SubjectEntity(
                name = "English 2nd Paper",
                colorHex = "#06B6D4",
                iconName = "School",
                targetHours = 30f,
                description = "HSC English 2nd Paper Syllabus"
            )
        )
        database.chapterDao().insertChapter(
            ChapterEntity(
                subjectId = subjectId,
                subjectName = "English 2nd Paper",
                chapterName = "1. Prepositions: Appropriate Prepositions & Use in Context",
                tapCount = 0,
                status = "পড়া শুরু হয় নাই",
                orderIndex = 0
            )
        )

        // Run ensureBanglaSyllabusSeeded
        repository.ensureBanglaSyllabusSeeded()

        val chapters = database.chapterDao().getChaptersForSubject(subjectId)
        val basicGrammar = chapters.find { it.chapterName.contains("Basic Grammar (ACS)", ignoreCase = true) }
        assertNotNull(basicGrammar)
        assertEquals("13. Basic Grammar (ACS)", basicGrammar?.chapterName)
    }
}
