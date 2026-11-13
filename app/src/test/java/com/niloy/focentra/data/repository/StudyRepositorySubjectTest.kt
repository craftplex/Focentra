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
import com.niloy.focentra.data.local.entity.TrashItemType
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
    fun deletingSubjectKeepsItsRowsInTrashUntilRestored() = runBlocking {
        val subjectId = repository.insertSubject(SubjectEntity(name = "Biology"))
        database.topicDao().insertTopic(
            TopicEntity(subjectId = subjectId, subjectName = "Biology", name = "Cells")
        )
        repository.insertChapter(
            ChapterEntity(subjectId = subjectId, subjectName = "Biology", chapterName = "Genetics")
        )

        repository.deleteSubject(database.subjectDao().getSubjectById(subjectId)!!)

        val trashed = database.trashDao().getAllTrashItemsFlow().first().single()
        assertEquals(TrashItemType.DATABASE_ROWS, trashed.itemType)
        assertTrue(database.subjectDao().getAllSubjects().isEmpty())

        repository.restoreTrashItem(trashed.id)

        assertEquals("Biology", database.subjectDao().getSubjectById(subjectId)?.name)
        assertEquals("Cells", database.topicDao().getAllTopics().single().name)
        assertEquals("Genetics", database.chapterDao().getAllChaptersFlow().first().single().chapterName)
        assertTrue(database.trashDao().getAllTrashItemsFlow().first().isEmpty())
    }

    @Test
    fun deletingTodaysStudyDataArchivesAndRestoresSessionsAndRevisions() = runBlocking {
        repository.insertSession(
            StudySessionEntity(
                startTime = 1_100L,
                endTime = 1_500L,
                durationSeconds = 400L,
                actualFocusedSeconds = 400L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Physics"
            )
        )
        repository.insertSession(
            StudySessionEntity(
                startTime = 2_100L,
                endTime = 2_500L,
                durationSeconds = 400L,
                actualFocusedSeconds = 400L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "Chemistry"
            )
        )
        val revisions = """[{"id":"revision-1","subjectName":"Physics","chapterName":"Motion"}]"""

        val moved = repository.archiveTodaysStudyData(
            startTime = 1_000L,
            endTimeExclusive = 2_000L,
            revisionRecordsJson = revisions,
            dateLabel = "Test day"
        )

        assertEquals(2, moved)
        assertEquals("Chemistry", database.sessionDao().getAllSessions().single().subject)
        val trashed = database.trashDao().getAllTrashItemsFlow().first().single()
        assertEquals(TrashItemType.TODAYS_STUDY_DATA, trashed.itemType)

        repository.restoreTrashItem(trashed.id)

        assertEquals(2, database.sessionDao().getAllSessions().size)
        assertTrue(
            database.settingDao().getSettingValue("revision_history_json")?.contains("revision-1") == true
        )
        assertTrue(database.trashDao().getAllTrashItemsFlow().first().isEmpty())
    }

    @Test
    fun clearingAppDataStoresFullSnapshotAndLeavesTrashOutOfTheSnapshot() = runBlocking {
        val subjectId = repository.insertSubject(SubjectEntity(name = "History"))
        repository.insertSession(
            StudySessionEntity(
                startTime = 1L,
                endTime = 2L,
                durationSeconds = 1L,
                actualFocusedSeconds = 1L,
                pausedSeconds = 0L,
                sessionType = "COUNTDOWN",
                subject = "History"
            )
        )
        database.settingDao().setSetting(SettingEntity("custom_data", "keep"))

        repository.clearAllAppDataToTrash()

        val trashed = database.trashDao().getAllTrashItemsFlow().first().single()
        assertEquals(TrashItemType.APP_SNAPSHOT, trashed.itemType)
        assertTrue(database.sessionDao().getAllSessions().isEmpty())
        assertTrue(database.trashDao().getAllTrashItemsFlow().first().isNotEmpty())

        repository.restoreTrashItem(trashed.id)

        assertEquals("History", database.subjectDao().getSubjectById(subjectId)?.name)
        assertEquals("History", database.sessionDao().getAllSessions().single().subject)
        assertEquals("keep", repository.getSetting("custom_data"))
        assertTrue(database.trashDao().getAllTrashItemsFlow().first().isEmpty())
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
