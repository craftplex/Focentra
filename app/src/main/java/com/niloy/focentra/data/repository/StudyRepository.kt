package com.niloy.focentra.data.repository

import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.niloy.focentra.data.local.AppDatabase
import com.niloy.focentra.data.local.SyllabusData
import com.niloy.focentra.data.local.entity.*
import com.niloy.focentra.data.provider.DaynexaContract
import com.niloy.focentra.data.provider.DaynexaIntegrationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

private fun android.util.JsonWriter.writeJsonValue(jsonValue: Any?) {
    when (jsonValue) {
        null, JSONObject.NULL -> nullValue()
        is JSONObject -> {
            beginObject()
            val keys = jsonValue.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                name(key)
                writeJsonValue(jsonValue.get(key))
            }
            endObject()
        }
        is JSONArray -> {
            beginArray()
            for (index in 0 until jsonValue.length()) {
                writeJsonValue(jsonValue.get(index))
            }
            endArray()
        }
        is String -> value(jsonValue)
        is Boolean -> value(jsonValue)
        is Number -> value(jsonValue)
        else -> value(jsonValue.toString())
    }
}

internal fun encodeCsvRecord(fields: List<String>): String =
    fields.joinToString(",") { field ->
        if (field.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }

internal fun parseCsvRecords(csv: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    val record = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var index = 0

    while (index < csv.length) {
        val character = csv[index]
        when {
            character == '"' && inQuotes && index + 1 < csv.length && csv[index + 1] == '"' -> {
                field.append('"')
                index += 2
                continue
            }
            character == '"' -> inQuotes = !inQuotes
            character == ',' && !inQuotes -> {
                record.add(field.toString())
                field.clear()
            }
            (character == '\n' || character == '\r') && !inQuotes -> {
                record.add(field.toString())
                field.clear()
                records.add(record.toList())
                record.clear()
                if (character == '\r' && index + 1 < csv.length && csv[index + 1] == '\n') {
                    index++
                }
            }
            else -> field.append(character)
        }
        index++
    }

    require(!inQuotes) { "Unterminated quoted CSV field." }
    if (record.isNotEmpty() || field.isNotEmpty()) {
        record.add(field.toString())
        records.add(record)
    }
    return records
}

class StudyRepository(private val database: AppDatabase, context: Context) {
    
    private val appContext = context.applicationContext
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val allSessions: Flow<List<StudySessionEntity>> = database.sessionDao().getAllSessionsFlow()
    val allSubjects: Flow<List<SubjectEntity>> = database.subjectDao().getAllSubjectsFlow()
    val allTopics: Flow<List<TopicEntity>> = database.topicDao().getAllTopicsFlow()
    val allGoals: Flow<List<StudyGoalEntity>> = database.goalDao().getActiveGoalsFlow()
    val allPresets: Flow<List<PresetEntity>> = database.presetDao().getAllPresetsFlow()
    val allAchievements: Flow<List<AchievementEntity>> = database.achievementDao().getAllAchievementsFlow()
    val allReminders: Flow<List<ReminderEntity>> = database.reminderDao().getAllRemindersFlow()
    val allSettings: Flow<List<SettingEntity>> = database.settingDao().getAllSettingsFlow()
    val allTasks: Flow<List<StudyTaskEntity>> = database.taskDao().getAllTasksFlow()
    val allChapters: Flow<List<ChapterEntity>> = database.chapterDao().getAllChaptersFlow()
    val allNotifications: Flow<List<NotificationEntity>> = database.notificationDao().getAllNotificationsFlow()
    val allRevisionSkips: Flow<List<RevisionSkipEntity>> = database.revisionSkipDao().getAllSkipsFlow()

    suspend fun insertNotification(notification: NotificationEntity) = withContext(Dispatchers.IO) {
        database.notificationDao().insertNotification(notification)
    }

    suspend fun markAllNotificationsAsRead() = withContext(Dispatchers.IO) {
        database.notificationDao().markAllAsRead()
    }

    suspend fun deleteNotificationById(id: String) = withContext(Dispatchers.IO) {
        database.notificationDao().deleteNotificationById(id)
    }

    suspend fun clearAllNotifications() = withContext(Dispatchers.IO) {
        database.notificationDao().clearAllNotifications()
    }

    suspend fun insertRevisionSkip(skip: RevisionSkipEntity) = withContext(Dispatchers.IO) {
        database.revisionSkipDao().insertSkip(skip)
    }

    suspend fun getSkipCountForChapter(subjectName: String, chapterName: String): Int = withContext(Dispatchers.IO) {
        database.revisionSkipDao().getSkipCountForChapter(subjectName, chapterName)
    }

    suspend fun deleteRevisionSkip(skip: RevisionSkipEntity) = withContext(Dispatchers.IO) {
        database.revisionSkipDao().deleteSkip(skip)
    }

    suspend fun clearAllRevisionSkips() = withContext(Dispatchers.IO) {
        database.revisionSkipDao().clearAllSkips()
    }

    suspend fun ensureBanglaSyllabusSeeded() = withContext(Dispatchers.IO) {
        // First, clean up duplicate subjects in Room DB if any exist
        deduplicateSubjectsInternal()

        val existingSubjects = database.subjectDao().getAllSubjects()

        val syllabiToSeed = listOf(
            SyllabusData(
                name = "বাংলা ১ম পত্র",
                colorHex = "#0D9488",
                iconName = "MenuBook",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক বাংলা প্রথম পত্র সিলেবাস",
                chapters = listOf(
                    "১. বাঙ্গালার নব্য লেখকদিগের প্রতি নিবেদন (গদ্য)",
                    "২. অপরিচিতা (গদ্য)",
                    "৩. বিলাসী (গদ্য)",
                    "৪. গৃহ (গদ্য)",
                    "৫. আহ্বান (গদ্য)",
                    "৬. আমার পথ (গদ্য)",
                    "৭. মানব-কল্যাণ (গদ্য)",
                    "৮. মাসি-পিসি (গদ্য)",
                    "৯. বায়ান্নর দিনগুলো (গদ্য)",
                    "১০. রেইনকোট (গদ্য)",
                    "১১. নেকলেস (গদ্য)",
                    "১২. অর্ধাঙ্গী / সাহিত্যের খেলা (গদ্য)",
                    "১৩. বিভীষণের প্রতি মেঘনাদ (পদ্য)",
                    "১৪. সোনার তরী (পদ্য)",
                    "১৫. বিদ্রোহী (পদ্য)",
                    "১৬. প্রতিদান (পদ্য)",
                    "১৭. সুচেতনা (পদ্য)",
                    "১৮. তাহারেই পড়ে মনে (পদ্য)",
                    "১৯. পদ্মা (পদ্য)",
                    "২০. ১৮ বছর বয়স (পদ্য)",
                    "২১. ফেব্রুয়ারি ১৯৬৯ (পদ্য)",
                    "২২. আমি কিংবদন্তির কথা বলছি (পদ্য)",
                    "২৩. নূরলদীনের কথা মনে পড়ে যায় (পদ্য)",
                    "২৪. ছবি (পদ্য)",
                    "২৫. লালসালু (সহপাঠ - উপন্যাস)",
                    "২৬. সিরাজউদ্দৌলা (সহপাঠ - নাটক)"
                )
            ),
            SyllabusData(
                name = "বাংলা ২য় পত্র",
                colorHex = "#F59E0B",
                iconName = "MenuBook",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক বাংলা দ্বিতীয় পত্র সিলেবাস",
                chapters = listOf(
                    "১. বাংলা উচ্চারণের নিয়ম (ব্যাকরণ)",
                    "২. বাংলা বানানের নিয়ম (ব্যাকরণ)",
                    "৩. বাংলা ভাষার ব্যাকরণিক শব্দশ্রেণি (ব্যাকরণ)",
                    "৪. বাংলা শব্দ গঠন: উপসর্গ, প্রত্যয় ও সমাস (ব্যাকরণ)",
                    "৫. বাক্য তত্ত্ব: বাক্যের শ্রেণিবিভাগ ও রূপান্তর (ব্যাকরণ)",
                    "৬. বাংলা ভাষার অপপ্রয়োগ ও শুদ্ধ প্রয়োগ (ব্যাকরণ)",
                    "৭. পারিভাষিক শব্দ অথবা অনুবাদ (নির্মিতি)",
                    "৮. দিনলিপি লিখন অথবা প্রতিবেদন তৈরি (নির্মিতি)",
                    "৯. বৈদ্যুতিন চিঠি (Email) অথবা আবেদনপত্র (নির্মিতি)",
                    "১০. সারাংশ/সারসংক্ষেপ অথবা ভাব-সম্প্রসারণ (নির্মিতি)",
                    "১১. সংলাপ লিখন অথবা ক্ষুদে গল্প রচনা (নির্মিতি)",
                    "১২. প্রবন্ধ-নিবন্ধ লিখন (রচনা) (নির্মিতি)"
                )
            ),
            SyllabusData(
                name = "English 1st Paper",
                colorHex = "#3B82F6",
                iconName = "School",
                targetHours = 30f,
                description = "HSC English 1st Paper Syllabus",
                chapters = listOf(
                    "1. Unit: People or Institutions Making History",
                    "2. Unit: Education and Life",
                    "3. Unit: Youthful Achievers",
                    "4. Unit: Human Relationship",
                    "5. Unit: Art and Craft",
                    "6. Unit: Tour and Travels",
                    "7. Unit: Environment and Nature",
                    "8. Unit: Myths and Literature",
                    "9. Unit: Human Rights",
                    "10. Unit: Diaspora",
                    "11. Unit: Peace and Conflict",
                    "12. Reading: Seen Comprehension Passages & Poems",
                    "13. Reading: Unseen Comprehension Passages",
                    "14. Reading: Information Transfer (Table Completion)",
                    "15. Reading: Flow Chart Creation",
                    "16. Reading: Summary Writing",
                    "17. Reading: Cloze Test with Clues",
                    "18. Reading: Cloze Test without Clues",
                    "19. Reading: Rearranging Sentences",
                    "20. Writing: Graph and Chart Interpretation & Analysis",
                    "21. Writing: Story Completing (Based on hints)",
                    "22. Writing: Informal Letters",
                    "23. Writing: E-mail Writing"
                )
            ),
            SyllabusData(
                name = "English 2nd Paper",
                colorHex = "#06B6D4",
                iconName = "School",
                targetHours = 30f,
                description = "HSC English 2nd Paper Syllabus",
                chapters = listOf(
                    "1. Prepositions: Appropriate Prepositions & Use in Context",
                    "2. Special Uses of Some Words and Phrases",
                    "3. Completing Sentences (Conditionals, Infinitives, Gerunds, Clauses)",
                    "4. Right Form of Verbs (Subject-Verb Agreement & Tenses)",
                    "5. Narrative Style (Direct to Indirect Speech)",
                    "6. Modifiers (Pre-modifiers & Post-modifiers)",
                    "7. Sentence Connectors (Logical Linkers)",
                    "8. Synonyms and Antonyms",
                    "9. Punctuation (Capitalization & Marks)",
                    "10. Formal Letters (Job Application with CV, Complaint, Inquiry)",
                    "11. Paragraph Writing 1: Listing, Definition, Descriptive",
                    "12. Paragraph Writing 2: Comparison-Contrast /Cause-Effect"
                )
            ),
            SyllabusData(
                name = "পদার্থবিজ্ঞান ১ম পত্র",
                colorHex = "#8B5CF6",
                iconName = "Psychology",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক পদার্থবিজ্ঞান প্রথম পত্র সিলেবাস",
                chapters = listOf(
                    "১. ভৌত জগৎ ও পরিমাপ",
                    "২. ভেক্টর",
                    "৩. গতিবিদ্যা",
                    "৪. নিউটনীয় বলবিদ্যা",
                    "৫. কাজ, শক্তি ও ক্ষমতা",
                    "৬. মহাকর্ষ ও অভিকর্ষ",
                    "৭. পদার্থের গাঠনিক ধর্ম",
                    "৮. পর্যাবৃত্ত গতি",
                    "৯. তরঙ্গ",
                    "১০. আদর্শ গ্যাস ও গ্যাসের গতিতত্ত্ব"
                )
            ),
            SyllabusData(
                name = "পদার্থবিজ্ঞান ২য় পত্র",
                colorHex = "#A855F7",
                iconName = "Psychology",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক পদার্থবিজ্ঞান দ্বিতীয় পত্র সিলেবাস",
                chapters = listOf(
                    "১. তাপগতিবিদ্যা",
                    "২. স্থির তড়িৎ",
                    "৩. চল তড়িৎ",
                    "৪. তড়িৎ প্রবাহের চৌম্বক ক্রিয়া ও চৌম্বকত্ব",
                    "৫. তড়িৎ চৌম্বকীয় আবেশ ও পরিবর্তী প্রবাহ",
                    "৬. জ্যামিতিক আলোকবিজ্ঞান",
                    "৭. ভৌত আলোকবিজ্ঞান",
                    "৮. আধুনিক পদার্থবিজ্ঞানের সূচনা",
                    "৯. পরমাণুর মডেল ও নিউক্লিয়ার পদার্থবিজ্ঞান",
                    "১০. সেমিকন্ডাক্টর ও ইলেকট্রনিক্স",
                    "১১. জ্যোতির্বিজ্ঞান"
                )
            ),
            SyllabusData(
                name = "রসায়ন ১ম পত্র",
                colorHex = "#10B981",
                iconName = "Science",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক রসায়ন প্রথম পত্র সিলেবাস",
                chapters = listOf(
                    "১. ল্যাবরেটরির নিরাপদ ব্যবহার",
                    "২. গুণগত রসায়ন",
                    "৩. মৌলের পর্যায়বৃত্ত ধর্ম ও রাসায়নিক বন্ধন",
                    "৪. রাসায়নিক পরিবর্তন",
                    "৫. কর্মমুখী রসায়ন"
                )
            ),
            SyllabusData(
                name = "রসায়ন ২য় পত্র",
                colorHex = "#059669",
                iconName = "Science",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক রসায়ন দ্বিতীয় পত্র সিলেবাস",
                chapters = listOf(
                    "১. পরিবেশ রসায়ন",
                    "২. জৈব রসায়ন",
                    "৩. পরিমাণগত রসায়ন",
                    "৪. তড়িৎ রসায়ন",
                    "৫. অর্থনৈতিক রসায়ন"
                )
            ),
            SyllabusData(
                name = "জীববিজ্ঞান ১ম পত্র (উদ্ভিদ)",
                colorHex = "#22C55E",
                iconName = "LocalFlorist",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক জীববিজ্ঞান প্রথম পত্র (উদ্ভিদবিজ্ঞান) সিলেবাস",
                chapters = listOf(
                    "১. কোষ ও এর গঠন",
                    "২. কোষ বিভাজন",
                    "৩. কোষ রসায়ন",
                    "৪. অণুজীব",
                    "৫. শৈবাল ও ছত্রাক",
                    "৬. ব্রায়োফাইটা ও টেরিডোফাইটা",
                    "৭. নগ্নবীজী ও আবৃতবীজী উদ্ভিদ",
                    "৮. টিস্যু ও টিস্যুতন্ত্র",
                    "৯. উদ্ভিদ শারীরতত্ত্ব",
                    "১০. উদ্ভিদ প্রজনন",
                    "১১. জীবপ্রযুক্তি",
                    "১২. জীবের পরিবেশ, বিস্তার ও সংরক্ষণ"
                )
            ),
            SyllabusData(
                name = "জীববিজ্ঞান ২য় পত্র (প্রাণী)",
                colorHex = "#16A34A",
                iconName = "Pets",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক জীববিজ্ঞান দ্বিতীয় পত্র (প্রাণীবিজ্ঞান) সিলেবাস",
                chapters = listOf(
                    "১. প্রাণীর বিভিন্নতা ও শ্রেণিবিন্যাস",
                    "২. প্রাণীর পরিচিতি: হাইড্রা, ঘাসফড়িং, রুই মাছ",
                    "৩. মানব শারীরতত্ত্ব: পরিপাক ও শোষণ",
                    "৪. মানব শারীরতত্ত্ব: রক্ত ও সংবহন",
                    "৫. মানব শারীরতত্ত্ব: শ্বসন ও শ্বাসক্রিয়া",
                    "৬. মানব শারীরতত্ত্ব: বর্জ্য ও নিষ্কাশন",
                    "৭. মানব শারীরতত্ত্ব: চলন ও অঙ্গচালনা",
                    "৮. মানব শারীরতত্ত্ব: সমন্বয় ও নিয়ন্ত্রণ",
                    "৯. প্রাণীর জীবনের ধারাবাহিকতা",
                    "১০. মানবদেহের প্রতিরক্ষা",
                    "১১. জিনতত্ত্ব ও বিবর্তন",
                    "১২. আচরণ"
                )
            ),
            SyllabusData(
                name = "তথ্য ও যোগাযোগ প্রযুক্তি",
                colorHex = "#EC4899",
                iconName = "Computer",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক তথ্য ও যোগাযোগ প্রযুক্তি (ICT) সিলেবাস",
                chapters = listOf(
                    "১. তথ্য ও যোগাযোগ প্রযুক্তি: বিশ্ব ও বাংলাদেশ প্রেক্ষিত",
                    "২. কমিউনিকেশন সিস্টেমস ও নেটওয়ার্কিং",
                    "৩. সংখ্যা পদ্ধতি ও ডিজিটাল ডিভাইস",
                    "৪. ওয়েব ডিজাইন পরিচিতি এবং এইচটিএমএল",
                    "৫. প্রোগ্রামিং ভাষা",
                    "৬. ডাটাবেজ ম্যানেজমেন্ট সিস্টেম"
                )
            ),
            SyllabusData(
                name = "উচ্চতর গণিত ১ম পত্র",
                colorHex = "#6366F1",
                iconName = "Calculate",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক উচ্চতর গণিত প্রথম পত্র সিলেবাস",
                chapters = listOf(
                    "১. ম্যাট্রিক্স ও নির্ণায়ক",
                    "২. ভেক্টর",
                    "৩. সরলরেখা",
                    "৪. বৃত্ত",
                    "৫. বিন্যাস ও সমাবেশ",
                    "৬. ত্রিকোণমিতিক অনুপাত",
                    "৭. সংযুক্ত কোণের ত্রিকোণমিতিক অনুপাত",
                    "৮. ফাংশন ও ফাংশনের লেখচিত্র",
                    "৯. অন্তরীকরণ",
                    "১০. যৌগজীকরণ"
                )
            ),
            SyllabusData(
                name = "উচ্চতর গণিত ২য় পত্র",
                colorHex = "#0284C7",
                iconName = "Calculate",
                targetHours = 30f,
                description = "উচ্চমাধ্যমিক উচ্চতর গণিত দ্বিতীয় পত্র সিলেবাস",
                chapters = listOf(
                    "১. বাস্তব সংখ্যা ও অসমতা",
                    "২. রৈখিক প্রোগ্রামিং",
                    "৩. জটিল সংখ্যা",
                    "৪. বহুপদী ও বহুপদী সমীকরণ",
                    "৫. দ্বিপদী বিস্তৃতি",
                    "৬. কনিক",
                    "৭. বিপরীত ত্রিকোণমিতিক ফাংশন ও ত্রিকোণমিতিক সমীকরণ",
                    "৮. স্থিতিবিদ্যা",
                    "৯. সমতলে বস্তুকণার গতি",
                    "১০. বিস্তার পরিমাপ ও সম্ভাবনা"
                )
            )
        )

        syllabiToSeed.forEach { syllabus ->
            val existing = existingSubjects.find { it.name.trim().equals(syllabus.name.trim(), ignoreCase = true) }
            if (existing == null) {
                val subjectId = database.subjectDao().insertSubject(
                    SubjectEntity(
                        name = syllabus.name,
                        colorHex = syllabus.colorHex,
                        iconName = syllabus.iconName,
                        targetHours = syllabus.targetHours,
                        description = syllabus.description
                    )
                )
                syllabus.chapters.forEachIndexed { index, title ->
                    database.chapterDao().insertChapter(
                        ChapterEntity(
                            subjectId = subjectId,
                            subjectName = syllabus.name,
                            chapterName = title,
                            tapCount = 0,
                            status = "পড়া শুরু হয় নাই",
                            orderIndex = index
                        )
                    )
                }
            }
        }
    }

    private suspend fun deduplicateSubjectsInternal() {
        val allSubjects = database.subjectDao().getAllSubjects()
        val grouped = allSubjects.groupBy { it.name.trim().lowercase() }
        grouped.forEach { (_, subjects) ->
            if (subjects.size > 1) {
                // Keep the one with lowest ID (original entry)
                val sorted = subjects.sortedBy { it.id }
                val keepSubject = sorted.first()
                val duplicates = sorted.drop(1)

                val allChapters = database.chapterDao().getAllChaptersFlow().first()
                val allTopics = database.topicDao().getAllTopics()

                duplicates.forEach { dup ->
                    // Reassign chapters from duplicate to original
                    allChapters.filter { it.subjectId == dup.id }.forEach { ch ->
                        database.chapterDao().insertChapter(ch.copy(subjectId = keepSubject.id, subjectName = keepSubject.name))
                    }
                    // Reassign topics from duplicate to original
                    allTopics.filter { it.subjectId == dup.id }.forEach { tp ->
                        database.topicDao().insertTopic(tp.copy(subjectId = keepSubject.id, subjectName = keepSubject.name))
                    }
                    // Delete duplicate subject
                    database.subjectDao().deleteSubject(dup)
                }
            }
        }
    }

    suspend fun deduplicateSubjects() = withContext(Dispatchers.IO) {
        deduplicateSubjectsInternal()
    }

    suspend fun insertChapter(chapter: ChapterEntity): Long = withContext(Dispatchers.IO) {
        database.chapterDao().insertChapter(chapter)
    }

    suspend fun updateChapter(chapter: ChapterEntity) = withContext(Dispatchers.IO) {
        database.chapterDao().updateChapter(chapter)
    }

    suspend fun deleteChapter(chapter: ChapterEntity) = withContext(Dispatchers.IO) {
        database.chapterDao().deleteChapter(chapter)
    }

    suspend fun insertTask(task: StudyTaskEntity) = withContext(Dispatchers.IO) {
        database.taskDao().insertTask(task)
    }

    suspend fun updateTask(task: StudyTaskEntity) = withContext(Dispatchers.IO) {
        database.taskDao().updateTask(task)
    }

    suspend fun deleteTask(task: StudyTaskEntity) = withContext(Dispatchers.IO) {
        database.taskDao().deleteTask(task)
    }

    suspend fun clearCompletedTasks() = withContext(Dispatchers.IO) {
        database.taskDao().clearCompletedTasks()
    }

    suspend fun insertSession(session: StudySessionEntity): Long = withContext(Dispatchers.IO) {
        val id = database.sessionDao().insertSession(session)
        val savedSession = session.copy(id = id)
        checkAndUnlockAchievements()
        sendDaynexaSessionBroadcast(savedSession)
        id
    }

    private fun sendDaynexaSessionBroadcast(session: StudySessionEntity) {
        repositoryScope.launch {
            try {
                // 1. Verify User Consent Gate
                val consent = database.settingDao().getSettingValue(DaynexaContract.SETTING_DAYNEXA_CONSENT)
                if (consent.equals("true", ignoreCase = true)) {
                    
                    // 2. Map to Daynexa Integration API v1 Contract
                    val intent = Intent(DaynexaContract.ACTION_SESSION_COMPLETED).apply {
                        putExtra(DaynexaContract.COLUMN_SESSION_ID, session.id)
                        putExtra(DaynexaContract.COLUMN_SUBJECT, session.subject)
                        putExtra(DaynexaContract.COLUMN_TOPIC, session.topic)
                        putExtra(DaynexaContract.COLUMN_START_TIME, session.startTime)
                        putExtra(DaynexaContract.COLUMN_END_TIME, session.endTime)
                        
                        val durationMin = if (session.actualFocusedSeconds > 0) {
                            (session.actualFocusedSeconds / 60).coerceAtLeast(1)
                        } else {
                            (session.durationSeconds / 60).coerceAtLeast(1)
                        }
                        putExtra(DaynexaContract.COLUMN_DURATION, durationMin)
                        putExtra(DaynexaContract.COLUMN_COMPLETION_STATUS, session.completionStatus.uppercase())
                        
                        val focusScore = DaynexaIntegrationHelper.calculateFocusScore(session)
                        putExtra(DaynexaContract.COLUMN_FOCUS_SCORE, focusScore)
                        putExtra(DaynexaContract.COLUMN_SCHEMA_VERSION, DaynexaContract.SCHEMA_VERSION)
                    }

                    // 3. SECURE BROADCAST: Use permission protection
                    // Only apps that declare com.focentra.permission.READ_SESSIONS in their manifest can receive this.
                    // This prevents data leakage to unauthorized apps.
                    appContext.sendBroadcast(intent, "com.focentra.permission.READ_SESSIONS")
                }
            } catch (e: Exception) {
                // Silently fail integration broadcasts to avoid breaking core app flow
                e.printStackTrace()
            }
        }
    }

    suspend fun updateSession(session: StudySessionEntity) = withContext(Dispatchers.IO) {
        database.sessionDao().updateSession(session)
        checkAndUnlockAchievements()
    }

    suspend fun deleteSession(session: StudySessionEntity) = withContext(Dispatchers.IO) {
        database.sessionDao().deleteSession(session)
        checkAndUnlockAchievements()
    }

    suspend fun clearAllSessions() = withContext(Dispatchers.IO) {
        database.sessionDao().clearAllSessions()
    }

    suspend fun clearAllAppData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        AppDatabase.populateInitialData(database)
    }

    suspend fun getSessionById(id: Long): StudySessionEntity? = withContext(Dispatchers.IO) {
        database.sessionDao().getSessionById(id)
    }

    suspend fun getAllSessionsList(): List<StudySessionEntity> = withContext(Dispatchers.IO) {
        database.sessionDao().getAllSessions()
    }

    suspend fun getSessionsBetween(start: Long, end: Long): List<StudySessionEntity> = withContext(Dispatchers.IO) {
        database.sessionDao().getSessionsBetween(start, end)
    }

    // Subjects
    suspend fun insertSubject(subject: SubjectEntity): Long = withContext(Dispatchers.IO) {
        database.subjectDao().insertSubject(subject)
    }

    suspend fun updateSubject(subject: SubjectEntity) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existingSubject = database.subjectDao().getSubjectById(subject.id)
            database.subjectDao().updateSubject(subject)
            if (existingSubject != null && existingSubject.name != subject.name) {
                database.topicDao().updateSubjectName(subject.id, subject.name)
                database.chapterDao().updateSubjectName(subject.id, subject.name)
                database.sessionDao().updateSubjectName(existingSubject.name, subject.name)
                database.taskDao().updateSubjectName(existingSubject.name, subject.name)
                database.presetDao().updateSubjectName(existingSubject.name, subject.name)
            }
        }
    }

    suspend fun deleteSubject(subject: SubjectEntity) = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.topicDao().deleteTopicsForSubject(subject.id)
            database.chapterDao().deleteChaptersForSubject(subject.id)
            database.subjectDao().deleteSubject(subject)
        }
    }

    // Topics
    suspend fun insertTopic(topic: TopicEntity): Long = withContext(Dispatchers.IO) {
        database.topicDao().insertTopic(topic)
    }

    suspend fun deleteTopic(topic: TopicEntity) = withContext(Dispatchers.IO) {
        database.topicDao().deleteTopic(topic)
    }

    // Goals
    suspend fun insertGoal(goal: StudyGoalEntity) = withContext(Dispatchers.IO) {
        database.goalDao().insertGoal(goal)
    }

    suspend fun updateGoal(goal: StudyGoalEntity) = withContext(Dispatchers.IO) {
        database.goalDao().updateGoal(goal)
    }

    // Presets
    suspend fun insertPreset(preset: PresetEntity): Long = withContext(Dispatchers.IO) {
        database.presetDao().insertPreset(preset)
    }

    suspend fun updatePreset(preset: PresetEntity) = withContext(Dispatchers.IO) {
        database.presetDao().updatePreset(preset)
    }

    suspend fun deletePreset(preset: PresetEntity) = withContext(Dispatchers.IO) {
        database.presetDao().deletePreset(preset)
    }

    // Reminders
    suspend fun insertReminder(reminder: ReminderEntity): Long = withContext(Dispatchers.IO) {
        database.reminderDao().insertReminder(reminder)
    }

    suspend fun updateReminder(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        database.reminderDao().updateReminder(reminder)
    }

    suspend fun deleteReminder(reminder: ReminderEntity) = withContext(Dispatchers.IO) {
        database.reminderDao().deleteReminder(reminder)
    }

    // Settings
    suspend fun getSetting(key: String, default: String = ""): String = withContext(Dispatchers.IO) {
        database.settingDao().getSettingValue(key) ?: default
    }

    suspend fun setSetting(key: String, value: String) = withContext(Dispatchers.IO) {
        database.settingDao().setSetting(SettingEntity(key, value))
    }

    // Achievement evaluation
    suspend fun checkAndUnlockAchievements() = withContext(Dispatchers.IO) {
        val sessions = database.sessionDao().getAllSessions()
        val totalFocusedSeconds = sessions.sumOf { it.actualFocusedSeconds }
        val totalFocusedHours = totalFocusedSeconds / 3600f
        val streak = calculateStreak(sessions)
        val now = System.currentTimeMillis()

        val achievements = database.achievementDao().getAllAchievements().toMutableList()

        for (ach in achievements) {
            var newProgress = ach.progress
            var unlocked = ach.unlocked
            val id = ach.id

            when (id) {
                "first_session" -> {
                    newProgress = if (sessions.isNotEmpty()) 1f else 0f
                    if (newProgress >= 1f) unlocked = true
                }
                "streak_3" -> {
                    newProgress = streak.currentStreak.toFloat()
                    if (newProgress >= 3f) unlocked = true
                }
                "streak_7" -> {
                    newProgress = streak.currentStreak.toFloat()
                    if (newProgress >= 7f) unlocked = true
                }
                "streak_14" -> {
                    newProgress = streak.currentStreak.toFloat()
                    if (newProgress >= 14f) unlocked = true
                }
                "streak_30" -> {
                    newProgress = streak.currentStreak.toFloat()
                    if (newProgress >= 30f) unlocked = true
                }
                "hours_10" -> {
                    newProgress = totalFocusedHours
                    if (newProgress >= 10f) unlocked = true
                }
                "hours_50" -> {
                    newProgress = totalFocusedHours
                    if (newProgress >= 50f) unlocked = true
                }
                "hours_100" -> {
                    newProgress = totalFocusedHours
                    if (newProgress >= 100f) unlocked = true
                }
                "hours_500" -> {
                    newProgress = totalFocusedHours
                    if (newProgress >= 500f) unlocked = true
                }
                "hours_1000" -> {
                    newProgress = totalFocusedHours
                    if (newProgress >= 1000f) unlocked = true
                }
                "night_owl" -> {
                    val hasNightSession = sessions.any {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.startTime }
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        hour in 0..4
                    }
                    if (hasNightSession) {
                        newProgress = 1f
                        unlocked = true
                    }
                }
                "early_bird" -> {
                    val hasEarlySession = sessions.any {
                        val cal = Calendar.getInstance().apply { timeInMillis = it.startTime }
                        val hour = cal.get(Calendar.HOUR_OF_DAY)
                        hour in 5..7
                    }
                    if (hasEarlySession) {
                        newProgress = 1f
                        unlocked = true
                    }
                }
                "deep_work_master" -> {
                    val hasDeepWork = sessions.any {
                        it.actualFocusedSeconds >= 5400 && it.productivityRating == 5
                    }
                    if (hasDeepWork) {
                        newProgress = 1f
                        unlocked = true
                    }
                }
            }

            if (newProgress != ach.progress || unlocked != ach.unlocked) {
                database.achievementDao().updateAchievement(
                    ach.copy(
                        progress = newProgress,
                        unlocked = unlocked,
                        unlockedTimestamp = if (unlocked && ach.unlockedTimestamp == 0L) now else ach.unlockedTimestamp
                    )
                )
            }
        }
    }

    // JSON Export / Import v2 (Full App State Backup)
    suspend fun exportDataJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", "Focentra")
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())

        val sessionsArray = JSONArray()
        database.sessionDao().getAllSessions().forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("startTime", s.startTime)
            obj.put("endTime", s.endTime)
            obj.put("durationSeconds", s.durationSeconds)
            obj.put("actualFocusedSeconds", s.actualFocusedSeconds)
            obj.put("pausedSeconds", s.pausedSeconds)
            obj.put("sessionType", s.sessionType)
            obj.put("subject", s.subject)
            obj.put("topic", s.topic)
            obj.put("category", s.category)
            obj.put("goal", s.goal)
            obj.put("notes", s.notes)
            obj.put("tags", s.tags)
            obj.put("productivityRating", s.productivityRating)
            obj.put("distractionCount", s.distractionCount)
            obj.put("distractionDetails", s.distractionDetails)
            obj.put("mood", s.mood)
            obj.put("energyLevel", s.energyLevel)
            obj.put("location", s.location)
            obj.put("completionStatus", s.completionStatus)
            sessionsArray.put(obj)
        }
        root.put("sessions", sessionsArray)

        val subjectsArray = JSONArray()
        database.subjectDao().getAllSubjects().forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("colorHex", s.colorHex)
            obj.put("iconName", s.iconName)
            obj.put("targetHours", s.targetHours)
            obj.put("description", s.description)
            subjectsArray.put(obj)
        }
        root.put("subjects", subjectsArray)

        val topicsArray = JSONArray()
        database.topicDao().getAllTopics().forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("subjectId", t.subjectId)
            obj.put("subjectName", t.subjectName)
            obj.put("name", t.name)
            obj.put("description", t.description)
            topicsArray.put(obj)
        }
        root.put("topics", topicsArray)

        val chaptersArray = JSONArray()
        database.chapterDao().getAllChaptersFlow().first().forEach { chapter ->
            val obj = JSONObject()
            obj.put("id", chapter.id)
            obj.put("subjectId", chapter.subjectId)
            obj.put("subjectName", chapter.subjectName)
            obj.put("chapterName", chapter.chapterName)
            obj.put("tapCount", chapter.tapCount)
            obj.put("status", chapter.status)
            obj.put("lastStudiedTimestamp", chapter.lastStudiedTimestamp)
            obj.put("completedTimestamp", chapter.completedTimestamp)
            obj.put("totalClasses", chapter.totalClasses)
            obj.put("completedClassesCount", chapter.completedClassesCount)
            obj.put("isRevision1Completed", chapter.isRevision1Completed)
            obj.put("isRevision2Completed", chapter.isRevision2Completed)
            obj.put("orderIndex", chapter.orderIndex)
            chaptersArray.put(obj)
        }
        root.put("chapters", chaptersArray)

        val presetsArray = JSONArray()
        database.presetDao().getAllPresets().forEach { p ->
            val obj = JSONObject()
            obj.put("title", p.title)
            obj.put("type", p.type)
            obj.put("durationMinutes", p.durationMinutes)
            obj.put("subject", p.subject)
            obj.put("topic", p.topic)
            obj.put("pomodoroFocusMin", p.pomodoroFocusMin)
            obj.put("pomodoroShortBreakMin", p.pomodoroShortBreakMin)
            obj.put("pomodoroLongBreakMin", p.pomodoroLongBreakMin)
            obj.put("pomodoroRounds", p.pomodoroRounds)
            obj.put("soundEnabled", p.soundEnabled)
            obj.put("vibrateEnabled", p.vibrateEnabled)
            obj.put("keepScreenAwake", p.keepScreenAwake)
            obj.put("tags", p.tags)
            presetsArray.put(obj)
        }
        root.put("presets", presetsArray)

        val goalsArray = JSONArray()
        database.goalDao().getAllGoals().forEach { g ->
            val obj = JSONObject()
            obj.put("periodType", g.periodType)
            obj.put("targetMinutes", g.targetMinutes)
            obj.put("subjectFilter", g.subjectFilter)
            obj.put("active", g.active)
            goalsArray.put(obj)
        }
        root.put("goals", goalsArray)

        val remindersArray = JSONArray()
        database.reminderDao().getAllReminders().forEach { r ->
            val obj = JSONObject()
            obj.put("title", r.title)
            obj.put("hour", r.hour)
            obj.put("minute", r.minute)
            obj.put("daysOfWeek", r.daysOfWeek)
            obj.put("enabled", r.enabled)
            obj.put("message", r.message)
            remindersArray.put(obj)
        }
        root.put("reminders", remindersArray)

        // Include Flashcards and Exam Targets stored in settings
        val flashcardsJson = getSetting("flashcards_json", "")
        root.put("flashcards", if (flashcardsJson.isBlank()) JSONArray() else JSONArray(flashcardsJson))

        val examTargetsJson = getSetting("exam_targets_json", "")
        root.put("examTargets", if (examTargetsJson.isBlank()) JSONArray() else JSONArray(examTargetsJson))

        root.toString()
    }

    suspend fun exportDataJsonStream(outputStream: java.io.OutputStream) = withContext(Dispatchers.IO) {
        val writer = android.util.JsonWriter(outputStream.bufferedWriter(Charsets.UTF_8))
        writer.use { jWriter ->
            jWriter.beginObject()
            jWriter.name("app").value("Focentra")
            jWriter.name("version").value(2)
            jWriter.name("exportedAt").value(System.currentTimeMillis())

            jWriter.name("sessions")
            jWriter.beginArray()
            database.sessionDao().getAllSessions().forEach { s ->
                jWriter.beginObject()
                jWriter.name("id").value(s.id)
                jWriter.name("startTime").value(s.startTime)
                jWriter.name("endTime").value(s.endTime)
                jWriter.name("durationSeconds").value(s.durationSeconds)
                jWriter.name("actualFocusedSeconds").value(s.actualFocusedSeconds)
                jWriter.name("pausedSeconds").value(s.pausedSeconds)
                jWriter.name("sessionType").value(s.sessionType)
                jWriter.name("subject").value(s.subject)
                jWriter.name("topic").value(s.topic)
                jWriter.name("category").value(s.category)
                jWriter.name("goal").value(s.goal)
                jWriter.name("notes").value(s.notes)
                jWriter.name("tags").value(s.tags)
                jWriter.name("productivityRating").value(s.productivityRating)
                jWriter.name("distractionCount").value(s.distractionCount)
                jWriter.name("distractionDetails").value(s.distractionDetails)
                jWriter.name("mood").value(s.mood)
                jWriter.name("energyLevel").value(s.energyLevel)
                jWriter.name("location").value(s.location)
                jWriter.name("completionStatus").value(s.completionStatus)
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.name("subjects")
            jWriter.beginArray()
            database.subjectDao().getAllSubjects().forEach { s ->
                jWriter.beginObject()
                jWriter.name("id").value(s.id)
                jWriter.name("name").value(s.name)
                jWriter.name("colorHex").value(s.colorHex)
                jWriter.name("iconName").value(s.iconName)
                jWriter.name("targetHours").value(s.targetHours.toDouble())
                jWriter.name("description").value(s.description)
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.name("topics")
            jWriter.beginArray()
            database.topicDao().getAllTopics().forEach { t ->
                jWriter.beginObject()
                jWriter.name("id").value(t.id)
                jWriter.name("subjectId").value(t.subjectId)
                jWriter.name("subjectName").value(t.subjectName)
                jWriter.name("name").value(t.name)
                jWriter.name("description").value(t.description)
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.name("chapters")
            jWriter.beginArray()
            database.chapterDao().getAllChaptersFlow().first().forEach { chapter ->
                jWriter.beginObject()
                jWriter.name("id").value(chapter.id)
                jWriter.name("subjectId").value(chapter.subjectId)
                jWriter.name("subjectName").value(chapter.subjectName)
                jWriter.name("chapterName").value(chapter.chapterName)
                jWriter.name("tapCount").value(chapter.tapCount)
                jWriter.name("status").value(chapter.status)
                jWriter.name("lastStudiedTimestamp").value(chapter.lastStudiedTimestamp)
                jWriter.name("completedTimestamp").value(chapter.completedTimestamp)
                jWriter.name("totalClasses").value(chapter.totalClasses)
                jWriter.name("completedClassesCount").value(chapter.completedClassesCount)
                jWriter.name("isRevision1Completed").value(chapter.isRevision1Completed)
                jWriter.name("isRevision2Completed").value(chapter.isRevision2Completed)
                jWriter.name("orderIndex").value(chapter.orderIndex)
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.name("presets")
            jWriter.beginArray()
            database.presetDao().getAllPresets().forEach { p ->
                jWriter.beginObject()
                jWriter.name("title").value(p.title)
                jWriter.name("type").value(p.type)
                jWriter.name("durationMinutes").value(p.durationMinutes)
                jWriter.name("subject").value(p.subject)
                jWriter.name("topic").value(p.topic)
                jWriter.name("pomodoroFocusMin").value(p.pomodoroFocusMin)
                jWriter.name("pomodoroShortBreakMin").value(p.pomodoroShortBreakMin)
                jWriter.name("pomodoroLongBreakMin").value(p.pomodoroLongBreakMin)
                jWriter.name("pomodoroRounds").value(p.pomodoroRounds)
                jWriter.name("soundEnabled").value(p.soundEnabled)
                jWriter.name("vibrateEnabled").value(p.vibrateEnabled)
                jWriter.name("keepScreenAwake").value(p.keepScreenAwake)
                jWriter.name("tags").value(p.tags)
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.name("goals")
            jWriter.beginArray()
            database.goalDao().getAllGoals().forEach { g ->
                jWriter.beginObject()
                jWriter.name("periodType").value(g.periodType)
                jWriter.name("targetMinutes").value(g.targetMinutes)
                jWriter.name("subjectFilter").value(g.subjectFilter)
                jWriter.name("active").value(g.active)
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.name("reminders")
            jWriter.beginArray()
            database.reminderDao().getAllReminders().forEach { r ->
                jWriter.beginObject()
                jWriter.name("title").value(r.title)
                jWriter.name("hour").value(r.hour)
                jWriter.name("minute").value(r.minute)
                jWriter.name("daysOfWeek").value(r.daysOfWeek)
                jWriter.name("enabled").value(r.enabled)
                jWriter.name("message").value(r.message)
                jWriter.endObject()
            }
            jWriter.endArray()

            val flashcardsJson = getSetting("flashcards_json", "")
            val flashcards = if (flashcardsJson.isBlank()) JSONArray() else JSONArray(flashcardsJson)
            jWriter.name("flashcards")
            jWriter.writeJsonValue(flashcards)

            val examTargetsJson = getSetting("exam_targets_json", "")
            val targets = if (examTargetsJson.isBlank()) JSONArray() else JSONArray(examTargetsJson)
            jWriter.name("examTargets")
            jWriter.beginArray()
            for (index in 0 until targets.length()) {
                val target = targets.getJSONObject(index)
                jWriter.beginObject()
                jWriter.name("id").value(target.optString("id"))
                jWriter.name("title").value(target.optString("title"))
                jWriter.name("targetDateEpochMs").value(target.optLong("targetDateEpochMs"))
                jWriter.name("targetHours").value(target.optDouble("targetHours", 50.0))
                jWriter.name("subject").value(target.optString("subject", "General"))
                jWriter.name("notes").value(target.optString("notes"))
                jWriter.endObject()
            }
            jWriter.endArray()

            jWriter.endObject()
        }
    }

    suspend fun exportSessionsCsv(): String = withContext(Dispatchers.IO) {
        val sessions = database.sessionDao().getAllSessions()
        val sb = StringBuilder()
        sb.append(
            encodeCsvRecord(
                listOf(
                    "ID", "Start Time", "End Time", "Total Duration (s)", "Focused Duration (s)",
                    "Paused Duration (s)", "Type", "Subject", "Topic", "Productivity",
                    "Distractions", "Mood", "Energy", "Notes"
                )
            )
        ).append('\n')
        sessions.forEach { s ->
            sb.append(
                encodeCsvRecord(
                    listOf(
                        s.id.toString(),
                        s.startTime.toString(),
                        s.endTime.toString(),
                        s.durationSeconds.toString(),
                        s.actualFocusedSeconds.toString(),
                        s.pausedSeconds.toString(),
                        s.sessionType,
                        s.subject,
                        s.topic,
                        s.productivityRating.toString(),
                        s.distractionCount.toString(),
                        s.mood,
                        s.energyLevel,
                        s.notes
                    )
                )
            ).append('\n')
        }
        sb.toString()
    }

    suspend fun importDataJson(jsonString: String, replaceExisting: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val trimmed = jsonString.trim()
            if (trimmed.isEmpty()) return@withContext false

            val rootObject = if (trimmed.startsWith("{")) JSONObject(trimmed) else null
            val rootArray = if (trimmed.startsWith("[")) JSONArray(trimmed) else null
            if (rootObject == null && rootArray == null) return@withContext false
            if (rootObject != null) {
                val supportedArrayNames = listOf(
                    "sessions", "subjects", "topics", "presets", "goals", "reminders",
                    "flashcards", "examTargets", "chapters"
                )
                val hasSessionFields = rootObject.has("startTime") ||
                    rootObject.has("subject") || rootObject.has("durationSeconds")
                if (!hasSessionFields && supportedArrayNames.none(rootObject::has)) {
                    return@withContext false
                }
                if (supportedArrayNames.any { name ->
                        rootObject.has(name) &&
                            rootObject.optJSONArray(name) == null &&
                            !(name == "examTargets" && rootObject.opt(name) is String)
                    }
                ) {
                    return@withContext false
                }
                (supportedArrayNames - "flashcards").forEach { name ->
                    val array = rootObject.optJSONArray(name)
                        ?: if (name == "examTargets" && rootObject.opt(name) is String) {
                            JSONArray(rootObject.getString(name))
                        } else {
                            null
                        }
                    if (array != null) {
                        for (index in 0 until array.length()) {
                            array.getJSONObject(index)
                        }
                    }
                }
            }
            rootArray?.let { array ->
                for (i in 0 until array.length()) {
                    array.getJSONObject(i)
                }
            }

            database.withTransaction {
                if (replaceExisting) {
                    database.sessionDao().clearAllSessions()
                    if (rootObject?.has("subjects") == true) {
                        database.subjectDao().clearAllSubjects()
                        database.topicDao().clearAllTopics()
                        database.chapterDao().deleteAllChapters()
                    }
                    if (rootObject?.has("topics") == true) database.topicDao().clearAllTopics()
                    if (rootObject?.has("chapters") == true) database.chapterDao().deleteAllChapters()
                    if (rootObject?.has("presets") == true) database.presetDao().clearAllPresets()
                    if (rootObject?.has("goals") == true) database.goalDao().clearAllGoals()
                    if (rootObject?.has("reminders") == true) database.reminderDao().clearAllReminders()
                }

                val sessionList = mutableListOf<StudySessionEntity>()

            fun parseSessionObject(obj: JSONObject): StudySessionEntity {
                val sTime = obj.optLong("startTime", System.currentTimeMillis())
                val dur = obj.optLong("durationSeconds", obj.optLong("actualFocusedSeconds", 1500L))
                val actDur = obj.optLong("actualFocusedSeconds", dur)
                val eTime = obj.optLong("endTime", sTime + (actDur * 1000L))

                return StudySessionEntity(
                    id = if (replaceExisting) obj.optLong("id", 0L) else 0L,
                    startTime = sTime,
                    endTime = eTime,
                    durationSeconds = dur,
                    actualFocusedSeconds = actDur,
                    pausedSeconds = obj.optLong("pausedSeconds", 0L),
                    sessionType = obj.optString("sessionType", "COUNTDOWN"),
                    subject = obj.optString("subject", "General"),
                    topic = obj.optString("topic", ""),
                    category = obj.optString("category", "Study"),
                    goal = obj.optString("goal", ""),
                    notes = obj.optString("notes", ""),
                    tags = obj.optString("tags", ""),
                    productivityRating = obj.optInt("productivityRating", 4).coerceIn(1, 5),
                    distractionCount = obj.optInt("distractionCount", 0),
                    distractionDetails = obj.optString("distractionDetails", ""),
                    mood = obj.optString("mood", "GOOD"),
                    energyLevel = obj.optString("energyLevel", "HIGH"),
                    location = obj.optString("location", "Desk"),
                    completionStatus = obj.optString("completionStatus", "COMPLETED")
                )
            }

                if (trimmed.startsWith("{")) {
                val root = JSONObject(trimmed)
                val importedSubjectIds = mutableMapOf<Long, Long>()
                val existingSubjects = database.subjectDao().getAllSubjects().toMutableList()
                if (root.has("sessions")) {
                    val sessionsArray = root.getJSONArray("sessions")
                    for (i in 0 until sessionsArray.length()) {
                        val obj = sessionsArray.getJSONObject(i)
                        sessionList.add(parseSessionObject(obj))
                    }
                } else if (root.has("startTime") || root.has("subject") || root.has("durationSeconds")) {
                    // Single session object
                    sessionList.add(parseSessionObject(root))
                }

                // Restore subjects if present
                if (root.has("subjects")) {
                    val subjectsArray = root.getJSONArray("subjects")
                    for (i in 0 until subjectsArray.length()) {
                        val obj = subjectsArray.getJSONObject(i)
                        val sName = obj.optString("name", "")
                        if (sName.isNotBlank()) {
                            val existing = existingSubjects.firstOrNull {
                                it.name.equals(sName, ignoreCase = true)
                            }
                            val importedId = existing?.id ?: database.subjectDao().insertSubject(
                                SubjectEntity(
                                    name = sName,
                                    colorHex = obj.optString("colorHex", "#6366F1"),
                                    iconName = obj.optString("iconName", "School"),
                                    targetHours = obj.optDouble("targetHours", 0.0).toFloat(),
                                    description = obj.optString("description", "")
                                )
                            )
                            if (existing == null) {
                                existingSubjects.add(
                                    SubjectEntity(
                                        id = importedId,
                                        name = sName,
                                        colorHex = obj.optString("colorHex", "#6366F1"),
                                        iconName = obj.optString("iconName", "School"),
                                        targetHours = obj.optDouble("targetHours", 0.0).toFloat(),
                                        description = obj.optString("description", "")
                                    )
                                )
                            }
                            val sourceId = obj.optLong("id", 0L)
                            if (sourceId > 0L) importedSubjectIds[sourceId] = importedId
                        }
                    }
                }

                // Restore topics if present
                if (root.has("topics")) {
                    val topicsArray = root.getJSONArray("topics")
                    for (i in 0 until topicsArray.length()) {
                        val obj = topicsArray.getJSONObject(i)
                        val tName = obj.optString("name", "")
                        val sName = obj.optString("subjectName", "General")
                        if (tName.isNotBlank()) {
                            val sourceSubjectId = obj.optLong("subjectId", 0L)
                            val subjectId = importedSubjectIds[sourceSubjectId]
                                ?: existingSubjects.firstOrNull { it.name.equals(sName, ignoreCase = true) }?.id
                                ?: sourceSubjectId
                            database.topicDao().insertTopic(
                                TopicEntity(
                                    subjectId = subjectId,
                                    subjectName = sName,
                                    name = tName,
                                    description = obj.optString("description", "")
                                )
                            )
                        }
                    }
                }

                if (root.has("chapters")) {
                    val chaptersArray = root.getJSONArray("chapters")
                    val existingChapters = database.chapterDao().getAllChaptersFlow().first()
                    for (i in 0 until chaptersArray.length()) {
                        val obj = chaptersArray.getJSONObject(i)
                        val subjectName = obj.optString("subjectName", "General")
                        val sourceSubjectId = obj.optLong("subjectId", 0L)
                        val subjectId = importedSubjectIds[sourceSubjectId]
                            ?: existingSubjects.firstOrNull {
                                it.name.equals(subjectName, ignoreCase = true)
                            }?.id ?: sourceSubjectId
                        val chapterName = obj.optString("chapterName", "")
                        if (chapterName.isBlank()) continue
                        val existing = existingChapters.firstOrNull {
                            it.subjectId == subjectId && it.chapterName == chapterName
                        }
                        val chapter = ChapterEntity(
                            id = existing?.id ?: 0L,
                            subjectId = subjectId,
                            subjectName = subjectName,
                            chapterName = chapterName,
                            tapCount = obj.optInt("tapCount", 0),
                            status = obj.optString("status", "পড়া শুরু হয় নাই"),
                            lastStudiedTimestamp = obj.optLong("lastStudiedTimestamp", 0L),
                            completedTimestamp = obj.optLong("completedTimestamp", 0L),
                            totalClasses = obj.optInt("totalClasses", 10),
                            completedClassesCount = obj.optInt("completedClassesCount", 0),
                            isRevision1Completed = obj.optBoolean("isRevision1Completed", false),
                            isRevision2Completed = obj.optBoolean("isRevision2Completed", false),
                            orderIndex = obj.optInt("orderIndex", 0)
                        )
                        if (existing == null) {
                            database.chapterDao().insertChapter(chapter)
                        } else {
                            database.chapterDao().updateChapter(chapter)
                        }
                    }
                }

                // Restore presets if present
                if (root.has("presets")) {
                    val presetsArray = root.getJSONArray("presets")
                    for (i in 0 until presetsArray.length()) {
                        val obj = presetsArray.getJSONObject(i)
                        val title = obj.optString("title", "")
                        if (title.isNotBlank()) {
                            database.presetDao().insertPreset(
                                PresetEntity(
                                    title = title,
                                    type = obj.optString("type", "COUNTDOWN"),
                                    durationMinutes = obj.optInt("durationMinutes", 25),
                                    subject = obj.optString("subject", "General"),
                                    topic = obj.optString("topic", ""),
                                    pomodoroFocusMin = obj.optInt("pomodoroFocusMin", 25),
                                    pomodoroShortBreakMin = obj.optInt("pomodoroShortBreakMin", 5),
                                    pomodoroLongBreakMin = obj.optInt("pomodoroLongBreakMin", 15),
                                    pomodoroRounds = obj.optInt("pomodoroRounds", 4),
                                    soundEnabled = obj.optBoolean("soundEnabled", true),
                                    vibrateEnabled = obj.optBoolean("vibrateEnabled", true),
                                    keepScreenAwake = obj.optBoolean("keepScreenAwake", true),
                                    tags = obj.optString("tags", "")
                                )
                            )
                        }
                    }
                }

                // Restore goals if present
                if (root.has("goals")) {
                    val goalsArray = root.getJSONArray("goals")
                    for (i in 0 until goalsArray.length()) {
                        val obj = goalsArray.getJSONObject(i)
                        val periodType = obj.optString("periodType", "")
                        if (periodType.isNotBlank()) {
                            database.goalDao().insertGoal(
                                StudyGoalEntity(
                                    periodType = periodType,
                                    targetMinutes = obj.optInt("targetMinutes", 480),
                                    subjectFilter = obj.optString("subjectFilter", ""),
                                    active = obj.optBoolean("active", true)
                                )
                            )
                        }
                    }
                }

                // Restore reminders if present
                if (root.has("reminders")) {
                    val remindersArray = root.getJSONArray("reminders")
                    for (i in 0 until remindersArray.length()) {
                        val obj = remindersArray.getJSONObject(i)
                        val title = obj.optString("title", "")
                        if (title.isNotBlank()) {
                            database.reminderDao().insertReminder(
                                ReminderEntity(
                                    title = title,
                                    hour = obj.optInt("hour", 9),
                                    minute = obj.optInt("minute", 0),
                                    daysOfWeek = obj.optString("daysOfWeek", "MON,TUE,WED,THU,FRI,SAT,SUN"),
                                    enabled = obj.optBoolean("enabled", true),
                                    message = obj.optString("message", "Time for focus study session")
                                )
                            )
                        }
                    }
                }

                // Restore flashcards if present
                if (root.has("flashcards")) {
                    database.settingDao().setSetting(
                        com.niloy.focentra.data.local.entity.SettingEntity("flashcards_json", root.getJSONArray("flashcards").toString())
                    )
                }

                // Restore exam targets if present
                if (root.has("examTargets")) {
                    val examTargets = root.optJSONArray("examTargets")
                        ?: JSONArray(root.getString("examTargets"))
                    database.settingDao().setSetting(
                        com.niloy.focentra.data.local.entity.SettingEntity("exam_targets_json", examTargets.toString())
                    )
                }

                } else if (trimmed.startsWith("[")) {
                // Array of sessions
                val sessionsArray = JSONArray(trimmed)
                for (i in 0 until sessionsArray.length()) {
                    val obj = sessionsArray.getJSONObject(i)
                    sessionList.add(parseSessionObject(obj))
                }
                } else {
                    Unit
                }

                if (sessionList.isNotEmpty()) {
                    sessionList.chunked(500).forEach { chunk ->
                        database.sessionDao().insertSessions(chunk)
                    }
                }
            }
            checkAndUnlockAchievements()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importDataJsonStream(inputStream: java.io.InputStream, replaceExisting: Boolean): Boolean = withContext(Dispatchers.IO) {
        val content = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        importDataJson(content, replaceExisting)
    }

    suspend fun importSessionsCsvStream(
        inputStream: java.io.InputStream,
        replaceExisting: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val records = inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                parseCsvRecords(reader.readText())
            }
            if (records.isEmpty()) return@withContext false

            val sessionList = mutableListOf<StudySessionEntity>()
            val dataRecords = if (records.first().any {
                    it.contains("Start Time", ignoreCase = true) || it.contains("Subject", ignoreCase = true)
                }
            ) {
                records.drop(1)
            } else {
                records
            }

            for (parts in dataRecords) {
                if (parts.all(String::isBlank)) continue
                if (parts.size >= 8) {
                    // Standard columns: ID,Start Time,End Time,Total Duration (s),Focused Duration (s),Paused Duration (s),Type,Subject,Topic,Productivity,Distractions,Mood,Energy,Notes
                    val sTime = parts.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis()
                    val eTime = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
                    val totalDur = parts.getOrNull(3)?.toLongOrNull() ?: 1500L
                    val focusedDur = parts.getOrNull(4)?.toLongOrNull() ?: totalDur
                    val pausedDur = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                    val sType = parts.getOrNull(6)?.ifBlank { "COUNTDOWN" } ?: "COUNTDOWN"
                    val subj = parts.getOrNull(7)?.ifBlank { "General" } ?: "General"
                    val top = parts.getOrNull(8) ?: ""
                    val prod = parts.getOrNull(9)?.toIntOrNull()?.coerceIn(1, 5) ?: 4
                    val distCount = parts.getOrNull(10)?.toIntOrNull() ?: 0
                    val md = parts.getOrNull(11) ?: "GOOD"
                    val energy = parts.getOrNull(12) ?: "HIGH"
                    val nts = parts.getOrNull(13) ?: ""

                    sessionList.add(
                        StudySessionEntity(
                            startTime = sTime,
                            endTime = eTime,
                            durationSeconds = totalDur,
                            actualFocusedSeconds = focusedDur,
                            pausedSeconds = pausedDur,
                            sessionType = sType,
                            subject = subj,
                            topic = top,
                            category = "Study",
                            goal = "",
                            notes = nts,
                            tags = "",
                            productivityRating = prod,
                            distractionCount = distCount,
                            distractionDetails = "",
                            mood = md,
                            energyLevel = energy,
                            location = "Desk",
                            completionStatus = "COMPLETED"
                        )
                    )

                }
            }

            if (sessionList.isEmpty()) return@withContext false
            database.withTransaction {
                if (replaceExisting) database.sessionDao().clearAllSessions()
                sessionList.chunked(500).forEach { chunk ->
                    database.sessionDao().insertSessions(chunk)
                }
            }
            checkAndUnlockAchievements()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}

data class StreakInfo(
    val currentStreak: Int,
    val longestStreak: Int,
    val isStudiedToday: Boolean
)

fun calculateStreak(sessions: List<StudySessionEntity>): StreakInfo {
    if (sessions.isEmpty()) return StreakInfo(0, 0, false)

    // Group sessions by local date string YYYY-MM-DD
    val datesWithStudy = mutableSetOf<String>()
    val cal = Calendar.getInstance()

    sessions.forEach {
        cal.timeInMillis = it.startTime
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val dateKey = String.format("%04d-%02d-%02d", y, m, d)
        datesWithStudy.add(dateKey)
    }

    val todayCal = Calendar.getInstance()
    val todayKey = String.format("%04d-%02d-%02d", todayCal.get(Calendar.YEAR), todayCal.get(Calendar.MONTH) + 1, todayCal.get(Calendar.DAY_OF_MONTH))

    val isStudiedToday = datesWithStudy.contains(todayKey)

    // Check consecutive days starting from today or yesterday
    var currentStreak = 0
    val checkCal = Calendar.getInstance()
    if (!isStudiedToday) {
        checkCal.add(Calendar.DAY_OF_YEAR, -1)
    }

    while (true) {
        val k = String.format("%04d-%02d-%02d", checkCal.get(Calendar.YEAR), checkCal.get(Calendar.MONTH) + 1, checkCal.get(Calendar.DAY_OF_MONTH))
        if (datesWithStudy.contains(k)) {
            currentStreak++
            checkCal.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            break
        }
    }

    // Calculate longest streak by sorting unique dates
    val sortedDates = datesWithStudy.sorted()
    var longestStreak = 0
    var tempStreak = 0
    var previousDate: String? = null

    for (dStr in sortedDates) {
        val previous = previousDate
        if (previous == null) {
            tempStreak = 1
        } else {
            val parts = previous.split("-").map { it.toInt() }
            val nextDate = Calendar.getInstance().apply {
                clear()
                set(parts[0], parts[1] - 1, parts[2])
                add(Calendar.DAY_OF_YEAR, 1)
            }
            val expectedDate = String.format(
                "%04d-%02d-%02d",
                nextDate.get(Calendar.YEAR),
                nextDate.get(Calendar.MONTH) + 1,
                nextDate.get(Calendar.DAY_OF_MONTH)
            )
            if (dStr == expectedDate) {
                tempStreak++
            } else {
                tempStreak = 1
            }
        }
        if (tempStreak > longestStreak) longestStreak = tempStreak
        previousDate = dStr
    }

    if (currentStreak > longestStreak) longestStreak = currentStreak

    return StreakInfo(currentStreak, longestStreak, isStudiedToday)
}
