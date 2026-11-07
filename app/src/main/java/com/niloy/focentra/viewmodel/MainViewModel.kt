package com.niloy.focentra.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.niloy.focentra.FocentraApp
import com.niloy.focentra.data.local.entity.*
import com.niloy.focentra.data.repository.calculateStreak
import com.niloy.focentra.engine.*
import com.niloy.focentra.service.StudyReminderNotifications
import com.niloy.focentra.service.StudyReminderScheduler
import com.niloy.focentra.service.revisionDueAt
import com.niloy.focentra.service.revisionStages
import com.niloy.focentra.service.revisionStageId
import com.niloy.focentra.service.calculateSequentialRevisionStages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class NotificationType {
    REVISION_DUE,
    DAILY_TARGET,
    RECAP_REMINDER,
    INACTIVITY_WARNING,
    GOAL_WARNING,
    MOTIVATION
}

data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val type: NotificationType,
    val title: String,
    val message: String,
    val subjectName: String = "",
    val chapterName: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class NavigationTab {
    DASHBOARD,
    TIMER,
    ANALYTICS,
    CALENDAR,
    HISTORY,
    SUBJECTS,
    ACHIEVEMENTS,
    PRESETS,
    SETTINGS,
    NOTIFICATIONS,
    REVISION_HUB
}

data class BrainDumpNote(
    val id: String = UUID.randomUUID().toString(),
    val note: String,
    val timestamp: Long = System.currentTimeMillis(),
    val subject: String = "General",
    val isDone: Boolean = false
)

data class ExamTarget(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val targetDateEpochMs: Long,
    val targetHours: Float = 50f,
    val subject: String = "General",
    val notes: String = "",
    val chapterIds: List<Long> = emptyList()
)

data class RevisionRecord(
    val id: String = UUID.randomUUID().toString(),
    val subjectName: String,
    val chapterName: String,
    val durationMinutes: Int,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val revisionStage: String = "Revision",
    val durationSeconds: Long = durationMinutes * 60L
)

data class DashboardWidgetOrder(
    val id: String,
    val title: String,
    val isVisible: Boolean = true
)

private fun toBanglaDigits(input: String): String {
    val map = mapOf(
        '0' to '০', '1' to '১', '2' to '২', '3' to '৩', '4' to '৪',
        '5' to '৫', '6' to '৬', '7' to '৭', '8' to '৮', '9' to '৯'
    )
    return input.map { map[it] ?: it }.joinToString("")
}

internal fun isRevisionTopic(topic: String): Boolean =
    topic.contains("revision", ignoreCase = true)

internal fun revisionCompletionKey(session: StudySessionEntity): String =
    "${session.startTime}:${session.endTime}:${session.subject}:${session.topic}"

internal fun chapterAfterRevision(chapter: ChapterEntity, revisedAt: Long): ChapterEntity {
    val isComplete = chapter.tapCount >= 3 || chapter.status == "অধ্যায় Completely শেষ"
    val originalCompletionTime = chapter.completedTimestamp.takeIf { it > 0L }
        ?: chapter.lastStudiedTimestamp.takeIf { isComplete }
        ?: chapter.completedTimestamp
    return chapter.copy(
        lastStudiedTimestamp = revisedAt,
        completedTimestamp = originalCompletionTime
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocentraApp
    val repository = app.repository
    val timerEngine = app.timerEngine

    // Active Navigation Screen
    private val _currentTab = MutableStateFlow(NavigationTab.DASHBOARD)
    val currentTab: StateFlow<NavigationTab> = _currentTab.asStateFlow()

    // Seen Notification IDs & Persistent Posting Track & Dismissed IDs
    private val _seenNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val seenNotificationIds: StateFlow<Set<String>> = _seenNotificationIds.asStateFlow()

    private val _dismissedNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val dismissedNotificationIds: StateFlow<Set<String>> = _dismissedNotificationIds.asStateFlow()

    private val _postedNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    private val _notificationStateLoaded = MutableStateFlow(false)
    val notificationStateLoaded: StateFlow<Boolean> = _notificationStateLoaded.asStateFlow()
    private val notificationPersistenceMutex = Mutex()
    fun markNotificationsAsSeen(notificationIds: Set<String>) {
        if (notificationIds.isEmpty()) return
        val updated = _seenNotificationIds.value + notificationIds
        _seenNotificationIds.value = updated
        persistNotificationIds("seen_notification_ids", updated)
    }

    fun dismissNotification(id: String) {
        val updated = _dismissedNotificationIds.value + id
        _dismissedNotificationIds.value = updated
        persistNotificationIds("dismissed_notification_ids", updated)
    }

    fun acknowledgeSystemNotification(id: String, wasPosted: Boolean) {
        if (wasPosted) {
            val updated = _postedNotificationIds.value + id
            _postedNotificationIds.value = updated
            persistNotificationIds("posted_notification_ids", updated)
        }
    }

    fun retrySystemNotifications() {
        StudyReminderScheduler.scheduleAll(getApplication(), reminders.value, chapters.value)
    }

    private fun persistNotificationIds(key: String, ids: Set<String>) {
        viewModelScope.launch {
            notificationPersistenceMutex.withLock {
                try {
                    val latestIds = when (key) {
                        "seen_notification_ids" -> _seenNotificationIds.value
                        "dismissed_notification_ids" -> _dismissedNotificationIds.value
                        else -> _postedNotificationIds.value
                    }
                    repository.setSetting(key, latestIds.joinToString(","))
                } catch (exception: Exception) {
                    Log.e("FocentraNotifications", "Failed to persist notification state for $key.", exception)
                }
            }
        }
    }

    // Full screen focus mode state
    private val _isFullScreenFocus = MutableStateFlow(false)
    val isFullScreenFocus: StateFlow<Boolean> = _isFullScreenFocus.asStateFlow()

    // Selected Statistics Period
    private val _selectedPeriod = MutableStateFlow(TimePeriod.THIS_WEEK)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    // Selected Calendar Date (epoch ms)
    private val _selectedCalendarDate = MutableStateFlow(System.currentTimeMillis())
    val selectedCalendarDate: StateFlow<Long> = _selectedCalendarDate.asStateFlow()

    // History Search & Filter State
    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    private val _historySubjectFilter = MutableStateFlow<String?>(null)
    val historySubjectFilter: StateFlow<String?> = _historySubjectFilter.asStateFlow()

    private val _historyMinProductivity = MutableStateFlow(0)
    val historyMinProductivity: StateFlow<Int> = _historyMinProductivity.asStateFlow()

    // Theme & Settings State (Initialized synchronously to prevent white flash)
    private val _currentTheme = MutableStateFlow(app.getInitialTheme())
    val currentTheme: StateFlow<String> = _currentTheme.asStateFlow()

    private val _language = MutableStateFlow(app.getInitialLanguage())
    val language: StateFlow<String> = _language.asStateFlow()

    // Daynexa Integration State
    private val _isDaynexaConnected = MutableStateFlow(false)
    val isDaynexaConnected: StateFlow<Boolean> = _isDaynexaConnected.asStateFlow()

    private val _daynexaConnectedAt = MutableStateFlow(0L)
    val daynexaConnectedAt: StateFlow<Long> = _daynexaConnectedAt.asStateFlow()

    // Snackbar event channel
    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    // Last deleted session for undo
    private var lastDeletedSession: StudySessionEntity? = null

    // Room DB streams
    val sessions = repository.allSessions.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val subjects = repository.allSubjects.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val topics = repository.allTopics.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val goals = repository.allGoals.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val presets = repository.allPresets.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val achievements = repository.allAchievements.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val reminders = repository.allReminders.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val settings = repository.allSettings.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dailyTasks = repository.allTasks.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val chapters = repository.allChapters.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbNotifications = repository.allNotifications.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val dbRevisionSkips = repository.allRevisionSkips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addChapter(subjectId: Long, subjectName: String, chapterName: String) {
        if (chapterName.isBlank()) return
        viewModelScope.launch {
            repository.insertChapter(
                ChapterEntity(
                    subjectId = subjectId,
                    subjectName = subjectName,
                    chapterName = chapterName.trim(),
                    tapCount = 0,
                    status = "পড়া শুরু হয় নাই"
                )
            )
        }
    }

    fun handleChapterTap(chapter: ChapterEntity) {
        val now = System.currentTimeMillis()
        val currentlyComplete = chapter.tapCount >= 3 || chapter.status == "অধ্যায় Completely শেষ"
        val newTapCount = if (currentlyComplete) 0 else chapter.tapCount.coerceIn(0, 2) + 1
        val newStatus = when (newTapCount) {
            3 -> "অধ্যায় Completely শেষ"
            2 -> "পড়া শেষ"
            1 -> "পড়া শুরু হয়েছে"
            else -> "পড়া শুরু হয় নাই"
        }
        val compTime = if (newTapCount == 3) now else 0L
        viewModelScope.launch {
            val updated = chapter.copy(tapCount = newTapCount, status = newStatus, lastStudiedTimestamp = now, completedTimestamp = compTime)
            repository.updateChapter(updated)
            StudyReminderScheduler.scheduleChapterRevisions(getApplication(), updated)
        }
    }

    fun updateChapterStatus(chapter: ChapterEntity, newStatus: String) {
        val now = System.currentTimeMillis()
        val newTapCount = when (newStatus) {
            "পড়া শুরু হয় নাই" -> 0
            "পড়া শুরু হয়েছে" -> 1
            "পড়া চলছে", "পড়া শেষ পর্যায়ে", "পড়া শেষ", "রিভিশন চলছে" -> 2
            "অধ্যায় Completely শেষ" -> 3
            else -> chapter.tapCount.coerceIn(0, 3)
        }
        val wasComplete = chapter.tapCount >= 3 || chapter.status == "অধ্যায় Completely শেষ"
        val compTime = if (newTapCount == 3) {
            if (wasComplete && chapter.completedTimestamp > 0L) chapter.completedTimestamp else now
        } else {
            0L
        }
        viewModelScope.launch {
            val updated = chapter.copy(status = newStatus, tapCount = newTapCount, lastStudiedTimestamp = now, completedTimestamp = compTime)
            repository.updateChapter(updated)
            StudyReminderScheduler.scheduleChapterRevisions(getApplication(), updated)
        }
    }

    fun updateChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            repository.updateChapter(chapter)
        }
    }

    fun addHistoricalChapterStudyTime(subjectName: String, chapterName: String, durationMinutes: Int) {
        if (durationMinutes <= 0) return
        viewModelScope.launch {
            val chapter = chapters.value.firstOrNull {
                it.subjectName.equals(subjectName, ignoreCase = true) &&
                    it.chapterName.equals(chapterName, ignoreCase = true)
            }
            if (chapter == null) {
                _snackbarMessage.emit("Chapter not found; no study time was added.")
                return@launch
            }
            val addedSeconds = durationMinutes.toLong() * 60L
            repository.updateChapter(
                chapter.copy(historicalStudySeconds = chapter.historicalStudySeconds + addedSeconds)
            )
            _snackbarMessage.emit("Earlier study time added to the subject goal.")
        }
    }

    fun deleteChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            repository.deleteChapter(chapter)
        }
    }

    // Streak Saver Pass Used Month (Stored as YYYY-MM)
    private val _streakPassUsedMonth = MutableStateFlow("")
    val streakPassUsedMonth: StateFlow<String> = _streakPassUsedMonth.asStateFlow()

    fun addTask(title: String, subject: String = "General") {
        if (title.isBlank()) return
        viewModelScope.launch {
            repository.insertTask(StudyTaskEntity(title = title.trim(), subject = subject))
        }
    }

    fun toggleTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun clearCompletedTasks() {
        viewModelScope.launch {
            repository.clearCompletedTasks()
        }
    }

    fun useStreakSaverPass() {
        val cal = Calendar.getInstance()
        val currentMonthKey = String.format("%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        if (_streakPassUsedMonth.value == currentMonthKey) return

        viewModelScope.launch {
            _streakPassUsedMonth.value = currentMonthKey
            repository.setSetting("streak_pass_month", currentMonthKey)
            _snackbarMessage.emit("Streak Saver Pass activated for this month!")
        }
    }

    // Today's focused minutes
    private val notificationClock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    val todayFocusedMinutes = combine(sessions, notificationClock) { list, now ->
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        list.filter { it.startTime in startOfToday..now }.sumOf { it.actualFocusedSeconds } / 60
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0L)

    // Revision History Log Feature
    private val _revisionHistory = MutableStateFlow<List<RevisionRecord>>(emptyList())
    val revisionHistory: StateFlow<List<RevisionRecord>> = _revisionHistory.asStateFlow()

    // Derived states optimized on Dispatchers.Default background threads
    val activeNotifications = combine(chapters, _revisionHistory) { chapterList, history ->
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val list = mutableListOf<AppNotification>()
            val now = System.currentTimeMillis()
            chapterList.forEach { ch ->
                val isCompleted = ch.tapCount >= 3 || ch.status == "অধ্যায় Completely শেষ"
                if (!isCompleted) return@forEach
                val completedAt = ch.completedTimestamp.takeIf { it > 0L } ?: ch.lastStudiedTimestamp
                if (completedAt <= 0L) return@forEach

                val chapterHistory = history.filter {
                    it.subjectName.equals(ch.subjectName, ignoreCase = true) &&
                        it.chapterName.equals(ch.chapterName, ignoreCase = true)
                }
                val stageInfos = calculateSequentialRevisionStages(completedAt, chapterHistory)
                stageInfos.forEach { info ->
                    // Only active (not upcoming and not completed) stages with an reached due date generate due notifications
                    if (!info.isCompleted && !info.isUpcoming && info.dueDateMs != null && info.dueDateMs <= now) {
                        val notifId = "rev_${ch.id}_${info.stage.id}"
                        list.add(
                            AppNotification(
                                id = notifId,
                                type = NotificationType.REVISION_DUE,
                                title = info.stage.title,
                                message = "সময় হয়েছে «${ch.chapterName}» (${ch.subjectName}) অধ্যায়টি অন্তত ১ ঘণ্টা রিভিশন করার।",
                                subjectName = ch.subjectName,
                                chapterName = ch.chapterName,
                                timestamp = info.dueDateMs
                            )
                        )
                    }
                }
            }

            list.toList()
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val unseenNotificationCount = combine(activeNotifications, _seenNotificationIds, _notificationStateLoaded) { active, seen, isLoaded ->
        if (isLoaded) active.count { it.id !in seen } else 0
    }.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val streakInfo = sessions.map { sessionList ->
        kotlinx.coroutines.withContext(Dispatchers.Default) { calculateStreak(sessionList) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, calculateStreak(emptyList()))

    val personalRecords = sessions.map { sessionList ->
        kotlinx.coroutines.withContext(Dispatchers.Default) { StatisticsEngine.calculatePersonalRecords(sessionList) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, PersonalRecords())

    val heatmapData = sessions.map { sessionList ->
        kotlinx.coroutines.withContext(Dispatchers.Default) { StatisticsEngine.buildHeatmapData(sessionList, weeksBack = 16) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val periodStatistics = combine(sessions, _selectedPeriod, goals) { sessionList, period, goalList ->
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val dailyGoal = goalList.find { it.periodType == "DAILY" }?.targetMinutes ?: 480
            StatisticsEngine.calculatePeriodStats(sessionList, period, dailyGoal)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, PeriodStatistics(TimePeriod.THIS_WEEK))

    // Filtered History list optimized on Dispatchers.Default
    val filteredHistory = combine(sessions, _historySearchQuery, _historySubjectFilter, _historyMinProductivity) { list, query, subFilter, minRating ->
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            list.filter { s ->
                val matchesQuery = query.isBlank() ||
                        s.subject.contains(query, ignoreCase = true) ||
                        s.topic.contains(query, ignoreCase = true) ||
                        s.notes.contains(query, ignoreCase = true) ||
                        s.tags.contains(query, ignoreCase = true)

                val matchesSubject = subFilter == null || s.subject.equals(subFilter, ignoreCase = true)
                val matchesRating = minRating == 0 || s.productivityRating >= minRating
                matchesQuery && matchesSubject && matchesRating
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Exam / Target Countdown Planner Feature
    private val _examTargets = MutableStateFlow<List<ExamTarget>>(emptyList())
    val examTargets: StateFlow<List<ExamTarget>> = _examTargets.asStateFlow()

    fun recordRevisionSkip(
        subjectName: String,
        chapterName: String,
        reason: String,
        scheduledRecapTimestamp: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val currentSkips = app.database.revisionSkipDao()
                .getSkipCountForChapter(subjectName, chapterName)
            if (currentSkips >= 5) {
                onResult(false, "Maximum 5 dismissals reached for «$chapterName». Complete 10 hours of study for this chapter before starting other subjects.")
                return@launch
            }
            if (scheduledRecapTimestamp <= now || scheduledRecapTimestamp > now + 4L * 24L * 60L * 60L * 1000L) {
                onResult(false, "Choose a reminder time within the next 4 days.")
                return@launch
            }
            if (reason.isBlank()) {
                onResult(false, "Please enter the reason for dismissing this revision.")
                return@launch
            }

            val skipEntity = RevisionSkipEntity(
                id = UUID.randomUUID().toString(),
                subjectName = subjectName,
                chapterName = chapterName,
                reason = reason.trim(),
                skippedAtTimestamp = now,
                scheduledRecapTimestamp = scheduledRecapTimestamp
            )
            repository.insertRevisionSkip(skipEntity)

            StudyReminderNotifications.scheduleRecapReminders(
                getApplication(),
                subjectName,
                chapterName,
                scheduledRecapTimestamp,
                now
            )

            val recapNotif = NotificationEntity(
                id = "recap_scheduled_${skipEntity.id}",
                type = NotificationType.RECAP_REMINDER.name,
                title = "Revision Recap Scheduled",
                message = "Revision for «$chapterName» ($subjectName) skipped and scheduled for recap.",
                subjectName = subjectName,
                chapterName = chapterName,
                timestamp = System.currentTimeMillis()
            )
            repository.insertNotification(recapNotif)

            _snackbarMessage.emit("Revision skipped. Recap scheduled successfully!")
            onResult(true, "Skip recorded successfully.")
        }
    }

    fun isTimerBlockedFor(targetSubject: String, targetChapter: String): Pair<Boolean, String> {
        for (item in mandatoryRevisionNotifications()) {
            val requirement = revisionRequirement(item) ?: continue
            val isTargetMatching = targetSubject.trim().equals(item.subjectName.trim(), ignoreCase = true) &&
                targetChapter.trim().equals(item.chapterName.trim(), ignoreCase = true)
            if (!isTargetMatching) {
                val hoursText = if (requirement.first == 3600L) "1 hour of revision" else "10 hours of study"
                return Pair(
                    true,
                    "Complete $hoursText for «${item.chapterName}» in ${item.subjectName} before starting a timer for another subject."
                )
            }
        }
        return Pair(false, "")
    }

    fun pendingMandatoryRevision(): AppNotification? =
        mandatoryRevisionNotifications().firstOrNull { revisionRequirement(it) != null }

    private fun mandatoryRevisionNotifications(): List<AppNotification> {
        val dueRevisions = activeNotifications.value.filter { it.type == NotificationType.REVISION_DUE }
        val lockNotifications = dbRevisionSkips.value
            .groupBy { "${it.subjectName.lowercase(Locale.ROOT)}|${it.chapterName.lowercase(Locale.ROOT)}" }
            .values
            .mapNotNull { skips ->
                val latest = skips.maxByOrNull { it.skippedAtTimestamp } ?: return@mapNotNull null
                if (skips.size < 5 || latest.scheduledRecapTimestamp > System.currentTimeMillis()) return@mapNotNull null
                val existingDue = dueRevisions.any {
                    it.subjectName.equals(latest.subjectName, ignoreCase = true) &&
                        it.chapterName.equals(latest.chapterName, ignoreCase = true)
                }
                if (existingDue) return@mapNotNull null
                AppNotification(
                    id = "revision_lock_${latest.subjectName.hashCode()}_${latest.chapterName.hashCode()}",
                    type = NotificationType.REVISION_DUE,
                    title = "10-hour chapter study required",
                    message = "Study «${latest.chapterName}» for 10 hours before starting another subject.",
                    subjectName = latest.subjectName,
                    chapterName = latest.chapterName,
                    timestamp = latest.scheduledRecapTimestamp
                )
            }
        return dueRevisions + lockNotifications
    }

    private fun revisionRequirement(notification: AppNotification): Pair<Long, Long>? {
        val matchingSkips = dbRevisionSkips.value.filter {
            it.subjectName.equals(notification.subjectName, ignoreCase = true) &&
                it.chapterName.equals(notification.chapterName, ignoreCase = true)
        }
        val skipCount = matchingSkips.size
        val lastSkip = matchingSkips.maxByOrNull { it.skippedAtTimestamp }
        if (skipCount in 1..5 && lastSkip != null && lastSkip.scheduledRecapTimestamp > System.currentTimeMillis()) {
            return null
        }

        val requiredSeconds = if (skipCount >= 5) 10L * 60L * 60L else 60L * 60L
        val gateStart = if (skipCount >= 5 && lastSkip != null) {
            lastSkip.scheduledRecapTimestamp
        } else {
            notification.timestamp
        }
        val completedSeconds = if (skipCount >= 5) {
            sessions.value.filter { session ->
                session.subject.equals(notification.subjectName, ignoreCase = true) &&
                    session.selectedChapter.equals(notification.chapterName, ignoreCase = true) &&
                    session.startTime >= gateStart
            }.sumOf { it.actualFocusedSeconds }
        } else {
            revisionHistory.value.filter {
                it.subjectName.equals(notification.subjectName, ignoreCase = true) &&
                    it.chapterName.equals(notification.chapterName, ignoreCase = true) &&
                    it.startTime >= gateStart
            }.sumOf { it.durationSeconds }
        }
        return if (completedSeconds < requiredSeconds) requiredSeconds to gateStart else null
    }

    fun recordRevision(
        subjectName: String,
        chapterName: String,
        durationMinutes: Int,
        startTime: Long = System.currentTimeMillis() - (durationMinutes.coerceAtLeast(1) * 60 * 1000L),
        endTime: Long = System.currentTimeMillis(),
        stage: String = "Revision",
        durationSeconds: Long = durationMinutes.coerceAtLeast(0) * 60L
    ) {
        val newRecord = RevisionRecord(
            subjectName = subjectName.ifBlank { "General" },
            chapterName = chapterName.ifBlank { "General Revision" },
            durationMinutes = (durationSeconds / 60L).toInt().coerceAtLeast(1),
            startTime = startTime,
            endTime = endTime,
            timestamp = endTime,
            revisionStage = stage.ifBlank { "Revision" },
            durationSeconds = durationSeconds.coerceAtLeast(0L)
        )
        val existing = _revisionHistory.value.firstOrNull()
        if (existing != null &&
            existing.subjectName == newRecord.subjectName &&
            existing.chapterName == newRecord.chapterName &&
            (System.currentTimeMillis() - existing.endTime) < 2000L) {
            return
        }

        _revisionHistory.value = listOf(newRecord) + _revisionHistory.value
        persistRevisionHistory()

        val matchingChapter = chapters.value.find {
            it.subjectName.equals(subjectName, ignoreCase = true) &&
            it.chapterName.equals(chapterName, ignoreCase = true)
        }
        if (matchingChapter != null) {
            val now = System.currentTimeMillis()
            updateChapter(chapterAfterRevision(matchingChapter, now))
        }
    }

    fun deleteRevisionRecord(id: String) {
        _revisionHistory.value = _revisionHistory.value.filter { it.id != id }
        persistRevisionHistory()
    }

    init {
        viewModelScope.launch {
            repository.ensureBanglaSyllabusSeeded()

            val themeSetting = repository.getSetting("theme", app.getInitialTheme())
            _currentTheme.value = themeSetting
            app.saveCachedTheme(themeSetting)

            val langSetting = repository.getSetting("language", app.getInitialLanguage())
            _language.value = langSetting
            app.saveCachedLanguage(langSetting)

            val daynexaEnabled = repository.getSetting("daynexa_integration_enabled", "false")
            _isDaynexaConnected.value = daynexaEnabled.equals("true", ignoreCase = true)

            val daynexaTime = repository.getSetting("daynexa_connected_timestamp", "0")
            _daynexaConnectedAt.value = daynexaTime.toLongOrNull() ?: 0L

            val streakPass = repository.getSetting("streak_pass_month", "")
            _streakPassUsedMonth.value = streakPass

            loadPersistentUserData()
        }

        viewModelScope.launch {
            timerEngine.savedRevisionSessions.collect { session ->
                val chapterName = session.selectedChapter.ifBlank { session.topic }
                recordRevision(
                    subjectName = session.subject.ifBlank { "General" },
                    chapterName = chapterName,
                    durationMinutes = (session.actualFocusedSeconds / 60L).toInt(),
                    startTime = session.startTime,
                    endTime = session.endTime,
                    stage = session.topic.ifBlank { "Revision" },
                    durationSeconds = session.actualFocusedSeconds
                )
            }
        }

        viewModelScope.launch {
            activeNotifications.collect { notifications ->
                notifications.forEach { notif ->
                    repository.insertNotification(
                        NotificationEntity(
                            id = notif.id,
                            type = notif.type.name,
                            title = notif.title,
                            message = notif.message,
                            subjectName = notif.subjectName,
                            chapterName = notif.chapterName,
                            timestamp = notif.timestamp
                        )
                    )
                }
            }
        }
    }

    private suspend fun loadPersistentUserData() {
        try {
            // Load Brain Dumps
            val bdJson = repository.getSetting("brain_dumps_json", "")
            if (bdJson.isNotBlank()) {
                val array = org.json.JSONArray(bdJson)
                val list = mutableListOf<BrainDumpNote>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        BrainDumpNote(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            note = obj.optString("note", ""),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            subject = obj.optString("subject", "General"),
                            isDone = obj.optBoolean("isDone", false)
                        )
                    )
                }
                _brainDumpNotes.value = list
            }


            // Load Exam Targets
            val etJson = repository.getSetting("exam_targets_json", "")
            if (etJson.isNotBlank()) {
                val array = org.json.JSONArray(etJson)
                val list = mutableListOf<ExamTarget>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        ExamTarget(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            title = obj.optString("title", ""),
                            targetDateEpochMs = obj.optLong("targetDateEpochMs", System.currentTimeMillis() + 86400000L * 30),
                            targetHours = obj.optDouble("targetHours", 50.0).toFloat(),
                            subject = obj.optString("subject", "General"),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
                _examTargets.value = list
            }

            // Load Seen, Dismissed, and Posted Notification IDs
            val seenStr = repository.getSetting("seen_notification_ids", "")
            if (seenStr.isNotBlank()) {
                _seenNotificationIds.value = seenStr.split(",").filter { it.isNotBlank() }.toSet()
            }
            val dismissedStr = repository.getSetting("dismissed_notification_ids", "")
            if (dismissedStr.isNotBlank()) {
                _dismissedNotificationIds.value = dismissedStr.split(",").filter { it.isNotBlank() }.toSet()
            }
            val postedStr = repository.getSetting("posted_notification_ids", "")
            if (postedStr.isNotBlank()) {
                _postedNotificationIds.value = postedStr.split(",").filter { it.isNotBlank() }.toSet()
            }
            _notificationStateLoaded.value = true

            // Load Revision History
            val rhJson = repository.getSetting("revision_history_json", "")
            if (rhJson.isNotBlank()) {
                val array = org.json.JSONArray(rhJson)
                val list = mutableListOf<RevisionRecord>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val st = obj.optLong("startTime", 0L)
                    val et = obj.optLong("endTime", 0L)
                    val ts = obj.optLong("timestamp", System.currentTimeMillis())
                    list.add(
                        RevisionRecord(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            subjectName = obj.optString("subjectName", ""),
                            chapterName = obj.optString("chapterName", ""),
                            durationMinutes = obj.optInt("durationMinutes", 30),
                            startTime = if (st > 0) st else ts,
                            endTime = if (et > 0) et else ts,
                            timestamp = ts,
                            revisionStage = obj.optString("revisionStage", "Revision"),
                            durationSeconds = obj.optLong(
                                "durationSeconds",
                                obj.optInt("durationMinutes", 30) * 60L
                            )
                        )
                    )
                }
                _revisionHistory.value = list
            }

            val savedRevisionSessions = repository.getAllSessionsList().filter {
                isRevisionTopic(it.topic) && it.selectedChapter.isNotBlank()
            }
            val missingRevisionRecords = savedRevisionSessions
                .filterNot { session ->
                    _revisionHistory.value.any {
                        it.subjectName.equals(session.subject, ignoreCase = true) &&
                            it.chapterName.equals(session.selectedChapter, ignoreCase = true) &&
                            it.startTime == session.startTime &&
                            it.endTime == session.endTime
                    }
                }
                .map { session ->
                    RevisionRecord(
                        id = "session_${session.startTime}_${session.endTime}",
                        subjectName = session.subject.ifBlank { "General" },
                        chapterName = session.selectedChapter,
                        durationMinutes = (session.actualFocusedSeconds / 60L).toInt().coerceAtLeast(1),
                        startTime = session.startTime,
                        endTime = session.endTime,
                        timestamp = session.endTime,
                        revisionStage = session.topic,
                        durationSeconds = session.actualFocusedSeconds
                    )
                }
            if (missingRevisionRecords.isNotEmpty()) {
                _revisionHistory.value = missingRevisionRecords + _revisionHistory.value
                persistRevisionHistory()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _notificationStateLoaded.value = true
        }
    }

    private fun persistBrainDumps() {
        viewModelScope.launch {
            try {
                val array = org.json.JSONArray()
                _brainDumpNotes.value.forEach { note ->
                    val obj = org.json.JSONObject().apply {
                        put("id", note.id)
                        put("note", note.note)
                        put("timestamp", note.timestamp)
                        put("subject", note.subject)
                        put("isDone", note.isDone)
                    }
                    array.put(obj)
                }
                repository.setSetting("brain_dumps_json", array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun persistRevisionHistory() {
        viewModelScope.launch {
            try {
                val array = org.json.JSONArray()
                _revisionHistory.value.forEach { rec ->
                    val obj = org.json.JSONObject().apply {
                        put("id", rec.id)
                        put("subjectName", rec.subjectName)
                        put("chapterName", rec.chapterName)
                        put("durationMinutes", rec.durationMinutes)
                        put("durationSeconds", rec.durationSeconds)
                        put("startTime", rec.startTime)
                        put("endTime", rec.endTime)
                        put("timestamp", rec.timestamp)
                        put("revisionStage", rec.revisionStage)
                    }
                    array.put(obj)
                }
                repository.setSetting("revision_history_json", array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    private fun persistExamTargets() {
        viewModelScope.launch {
            try {
                val array = org.json.JSONArray()
                _examTargets.value.forEach { target ->
                    val obj = org.json.JSONObject().apply {
                        put("id", target.id)
                        put("title", target.title)
                        put("targetDateEpochMs", target.targetDateEpochMs)
                        put("targetHours", target.targetHours.toDouble())
                        put("subject", target.subject)
                        put("notes", target.notes)
                    }
                    array.put(obj)
                }
                repository.setSetting("exam_targets_json", array.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDaynexaConsent(enabled: Boolean) {
        _isDaynexaConnected.value = enabled
        val now = if (enabled) System.currentTimeMillis() else 0L
        _daynexaConnectedAt.value = now
        viewModelScope.launch {
            repository.setSetting("daynexa_integration_enabled", if (enabled) "true" else "false")
            repository.setSetting("daynexa_connected_timestamp", now.toString())
            if (enabled) {
                _snackbarMessage.emit("Daynexa Integration connected (API v1).")
            } else {
                _snackbarMessage.emit("Daynexa Integration disconnected.")
            }
        }
    }

    fun navigateTo(tab: NavigationTab) {
        _currentTab.value = tab
    }

    fun setFullScreenFocus(enabled: Boolean) {
        _isFullScreenFocus.value = enabled
    }

    fun setPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    fun setCalendarDate(epochMs: Long) {
        _selectedCalendarDate.value = epochMs
    }

    fun setHistorySearchQuery(q: String) {
        _historySearchQuery.value = q
    }

    fun setHistorySubjectFilter(sub: String?) {
        _historySubjectFilter.value = sub
    }

    fun setHistoryMinProductivity(rating: Int) {
        _historyMinProductivity.value = rating
    }

    fun setTheme(theme: String) {
        _currentTheme.value = theme
        app.saveCachedTheme(theme)
        viewModelScope.launch {
            repository.setSetting("theme", theme)
        }
    }

    fun setLanguage(lang: String) {
        _language.value = lang
        app.saveCachedLanguage(lang)
        viewModelScope.launch {
            repository.setSetting("language", lang)
        }
    }

    // Timer Actions
    fun startTimerWithPreset(preset: PresetEntity): Boolean {
        val mode = when (preset.type) {
            "POMODORO" -> SessionMode.POMODORO
            "STOPWATCH" -> SessionMode.STOPWATCH
            else -> SessionMode.COUNTDOWN
        }
        val chapter = chapters.value.firstOrNull {
            it.subjectName.equals(preset.subject, ignoreCase = true) &&
                it.chapterName.equals(preset.topic, ignoreCase = true)
        }?.chapterName.orEmpty()
        val (blocked, reason) = isTimerBlockedFor(preset.subject, chapter)
        if (blocked || !ensureTimerIdle()) {
            showTimerStartBlock(reason, blocked)
            return false
        }
        timerEngine.configureSession(
            mode = mode,
            subject = preset.subject,
            selectedChapter = chapter,
            topic = preset.topic,
            durationMinutes = preset.durationMinutes,
            pomodoroFocusMin = preset.pomodoroFocusMin,
            pomodoroShortBreakMin = preset.pomodoroShortBreakMin,
            pomodoroLongBreakMin = preset.pomodoroLongBreakMin,
            pomodoroRounds = preset.pomodoroRounds,
            tags = if (preset.tags.isNotBlank()) preset.tags.split(",") else emptyList()
        )
        timerEngine.startSession()
        _currentTab.value = NavigationTab.TIMER
        return true
    }

    fun startQuickTimer(
        durationMinutes: Int,
        subject: String,
        mode: SessionMode = SessionMode.COUNTDOWN,
        chapter: String = ""
    ): Boolean {
        val (blocked, reason) = isTimerBlockedFor(subject, chapter)
        if (blocked || !ensureTimerIdle()) {
            showTimerStartBlock(reason, blocked)
            return false
        }
        timerEngine.configureSession(
            mode = mode,
            subject = subject,
            selectedChapter = chapter,
            durationMinutes = durationMinutes
        )
        timerEngine.startSession()
        _currentTab.value = NavigationTab.TIMER
        return true
    }

    fun startTimerSession(
        mode: SessionMode,
        subject: String,
        chapter: String = "",
        topic: String = "",
        goal: String = "",
        targetId: String = "",
        autoSaveOnFinish: Boolean = false,
        durationMinutes: Int = 25,
        pomodoroFocusMin: Int = 25,
        pomodoroShortBreakMin: Int = 5,
        pomodoroLongBreakMin: Int = 15,
        pomodoroRounds: Int = 4,
        autoStartBreaks: Boolean = false,
        destination: NavigationTab = NavigationTab.TIMER
    ): Boolean {
        val (blocked, reason) = isTimerBlockedFor(subject, chapter)
        if (blocked || !ensureTimerIdle()) {
            showTimerStartBlock(reason, blocked)
            return false
        }
        timerEngine.configureSession(
            mode = mode,
            subject = subject,
            selectedChapter = chapter,
            topic = topic,
            goal = goal,
            targetId = targetId,
            autoSaveOnFinish = autoSaveOnFinish,
            durationMinutes = durationMinutes,
            pomodoroFocusMin = pomodoroFocusMin,
            pomodoroShortBreakMin = pomodoroShortBreakMin,
            pomodoroLongBreakMin = pomodoroLongBreakMin,
            pomodoroRounds = pomodoroRounds,
            autoStartBreaks = autoStartBreaks
        )
        timerEngine.startSession()
        _currentTab.value = destination
        return true
    }

    fun startRevisionTimer(
        durationMinutes: Int,
        subject: String,
        chapter: String,
        stage: String = "Revision"
    ): Boolean {
        val (blocked, reason) = isTimerBlockedFor(subject, chapter)
        if (blocked || !ensureTimerIdle()) {
            showTimerStartBlock(reason, blocked)
            return false
        }
        timerEngine.configureSession(
            mode = SessionMode.COUNTDOWN,
            subject = subject,
            selectedChapter = chapter,
            topic = "Revision: $stage",
            durationMinutes = maxOf(60, durationMinutes)
        )
        timerEngine.startSession()
        _currentTab.value = NavigationTab.REVISION_HUB
        return true
    }

    private fun ensureTimerIdle(): Boolean {
        return timerEngine.uiState.value.status == TimerStatus.IDLE
    }

    private fun showTimerStartBlock(message: String, blocked: Boolean) {
        viewModelScope.launch {
            _snackbarMessage.emit(
                when {
                    blocked -> message
                    else -> "Finish or cancel the current timer before starting another session."
                }
            )
        }
    }

    // Subject operations
    fun createSubject(name: String, colorHex: String, iconName: String, targetHours: Float, description: String) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                _snackbarMessage.emit("Subject name cannot be empty.")
                return@launch
            }
            val existing = subjects.value.find { it.name.trim().equals(trimmedName, ignoreCase = true) }
            if (existing != null) {
                _snackbarMessage.emit("Subject «$trimmedName» already exists.")
                return@launch
            }
            repository.insertSubject(
                SubjectEntity(
                    name = trimmedName,
                    colorHex = colorHex,
                    iconName = iconName,
                    targetHours = targetHours,
                    description = description,
                    orderIndex = subjects.value.size
                )
            )
            _snackbarMessage.emit("Subject «$trimmedName» created.")
        }
    }

    fun updateSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            val trimmedName = subject.name.trim()
            if (trimmedName.isBlank()) {
                _snackbarMessage.emit("Subject name cannot be empty.")
                return@launch
            }
            val duplicate = subjects.value.any {
                it.id != subject.id && it.name.trim().equals(trimmedName, ignoreCase = true)
            }
            if (duplicate) {
                _snackbarMessage.emit("Subject «$trimmedName» already exists.")
                return@launch
            }
            repository.updateSubject(subject.copy(name = trimmedName))
            _snackbarMessage.emit("Subject «$trimmedName» updated.")
        }
    }

    fun deleteSubject(subject: SubjectEntity) {
        viewModelScope.launch {
            repository.deleteSubject(subject)
            _snackbarMessage.emit("Subject «${subject.name}» deleted.")
        }
    }

    fun createTopic(subjectId: Long, subjectName: String, name: String) {
        viewModelScope.launch {
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) return@launch
            val duplicate = topics.value.any {
                it.subjectId == subjectId && it.name.trim().equals(trimmedName, ignoreCase = true)
            }
            if (duplicate) {
                _snackbarMessage.emit("Topic «$trimmedName» already exists in $subjectName.")
                return@launch
            }
            repository.insertTopic(TopicEntity(subjectId = subjectId, subjectName = subjectName, name = trimmedName))
            _snackbarMessage.emit("Topic «$trimmedName» added.")
        }
    }

    fun deleteTopic(topic: TopicEntity) {
        viewModelScope.launch {
            repository.deleteTopic(topic)
        }
    }

    // Goal operations
    fun updateGoalTarget(periodType: String, newTargetMinutes: Int) {
        viewModelScope.launch {
            val existing = goals.value.find { it.periodType == periodType }
            if (existing != null) {
                repository.updateGoal(existing.copy(targetMinutes = newTargetMinutes))
            } else {
                repository.insertGoal(StudyGoalEntity(periodType = periodType, targetMinutes = newTargetMinutes))
            }
            _snackbarMessage.emit("Updated $periodType goal to ${newTargetMinutes / 60}h ${newTargetMinutes % 60}m.")
        }
    }

    // Preset operations
    fun savePreset(preset: PresetEntity) {
        viewModelScope.launch {
            if (preset.id == 0L) {
                repository.insertPreset(preset)
            } else {
                repository.updatePreset(preset)
            }
            _snackbarMessage.emit("Preset «${preset.title}» saved.")
        }
    }

    fun deletePreset(preset: PresetEntity) {
        viewModelScope.launch {
            repository.deletePreset(preset)
            _snackbarMessage.emit("Preset «${preset.title}» deleted.")
        }
    }

    // Manual study data input
    fun addManualSession(
        subject: String,
        topic: String = "",
        chapter: String = "",
        durationMinutes: Int,
        category: String = "Study",
        notes: String = "",
        rating: Int = 4,
        targetId: String = "",
        timestamp: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val durationSec = durationMinutes.toLong() * 60L
            val session = StudySessionEntity(
                startTime = timestamp - (durationSec * 1000L),
                endTime = timestamp,
                durationSeconds = durationSec,
                actualFocusedSeconds = durationSec,
                pausedSeconds = 0L,
                sessionType = "MANUAL",
                subject = subject.ifBlank { "General" },
                topic = topic,
                selectedChapter = chapter,
                category = category.ifBlank { "Study" },
                notes = notes,
                productivityRating = rating,
                targetId = targetId,
                completionStatus = "COMPLETED",
                createdTimestamp = timestamp,
                updatedTimestamp = timestamp
            )
            repository.insertSession(session)
            _snackbarMessage.emit("Study Data saved successfully!")
        }
    }

    // Session edits & delete
    fun updateSession(session: StudySessionEntity) {
        viewModelScope.launch {
            repository.updateSession(session.copy(updatedTimestamp = System.currentTimeMillis()))
            _snackbarMessage.emit("Session updated.")
        }
    }

    fun deleteSession(session: StudySessionEntity) {
        lastDeletedSession = session
        viewModelScope.launch {
            repository.deleteSession(session)
            _snackbarMessage.emit("Session deleted. Tap Undo to restore.")
        }
    }

    fun undoDeleteSession() {
        val session = lastDeletedSession ?: return
        viewModelScope.launch {
            repository.insertSession(session)
            lastDeletedSession = null
            _snackbarMessage.emit("Session restored.")
        }
    }

    fun clearAllAppData() {
        viewModelScope.launch {
            reminders.value.forEach { StudyReminderScheduler.cancel(getApplication(), it.id) }
            repository.clearAllAppData()
            _brainDumpNotes.value = emptyList()
            _examTargets.value = emptyList()
            StudyReminderScheduler.scheduleAll(
                getApplication(),
                repository.allReminders.first()
            )
        }
    }

    // Reminders
    fun toggleReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            val updated = reminder.copy(enabled = !reminder.enabled)
            repository.updateReminder(updated)
            StudyReminderScheduler.schedule(getApplication(), updated)
            _snackbarMessage.emit(if (updated.enabled) "Reminder enabled." else "Reminder disabled.")
        }
    }

    fun saveReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            val savedReminder = if (reminder.id == 0L) {
                reminder.copy(id = repository.insertReminder(reminder))
            } else {
                repository.updateReminder(reminder)
                reminder
            }
            StudyReminderScheduler.schedule(getApplication(), savedReminder)
            _snackbarMessage.emit("Reminder saved.")
        }
    }

    fun deleteReminder(reminder: ReminderEntity) {
        viewModelScope.launch {
            StudyReminderScheduler.cancel(getApplication(), reminder.id)
            repository.deleteReminder(reminder)
        }
    }

    // Export & Import
    suspend fun exportJson(): String {
        return repository.exportDataJson()
    }

    suspend fun exportCsv(): String {
        return repository.exportSessionsCsv()
    }

    suspend fun importJson(json: String, replace: Boolean): Boolean {
        val success = repository.importDataJson(json, replace)
        if (success) {
            rescheduleReminders()
            _snackbarMessage.emit("Backup imported successfully.")
        } else {
            _snackbarMessage.emit("Import failed: Invalid backup format.")
        }
        return success
    }

    fun rescheduleReminders() {
        viewModelScope.launch {
            StudyReminderScheduler.scheduleAll(
                getApplication(),
                repository.allReminders.first(),
                repository.allChapters.first()
            )
        }
    }

    // Brain Dump & Focus Notes Feature
    private val _brainDumpNotes = MutableStateFlow<List<BrainDumpNote>>(emptyList())
    val brainDumpNotes: StateFlow<List<BrainDumpNote>> = _brainDumpNotes.asStateFlow()

    fun addBrainDumpNote(text: String, subject: String = "General") {
        if (text.isBlank()) return
        val newNote = BrainDumpNote(note = text.trim(), subject = subject)
        _brainDumpNotes.value = listOf(newNote) + _brainDumpNotes.value
        persistBrainDumps()
        viewModelScope.launch {
            _snackbarMessage.emit("Thought captured to Brain Dump!")
        }
    }

    fun toggleBrainDumpDone(id: String) {
        _brainDumpNotes.value = _brainDumpNotes.value.map {
            if (it.id == id) it.copy(isDone = !it.isDone) else it
        }
        persistBrainDumps()
    }

    fun deleteBrainDumpNote(id: String) {
        _brainDumpNotes.value = _brainDumpNotes.value.filter { it.id != id }
        persistBrainDumps()
    }

    fun clearCompletedBrainDumps() {
        _brainDumpNotes.value = _brainDumpNotes.value.filter { !it.isDone }
        persistBrainDumps()
    }


    // Exam Target Planner Methods
    fun addExamTarget(
        title: String,
        targetDateEpochMs: Long,
        targetHours: Float = 50f,
        subject: String = "General",
        notes: String = ""
    ) {
        if (title.isBlank()) return
        val newTarget = ExamTarget(
            title = title.trim(),
            targetDateEpochMs = targetDateEpochMs,
            targetHours = targetHours.coerceAtLeast(1f),
            subject = subject.ifBlank { "General" },
            notes = notes.trim()
        )
        _examTargets.value = _examTargets.value + newTarget
        persistExamTargets()
        viewModelScope.launch {
            _snackbarMessage.emit("Target milestone created!")
        }
    }

    fun deleteExamTarget(id: String) {
        _examTargets.value = _examTargets.value.filter { it.id != id }
        persistExamTargets()
    }

    fun editExamTarget(updated: ExamTarget) {
        _examTargets.value = _examTargets.value.map {
            if (it.id == updated.id) updated else it
        }
        persistExamTargets()
    }

    fun generateShareableStudyReport(): String {
        val stats = periodStatistics.value
        val streak = streakInfo.value
        val df = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val dateStr = df.format(Date())

        val totalHrs = stats.totalFocusedSeconds / 3600
        val totalMins = (stats.totalFocusedSeconds % 3600) / 60

        val sb = StringBuilder()
        sb.append("====================================\n")
        sb.append("      FOCENTRA STUDY REPORT\n")
        sb.append("          $dateStr\n")
        sb.append("====================================\n\n")
        sb.append("Total Focus Time: ${totalHrs}h ${totalMins}m\n")
        sb.append("Sessions Completed: ${stats.sessionCount}\n")
        sb.append("Average Session: ${stats.averageSessionMinutes} min\n")
        sb.append("Focus Score: ${stats.focusScore}/100\n")
        sb.append("Current Streak: ${streak.currentStreak} Days\n")
        sb.append("Longest Streak: ${streak.longestStreak} Days\n")
        sb.append("Primary Subject: ${stats.mostStudiedSubject}\n")
        sb.append("Top Output Day: ${stats.mostProductiveDay}\n")
        sb.append("Peak Concentration: ${stats.mostProductiveHour}\n\n")
        sb.append("Keep learning with discipline! - Focentra Offline Study OS")
        return sb.toString()
    }
}
