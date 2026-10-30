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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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
    val revisionStage: String = "Revision"
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
    private val pendingSystemNotificationIds = mutableSetOf<String>()
    private val systemNotificationEvents = Channel<AppNotification>(Channel.UNLIMITED)

    val systemNotificationEvent: Flow<AppNotification> = systemNotificationEvents.receiveAsFlow()

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
        pendingSystemNotificationIds.remove(id)
        if (!wasPosted) return

        val updated = _postedNotificationIds.value + id
        _postedNotificationIds.value = updated
        persistNotificationIds("posted_notification_ids", updated)
    }

    fun retrySystemNotifications() {
        if (!_notificationStateLoaded.value) return
        activeNotifications.value.forEach(::enqueueSystemNotification)
    }

    private fun enqueueSystemNotification(notification: AppNotification) {
        if (notification.id in _postedNotificationIds.value ||
            !pendingSystemNotificationIds.add(notification.id)
        ) return

        val result = systemNotificationEvents.trySend(notification)
        if (result.isFailure) {
            pendingSystemNotificationIds.remove(notification.id)
            Log.e("FocentraNotifications", "Failed to queue system notification ${notification.id}.", result.exceptionOrNull())
        }
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
    val dbRevisionSkips = repository.allRevisionSkips.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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
            repository.updateChapter(chapter.copy(tapCount = newTapCount, status = newStatus, lastStudiedTimestamp = now, completedTimestamp = compTime))
            if (newTapCount == 3) {
                StudyReminderNotifications.scheduleEbbinghausRevisions(getApplication(), chapter.subjectName, chapter.chapterName, compTime)
            }
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
            repository.updateChapter(chapter.copy(status = newStatus, tapCount = newTapCount, lastStudiedTimestamp = now, completedTimestamp = compTime))
            if (newTapCount == 3) {
                StudyReminderNotifications.scheduleEbbinghausRevisions(getApplication(), chapter.subjectName, chapter.chapterName, compTime)
            }
        }
    }

    fun updateChapter(chapter: ChapterEntity) {
        viewModelScope.launch {
            repository.updateChapter(chapter)
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

    private val notificationProgress = combine(todayFocusedMinutes, notificationClock) { minutes, now ->
        minutes to now
    }

    // Derived states optimized on Dispatchers.Default background threads
    val activeNotifications = combine(chapters, sessions, goals, notificationProgress, _dismissedNotificationIds) { chapterList, sessionList, goalList, todayProgress, dismissed ->
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val list = mutableListOf<AppNotification>()
            val (todayMins, now) = todayProgress
            val ONE_DAY_MS = 24 * 60 * 60 * 1000L
            val THREE_DAYS_MS = 3 * ONE_DAY_MS
            val FIVE_DAYS_MS = 5 * ONE_DAY_MS
            val SEVEN_DAYS_MS = 7 * ONE_DAY_MS
            val FIFTEEN_DAYS_MS = 15 * ONE_DAY_MS

            val todayDateStr = SimpleDateFormat("yyyy_MM_dd", Locale.getDefault()).format(Date(now))

            val THIRTY_DAYS_MS = 30 * ONE_DAY_MS

            // 3. Ebbinghaus Forgetting Curve Alerts (1d, 3d, 7d, 15d, 30d)
            chapterList.forEach { ch ->
                val isCompleted = ch.tapCount >= 3 || ch.status == "অধ্যায় Completely শেষ"
                if (isCompleted) {
                    val compTime = if (ch.completedTimestamp > 0) ch.completedTimestamp else ch.lastStudiedTimestamp
                    val elapsed = now - compTime
                    val (title, msg, stage) = when {
                        elapsed >= THIRTY_DAYS_MS -> Triple(
                            "30th Day Master Review Cycle",
                            "It's time for the 30th day master review cycle for «${ch.chapterName}» in ${ch.subjectName}. Solidify your memory for long term!",
                            "30d"
                        )
                        elapsed >= FIFTEEN_DAYS_MS -> Triple(
                            "15th Day Final Revision Test",
                            "It's time for the 15th day revision test for «${ch.chapterName}» in ${ch.subjectName}. Complete a 30-minute revision session!",
                            "15d"
                        )
                        elapsed >= SEVEN_DAYS_MS -> Triple(
                            "7th Day Problem Solving Revision",
                            "«${ch.chapterName}» in ${ch.subjectName} was studied 7 days ago. Start a 30-minute revision session to reinforce memory!",
                            "7d"
                        )
                        elapsed >= THREE_DAYS_MS -> Triple(
                            "3rd Day Scientific Revision",
                            "The 3-day scientific revision cycle for «${ch.chapterName}» in ${ch.subjectName} has begun. Refresh your memory with a 30-minute session!",
                            "3d"
                        )
                        elapsed >= ONE_DAY_MS -> Triple(
                            "1st Day Quick Recap Revision",
                            "You recently completed «${ch.chapterName}» in ${ch.subjectName}. Do a quick recap revision now to retain key concepts!",
                            "1d"
                        )
                        else -> Triple("", "", "")
                    }

                    if (title.isNotEmpty()) {
                        val notifId = "rev_${ch.id}_${compTime}_${stage}"
                        list.add(
                            AppNotification(
                                id = notifId,
                                type = NotificationType.REVISION_DUE,
                                title = title,
                                message = msg,
                                subjectName = ch.subjectName,
                                chapterName = ch.chapterName,
                                timestamp = compTime
                            )
                        )
                    }
                } else if (ch.status != "পড়া শুরু হয় নাই" && ch.status.isNotBlank()) {
                    val lastStudy = ch.lastStudiedTimestamp
                    if (lastStudy > 0 && (now - lastStudy >= FIVE_DAYS_MS)) {
                        val notifId = "inact_${ch.id}_${todayDateStr}"
                        list.add(
                            AppNotification(
                                id = notifId,
                                type = NotificationType.INACTIVITY_WARNING,
                                title = "Completion Reminder",
                                message = "«${ch.chapterName}» in ${ch.subjectName} hasn't been studied for 5 days. Start a focus session to finish it today!",
                                subjectName = ch.subjectName,
                                chapterName = ch.chapterName,
                                timestamp = lastStudy
                            )
                        )
                    }
                }
            }

            val todayStartMs = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            // 8. Dynamic Progress Bar in Daily Target Tracker
            val dailyGoal = goalList.find { it.periodType == "DAILY" }?.targetMinutes ?: 480
            val remainingMins = (dailyGoal - todayMins).coerceAtLeast(0L)

            val totalGoalMins = dailyGoal.toFloat().coerceAtLeast(1f)
            val completedRatio = (todayMins / totalGoalMins).coerceIn(0f, 1f)
            val filledBlocks = (completedRatio * 10).toInt()
            val emptyBlocks = 10 - filledBlocks
            val progressBarText = "[${"▓".repeat(filledBlocks)}${"░".repeat(emptyBlocks)}]"
            val percentInt = (completedRatio * 100).toInt()

            val todayHrsText = String.format(Locale.US, "%.1f", todayMins / 60f)
            val goalHrsText = String.format(Locale.US, "%.1f", dailyGoal / 60f)

            val remainingHours = remainingMins / 60
            val remainingMinutes = remainingMins % 60
            val remainingTimeText = if (remainingMinutes == 0L) {
                "${remainingHours}h remaining"
            } else if (remainingHours == 0L) {
                "${remainingMinutes}m remaining"
            } else {
                "${remainingHours}h ${remainingMinutes}m remaining"
            }

            val goalMessage = "Daily Goal: ${todayHrsText} / ${goalHrsText}h $progressBarText $percentInt% completed! ($remainingTimeText)"
            val goalNotifId = "goal_warning_${todayDateStr}"
            list.add(
                AppNotification(
                    id = goalNotifId,
                    type = NotificationType.GOAL_WARNING,
                    title = "Daily Target Tracker",
                    message = goalMessage,
                    timestamp = todayStartMs + (7 * 3600 * 1000L)
                )
            )

            // 2. Adaptive AI / Peak Focus Hour Reminder
            val calNow = Calendar.getInstance().apply { timeInMillis = now }
            val currentHour = calNow.get(Calendar.HOUR_OF_DAY)
            val currentDayOfWeek = calNow.get(Calendar.DAY_OF_WEEK) // 1 = Sunday

            if (currentHour in 21..23 || currentHour in 6..8) {
                val notifId = "peak_hour_${todayDateStr}_${currentHour}"
                list.add(
                    AppNotification(
                        id = notifId,
                        type = NotificationType.DAILY_TARGET,
                        title = "Peak Focus Hour Started!",
                        message = "Your peak productivity window is starting now. Launch a focus session to make the most of it!",
                        timestamp = todayStartMs + (currentHour * 3600 * 1000L)
                    )
                )
            }

            // 4. Streak Saver / Evening Intervention (after 7 PM if 0 mins studied today)
            if (currentHour >= 19 && todayMins == 0L) {
                val currentStreakVal = calculateStreak(sessionList).currentStreak
                val notifId = "streak_saver_${todayDateStr}"
                list.add(
                    AppNotification(
                        id = notifId,
                        type = NotificationType.INACTIVITY_WARNING,
                        title = "Study Streak at Risk!",
                        message = "Your ${currentStreakVal}-day study streak is in danger! Complete at least a 10-minute focus session now to save your streak.",
                        timestamp = todayStartMs + (19 * 3600 * 1000L)
                    )
                )
            }

            // 6. Brain Dump & Daily Reflection Prompt (after 10 PM)
            if (currentHour >= 22) {
                val notifId = "brain_dump_prompt_${todayDateStr}"
                list.add(
                    AppNotification(
                        id = notifId,
                        type = NotificationType.DAILY_TARGET,
                        title = "Night Reflection & Brain Dump",
                        message = "Clear your mind before sleep! Unload remaining thoughts or study notes into Brain Dump for a fresh start tomorrow.",
                        timestamp = todayStartMs + (22 * 3600 * 1000L)
                    )
                )
            }

            // 7. Weekly Digest & Productivity Benchmark (Sunday mornings)
            if (currentDayOfWeek == Calendar.SUNDAY && currentHour in 6..11) {
                val weekStart = now - SEVEN_DAYS_MS
                val totalWeeklyMins = sessionList
                    .filter { it.startTime in weekStart..now }
                    .sumOf { it.actualFocusedSeconds } / 60
                val weeklyHrs = String.format(Locale.US, "%.1f", totalWeeklyMins / 60f)
                val notifId = "weekly_digest_${todayDateStr}"
                list.add(
                    AppNotification(
                        id = notifId,
                        type = NotificationType.DAILY_TARGET,
                        title = "Weekly Focus Digest",
                        message = "Great effort this week! You completed $weeklyHrs hours of focused study across the past 7 days.",
                        timestamp = todayStartMs + (8 * 3600 * 1000L)
                    )
                )
            }

            // 10. Ambient Vibe Quick Launch Notification
            val ambientNotifId = "ambient_vibe_${todayDateStr}"
            list.add(
                AppNotification(
                    id = ambientNotifId,
                    type = NotificationType.DAILY_TARGET,
                    title = "Ambient Focus Environment",
                    message = "Set the perfect study mood! Launch a focus session with deep focus background soundscapes.",
                    timestamp = todayStartMs + (9 * 3600 * 1000L)
                )
            )

            // Achievement notifications
            val totalHours = sessionList.sumOf { it.actualFocusedSeconds } / 3600f
            if (totalHours >= 10f) {
                val notifId = "ach_10h_${todayDateStr}"
                list.add(
                    AppNotification(
                        id = notifId,
                        type = NotificationType.MOTIVATION,
                        title = "Milestone Achieved!",
                        message = "You have completed over 10 hours of focused study! Keep up the momentum.",
                        timestamp = now
                    )
                )
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

    // Revision History Log Feature
    private val _revisionHistory = MutableStateFlow<List<RevisionRecord>>(emptyList())
    val revisionHistory: StateFlow<List<RevisionRecord>> = _revisionHistory.asStateFlow()

    fun recordRevisionSkip(
        subjectName: String,
        chapterName: String,
        reason: String,
        scheduledRecapTimestamp: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val maxAllowedDate = now + (4 * 24 * 3600 * 1000L) // 4 Days limit
            if (scheduledRecapTimestamp > maxAllowedDate) {
                onResult(false, "সিলেক্ট করা তারিখ ও সময় অবশ্যই আগামী ৪ দিনের মধ্যে হতে হবে।")
                return@launch
            }

            val currentSkips = dbRevisionSkips.value.count {
                it.subjectName.equals(subjectName, ignoreCase = true) &&
                it.chapterName.equals(chapterName, ignoreCase = true)
            }
            if (currentSkips >= 5) {
                onResult(false, "আপনি সর্বোচ্চ ৫ বার রিভিশন Dismiss করেছেন! অন্য কোনো সাবজেক্টের টাইমার অন করতে হলে আপনাকে «$chapterName» অধ্যায় ১০ ঘণ্টা রিভিশন পড়তে হবে।")
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
                scheduledRecapTimestamp
            )

            val recapNotif = NotificationEntity(
                id = "recap_scheduled_${skipEntity.id}",
                type = NotificationType.RECAP_REMINDER.name,
                title = "রিভিশন রিক্যাপ স্কেজুল করা হয়েছে",
                message = "«$chapterName» ($subjectName) এর রিভিশন ডিসমিস করা হয়েছে এবং রিক্যাপ স্কেজুল করা হয়েছে।",
                subjectName = subjectName,
                chapterName = chapterName,
                timestamp = now
            )
            repository.insertNotification(recapNotif)

            _snackbarMessage.emit("রিভিশন ডিসমিস করা হয়েছে এবং রিক্যাপ স্কেজুল করা হয়েছে।")
            onResult(true, "ডিসমিস সফলভাবে রেকর্ড করা হয়েছে।")
        }
    }

    fun isTimerBlockedFor(targetSubject: String, targetChapter: String): Pair<Boolean, String> {
        val pendingRevisions = activeNotifications.value.filter { it.type == NotificationType.REVISION_DUE }
        val skips = dbRevisionSkips.value
        val now = System.currentTimeMillis()

        for (item in pendingRevisions) {
            val matchingSkips = skips.filter {
                it.subjectName.equals(item.subjectName, ignoreCase = true) &&
                it.chapterName.equals(item.chapterName, ignoreCase = true)
            }
            val skipCount = matchingSkips.size

            // If there's an active scheduled recap in the future and skip count < 5, skipping is active!
            val hasActiveSkip = skipCount < 5 && matchingSkips.any { it.scheduledRecapTimestamp > now }
            if (hasActiveSkip) {
                continue // Unblocked because user explicitly skipped it and recap time has not arrived
            }

            val requiredMins = if (skipCount >= 5) 600 else 60 // 10 hours if skip count >= 5, else 1 hour
            val completedMins = revisionHistory.value.filter {
                it.subjectName.equals(item.subjectName, ignoreCase = true) &&
                it.chapterName.equals(item.chapterName, ignoreCase = true) &&
                it.timestamp >= item.timestamp
            }.sumOf { it.durationMinutes }

            if (completedMins < requiredMins) {
                val isTargetMatching = targetSubject.trim().equals(item.subjectName.trim(), ignoreCase = true) &&
                        targetChapter.trim().equals(item.chapterName.trim(), ignoreCase = true)
                if (!isTargetMatching) {
                    val reqHoursText = if (requiredMins >= 60) "${requiredMins / 60} ঘণ্টা" else "${requiredMins} মিনিট"
                    val skipMsg = if (skipCount >= 5) " (সর্বোচ্চ ৫ বার Dismiss অতিক্রম হয়েছে - ১০ ঘণ্টা পড়া বাধ্যতামূলক)" else ""
                    return Pair(
                        true,
                        "অন্য কোনো সাবজেক্টের টাইমার চালু করার আগে আপনাকে «${item.chapterName}» ($item.subjectName) অধ্যায়ের অন্তত $reqHoursText রিভিশন সম্পন্ন করতে হবে।$skipMsg"
                    )
                }
            }
        }
        return Pair(false, "")
    }

    fun recordRevision(
        subjectName: String,
        chapterName: String,
        durationMinutes: Int,
        startTime: Long = System.currentTimeMillis() - (durationMinutes.coerceAtLeast(1) * 60 * 1000L),
        endTime: Long = System.currentTimeMillis(),
        stage: String = "Revision"
    ) {
        val newRecord = RevisionRecord(
            subjectName = subjectName.ifBlank { "General" },
            chapterName = chapterName.ifBlank { "General Revision" },
            durationMinutes = durationMinutes.coerceAtLeast(1),
            startTime = startTime,
            endTime = endTime,
            timestamp = endTime,
            revisionStage = stage.ifBlank { "Revision" }
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
            var lastHandledSessionId: String? = null
            timerEngine.uiState.collect { state ->
                if (state.status == TimerStatus.COMPLETED &&
                    state.completedSessionData != null &&
                    isRevisionTopic(state.topic)
                ) {
                    val session = state.completedSessionData
                    val sessionKey = revisionCompletionKey(session)
                    if (sessionKey != lastHandledSessionId) {
                        lastHandledSessionId = sessionKey
                        val chapterName = if (state.selectedChapter.isNotBlank()) state.selectedChapter else (if (session.topic.isNotBlank()) session.topic else "General Chapter")
                        val durMins = (session.actualFocusedSeconds / 60).toInt().coerceAtLeast(1)
                        recordRevision(
                            subjectName = session.subject.ifBlank { "General" },
                            chapterName = chapterName,
                            durationMinutes = durMins,
                            stage = if (session.topic.isNotBlank()) session.topic else "Revision"
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            combine(activeNotifications, _notificationStateLoaded) { notifications, isLoaded ->
                if (isLoaded) notifications else emptyList()
            }.collect { notifications ->
                notifications.forEach { notif ->
                    enqueueSystemNotification(notif)
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
                            revisionStage = obj.optString("revisionStage", "Revision")
                        )
                    )
                }
                _revisionHistory.value = list
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
    fun startTimerWithPreset(preset: PresetEntity) {
        val mode = when (preset.type) {
            "POMODORO" -> SessionMode.POMODORO
            "STOPWATCH" -> SessionMode.STOPWATCH
            else -> SessionMode.COUNTDOWN
        }
        timerEngine.configureSession(
            mode = mode,
            subject = preset.subject,
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
    }

    fun startQuickTimer(durationMinutes: Int, subject: String, mode: SessionMode = SessionMode.COUNTDOWN) {
        timerEngine.configureSession(
            mode = mode,
            subject = subject,
            durationMinutes = durationMinutes
        )
        timerEngine.startSession()
        _currentTab.value = NavigationTab.TIMER
    }

    fun startRevisionTimer(durationMinutes: Int, subject: String, chapter: String) {
        timerEngine.configureSession(
            mode = SessionMode.COUNTDOWN,
            subject = subject,
            selectedChapter = chapter,
            topic = "Revision",
            durationMinutes = durationMinutes
        )
        timerEngine.startSession()
        _currentTab.value = NavigationTab.TIMER
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
                repository.allReminders.first()
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
