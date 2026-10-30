package com.niloy.focentra.engine

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.data.repository.StudyRepository
import com.niloy.focentra.service.StudyTimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SessionMode {
    COUNTDOWN,
    STOPWATCH,
    POMODORO
}

enum class PomodoroPhase {
    FOCUS,
    SHORT_BREAK,
    LONG_BREAK
}

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

data class DistractionLog(
    val category: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class TimerUiState(
    val status: TimerStatus = TimerStatus.IDLE,
    val mode: SessionMode = SessionMode.COUNTDOWN,
    val subject: String = "Mathematics",
    val selectedChapter: String = "",
    val topic: String = "",
    val goal: String = "",
    val targetId: String = "",
    val tags: List<String> = emptyList(),
    val targetDurationSeconds: Long = 25 * 60L,
    val elapsedSeconds: Long = 0L, // Total time elapsed since start
    val focusedSeconds: Long = 0L, // Focused study time excluding pauses
    val pausedSeconds: Long = 0L,  // Accumulated pause time
    val remainingSeconds: Long = 25 * 60L, // For countdown / pomodoro
    val progress: Float = 0f, // 0.0 to 1.0
    // Pomodoro specifics
    val pomodoroPhase: PomodoroPhase = PomodoroPhase.FOCUS,
    val currentRound: Int = 1,
    val totalRounds: Int = 4,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val autoStartBreaks: Boolean = false,
    val autoStartFocus: Boolean = false,
    // Distraction tracking
    val distractionCount: Int = 0,
    val distractions: List<DistractionLog> = emptyList(),
    // Metadata
    val sessionStartTime: Long = 0L,
    val keepScreenAwake: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val showCompletionSheet: Boolean = false,
    val completedSessionData: StudySessionEntity? = null
)

internal data class PomodoroSessionTotals(
    val focusedSeconds: Long = 0L,
    val breakSeconds: Long = 0L,
    val pausedSeconds: Long = 0L
) {
    fun includePhase(phase: PomodoroPhase, elapsedSeconds: Long, pausedSeconds: Long): PomodoroSessionTotals =
        when (phase) {
            PomodoroPhase.FOCUS -> copy(
                focusedSeconds = focusedSeconds + elapsedSeconds.coerceAtLeast(0L),
                pausedSeconds = this.pausedSeconds + pausedSeconds.coerceAtLeast(0L)
            )
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> copy(
                breakSeconds = breakSeconds + elapsedSeconds.coerceAtLeast(0L),
                pausedSeconds = this.pausedSeconds + pausedSeconds.coerceAtLeast(0L)
            )
        }

    val durationSeconds: Long
        get() = focusedSeconds + breakSeconds + pausedSeconds
}

internal fun pomodoroPauseStartedAt(autoStart: Boolean, nowMillis: Long): Long =
    if (autoStart) 0L else nowMillis

class TimerEngine private constructor(
    private val context: Context,
    private val repository: StudyRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tickerJob: Job? = null

    private val _uiState = MutableStateFlow(TimerUiState())
    val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

    // Time tracking variables
    private var sessionStartMs: Long = 0L
    private var runningSegmentStartMs: Long = 0L
    private var priorFocusedSeconds: Long = 0L
    private var priorPausedSeconds: Long = 0L
    private var pauseSegmentStartMs: Long = 0L
    private var completedPomodoroTotals = PomodoroSessionTotals()

    companion object {
        @Volatile
        private var INSTANCE: TimerEngine? = null

        fun getInstance(context: Context, repository: StudyRepository): TimerEngine {
            return INSTANCE ?: synchronized(this) {
                val instance = TimerEngine(context.applicationContext, repository)
                INSTANCE = instance
                instance
            }
        }
    }

    fun configureSession(
        mode: SessionMode,
        subject: String,
        selectedChapter: String = "",
        topic: String = "",
        goal: String = "",
        targetId: String = "",
        tags: List<String> = emptyList(),
        durationMinutes: Int = 25,
        pomodoroFocusMin: Int = 25,
        pomodoroShortBreakMin: Int = 5,
        pomodoroLongBreakMin: Int = 15,
        pomodoroRounds: Int = 4,
        autoStartBreaks: Boolean = false,
        autoStartFocus: Boolean = false,
        keepScreenAwake: Boolean = true,
        soundEnabled: Boolean = true,
        vibrationEnabled: Boolean = true
    ) {
        if (_uiState.value.status == TimerStatus.RUNNING || _uiState.value.status == TimerStatus.PAUSED) {
            return // Don't reconfigure while running
        }

        val safeDurationMinutes = durationMinutes.coerceAtLeast(1)
        val safeFocusMinutes = pomodoroFocusMin.coerceAtLeast(1)
        val safeRounds = pomodoroRounds.coerceAtLeast(1)
        val targetSec = when (mode) {
            SessionMode.COUNTDOWN -> safeDurationMinutes * 60L
            SessionMode.STOPWATCH -> 0L
            SessionMode.POMODORO -> safeFocusMinutes * 60L
        }

        _uiState.value = _uiState.value.copy(
            status = TimerStatus.IDLE,
            mode = mode,
            subject = subject,
            selectedChapter = selectedChapter,
            topic = topic,
            goal = goal,
            targetId = targetId,
            tags = tags,
            targetDurationSeconds = targetSec,
            remainingSeconds = targetSec,
            elapsedSeconds = 0L,
            focusedSeconds = 0L,
            pausedSeconds = 0L,
            progress = 0f,
            pomodoroPhase = PomodoroPhase.FOCUS,
            currentRound = 1,
            totalRounds = safeRounds,
            pomodoroFocusMinutes = safeFocusMinutes,
            pomodoroShortBreakMinutes = pomodoroShortBreakMin.coerceAtLeast(0),
            pomodoroLongBreakMinutes = pomodoroLongBreakMin.coerceAtLeast(0),
            autoStartBreaks = autoStartBreaks,
            autoStartFocus = autoStartFocus,
            distractionCount = 0,
            distractions = emptyList(),
            keepScreenAwake = keepScreenAwake,
            soundEnabled = soundEnabled,
            vibrationEnabled = vibrationEnabled,
            showCompletionSheet = false,
            completedSessionData = null
        )
    }

    fun startSession() {
        if (_uiState.value.status == TimerStatus.RUNNING || _uiState.value.status == TimerStatus.PAUSED) {
            return
        }

        val now = System.currentTimeMillis()
        sessionStartMs = now
        runningSegmentStartMs = now
        priorFocusedSeconds = 0L
        priorPausedSeconds = 0L
        pauseSegmentStartMs = 0L
        completedPomodoroTotals = PomodoroSessionTotals()

        _uiState.value = _uiState.value.copy(
            status = TimerStatus.RUNNING,
            sessionStartTime = now,
            showCompletionSheet = false
        )

        startTicker()
        updateServiceState(TimerStatus.RUNNING)
    }

    fun pauseSession() {
        if (_uiState.value.status != TimerStatus.RUNNING) return
        val now = System.currentTimeMillis()
        val currentSegmentSec = (now - runningSegmentStartMs) / 1000
        priorFocusedSeconds += currentSegmentSec
        pauseSegmentStartMs = now

        _uiState.value = _uiState.value.copy(
            status = TimerStatus.PAUSED,
            focusedSeconds = priorFocusedSeconds
        )

        updateServiceState(TimerStatus.PAUSED)
    }

    fun resumeSession() {
        if (_uiState.value.status != TimerStatus.PAUSED) return
        val now = System.currentTimeMillis()
        val pauseSegmentSec = (now - pauseSegmentStartMs) / 1000
        priorPausedSeconds += pauseSegmentSec
        runningSegmentStartMs = now

        _uiState.value = _uiState.value.copy(
            status = TimerStatus.RUNNING,
            pausedSeconds = priorPausedSeconds
        )

        updateServiceState(TimerStatus.RUNNING)
    }

    fun addDistraction(category: String) {
        val current = _uiState.value.distractions.toMutableList()
        current.add(DistractionLog(category))
        _uiState.value = _uiState.value.copy(
            distractionCount = current.size,
            distractions = current
        )
    }

    fun finishSession(isEarly: Boolean = false) {
        if (_uiState.value.status != TimerStatus.RUNNING && _uiState.value.status != TimerStatus.PAUSED) {
            return
        }
        stopTicker()
        val now = System.currentTimeMillis()
        var finalFocused = priorFocusedSeconds
        var finalPaused = priorPausedSeconds

        if (_uiState.value.status == TimerStatus.RUNNING) {
            finalFocused += (now - runningSegmentStartMs) / 1000
        } else if (_uiState.value.status == TimerStatus.PAUSED) {
            finalPaused += (now - pauseSegmentStartMs) / 1000
        }

        val totalDuration = finalFocused + finalPaused
        val currentState = _uiState.value
        val sessionTotals = if (currentState.mode == SessionMode.POMODORO) {
            completedPomodoroTotals.includePhase(currentState.pomodoroPhase, finalFocused, finalPaused)
        } else {
            PomodoroSessionTotals(
                focusedSeconds = finalFocused,
                pausedSeconds = finalPaused
            )
        }
        val sessionDuration = if (currentState.mode == SessionMode.POMODORO) {
            sessionTotals.durationSeconds
        } else {
            totalDuration
        }

        val distractionSummary = currentState.distractions
            .groupBy { it.category }
            .map { "${it.key}: ${it.value.size}" }
            .joinToString(", ")

        val safeFocused = sessionTotals.focusedSeconds.coerceAtLeast(1L)
        val safeDuration = sessionDuration.coerceAtLeast(1L)

        val effectiveTopic = if (currentState.selectedChapter.isNotBlank()) {
            if (currentState.topic.isNotBlank()) "${currentState.selectedChapter} - ${currentState.topic}" else currentState.selectedChapter
        } else currentState.topic

        val sessionEntity = StudySessionEntity(
            startTime = if (sessionStartMs > 0) sessionStartMs else now - (safeDuration * 1000),
            endTime = now,
            durationSeconds = safeDuration,
            actualFocusedSeconds = safeFocused,
            pausedSeconds = sessionTotals.pausedSeconds,
            sessionType = currentState.mode.name,
            subject = currentState.subject,
            topic = effectiveTopic,
            goal = currentState.goal,
            targetId = currentState.targetId,
            tags = currentState.tags.joinToString(","),
            distractionCount = currentState.distractionCount,
            distractionDetails = distractionSummary,
            completionStatus = if (isEarly) "EARLY_FINISH" else "COMPLETED"
        )

        _uiState.value = currentState.copy(
            status = TimerStatus.COMPLETED,
            focusedSeconds = sessionTotals.focusedSeconds,
            pausedSeconds = sessionTotals.pausedSeconds,
            elapsedSeconds = sessionDuration,
            showCompletionSheet = true,
            completedSessionData = sessionEntity
        )

        updateServiceState(TimerStatus.IDLE)
        playCompletionSignals()
    }

    fun finishAndSaveSessionDirectly(isEarly: Boolean = false) {
        if (_uiState.value.status != TimerStatus.RUNNING && _uiState.value.status != TimerStatus.PAUSED) {
            return
        }
        stopTicker()
        val now = System.currentTimeMillis()
        var finalFocused = priorFocusedSeconds
        var finalPaused = priorPausedSeconds

        if (_uiState.value.status == TimerStatus.RUNNING) {
            finalFocused += (now - runningSegmentStartMs) / 1000
        } else if (_uiState.value.status == TimerStatus.PAUSED) {
            finalPaused += (now - pauseSegmentStartMs) / 1000
        }

        val totalDuration = finalFocused + finalPaused
        val currentState = _uiState.value
        val sessionTotals = if (currentState.mode == SessionMode.POMODORO) {
            completedPomodoroTotals.includePhase(currentState.pomodoroPhase, finalFocused, finalPaused)
        } else {
            PomodoroSessionTotals(
                focusedSeconds = finalFocused,
                pausedSeconds = finalPaused
            )
        }
        val sessionDuration = if (currentState.mode == SessionMode.POMODORO) {
            sessionTotals.durationSeconds
        } else {
            totalDuration
        }

        val distractionSummary = currentState.distractions
            .groupBy { it.category }
            .map { "${it.key}: ${it.value.size}" }
            .joinToString(", ")

        val safeFocused = sessionTotals.focusedSeconds.coerceAtLeast(1L)
        val safeDuration = sessionDuration.coerceAtLeast(1L)

        val sessionEntity = StudySessionEntity(
            startTime = if (sessionStartMs > 0) sessionStartMs else now - (safeDuration * 1000),
            endTime = now,
            durationSeconds = safeDuration,
            actualFocusedSeconds = safeFocused,
            pausedSeconds = sessionTotals.pausedSeconds,
            sessionType = currentState.mode.name,
            subject = currentState.subject,
            topic = currentState.topic,
            goal = currentState.goal,
            targetId = currentState.targetId,
            tags = currentState.tags.joinToString(","),
            distractionCount = currentState.distractionCount,
            distractionDetails = distractionSummary,
            completionStatus = if (isEarly) "EARLY_FINISH" else "COMPLETED"
        )

        runBlocking(Dispatchers.IO) {
            if (sessionEntity.topic.contains("Revision", ignoreCase = true) || sessionEntity.sessionType == "REVISION") {
                val chapter = currentState.selectedChapter.ifBlank { sessionEntity.topic.ifBlank { "General Chapter" } }
                saveRevisionRecordDirectly(sessionEntity, chapter)
            } else {
                repository.insertSession(sessionEntity)
            }
        }

        _uiState.value = currentState.copy(
            status = TimerStatus.IDLE,
            focusedSeconds = 0L,
            pausedSeconds = 0L,
            elapsedSeconds = 0L,
            showCompletionSheet = false,
            completedSessionData = null
        )

        updateServiceState(TimerStatus.IDLE)
        playCompletionSignals()
    }

    private fun saveRevisionRecordDirectly(session: StudySessionEntity, chapterName: String) {
        try {
            val rhJson = runBlocking(Dispatchers.IO) { repository.getSetting("revision_history_json", "") }
            val array = if (rhJson.isNotBlank()) org.json.JSONArray(rhJson) else org.json.JSONArray()
            val durMins = (session.actualFocusedSeconds / 60).toInt().coerceAtLeast(1)
            val newObj = org.json.JSONObject().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("subjectName", session.subject.ifBlank { "General" })
                put("chapterName", if (chapterName.isNotBlank()) chapterName else "General Chapter")
                put("durationMinutes", durMins)
                put("startTime", session.startTime)
                put("endTime", session.endTime)
                put("timestamp", session.endTime)
                put("revisionStage", if (session.topic.isNotBlank()) session.topic else "Revision")
            }
            val newArray = org.json.JSONArray()
            newArray.put(newObj)
            for (i in 0 until array.length()) {
                newArray.put(array.get(i))
            }
            runBlocking(Dispatchers.IO) { repository.setSetting("revision_history_json", newArray.toString()) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveCompletedSessionWithFeedback(
        productivityRating: Int,
        mood: String,
        energyLevel: String,
        notes: String,
        location: String,
        targetId: String = ""
    ) {
        val completed = _uiState.value.completedSessionData ?: return
        if (completed.topic.contains("Revision", ignoreCase = true) || completed.sessionType == "REVISION") {
            _uiState.value = _uiState.value.copy(
                showCompletionSheet = false,
                status = TimerStatus.IDLE,
                completedSessionData = null
            )
            return
        }
        scope.launch {
            val updated = completed.copy(
                productivityRating = productivityRating,
                mood = mood,
                energyLevel = energyLevel,
                notes = notes,
                location = location,
                targetId = targetId.ifBlank { completed.targetId },
                updatedTimestamp = System.currentTimeMillis()
            )
            repository.insertSession(updated)
            _uiState.value = _uiState.value.copy(
                showCompletionSheet = false,
                status = TimerStatus.IDLE,
                completedSessionData = null
            )
        }
    }

    fun dismissCompletionSheet() {
        val completed = _uiState.value.completedSessionData
        if (completed != null && !completed.topic.contains("Revision", ignoreCase = true) && completed.sessionType != "REVISION") {
            scope.launch {
                repository.insertSession(completed)
            }
        }
        _uiState.value = _uiState.value.copy(
            showCompletionSheet = false,
            status = TimerStatus.IDLE,
            completedSessionData = null
        )
    }

    fun cancelSession() {
        stopTicker()
        _uiState.value = _uiState.value.copy(
            status = TimerStatus.IDLE,
            focusedSeconds = 0L,
            pausedSeconds = 0L,
            elapsedSeconds = 0L,
            distractionCount = 0,
            distractions = emptyList(),
            showCompletionSheet = false,
            completedSessionData = null
        )
        updateServiceState(TimerStatus.IDLE)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                tick()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun tick() {
        if (_uiState.value.status != TimerStatus.RUNNING) return

        val now = System.currentTimeMillis()
        val currentSegmentSec = (now - runningSegmentStartMs) / 1000
        val currentFocused = priorFocusedSeconds + currentSegmentSec
        val totalElapsed = currentFocused + priorPausedSeconds

        val currentState = _uiState.value

        when (currentState.mode) {
            SessionMode.COUNTDOWN -> {
                val remaining = (currentState.targetDurationSeconds - currentFocused).coerceAtLeast(0L)
                val progress = if (currentState.targetDurationSeconds > 0) {
                    (currentFocused.toFloat() / currentState.targetDurationSeconds).coerceIn(0f, 1f)
                } else 1f

                _uiState.value = currentState.copy(
                    focusedSeconds = currentFocused,
                    elapsedSeconds = totalElapsed,
                    remainingSeconds = remaining,
                    progress = progress
                )

                if (remaining <= 0L) {
                    finishSession(isEarly = false)
                }
            }
            SessionMode.STOPWATCH -> {
                _uiState.value = currentState.copy(
                    focusedSeconds = currentFocused,
                    elapsedSeconds = totalElapsed,
                    progress = 1f
                )
            }
            SessionMode.POMODORO -> {
                val targetPhaseSec = when (currentState.pomodoroPhase) {
                    PomodoroPhase.FOCUS -> currentState.pomodoroFocusMinutes * 60L
                    PomodoroPhase.SHORT_BREAK -> currentState.pomodoroShortBreakMinutes * 60L
                    PomodoroPhase.LONG_BREAK -> currentState.pomodoroLongBreakMinutes * 60L
                }

                val remaining = (targetPhaseSec - currentFocused).coerceAtLeast(0L)
                val progress = if (targetPhaseSec > 0) {
                    (currentFocused.toFloat() / targetPhaseSec).coerceIn(0f, 1f)
                } else 1f

                _uiState.value = currentState.copy(
                    focusedSeconds = currentFocused,
                    elapsedSeconds = totalElapsed,
                    remainingSeconds = remaining,
                    progress = progress
                )

                if (remaining <= 0L) {
                    handlePomodoroPhaseTransition()
                }
            }
        }

        if (_uiState.value.status == TimerStatus.RUNNING) {
            updateServiceState(TimerStatus.RUNNING)
        }
    }

    private fun handlePomodoroPhaseTransition() {
        playCompletionSignals()
        val currentState = _uiState.value
        if (currentState.pomodoroPhase != PomodoroPhase.LONG_BREAK) {
            completedPomodoroTotals = completedPomodoroTotals.includePhase(
                currentState.pomodoroPhase,
                currentState.focusedSeconds,
                currentState.pausedSeconds
            )
        }
        when (currentState.pomodoroPhase) {
            PomodoroPhase.FOCUS -> {
                // Focus ended -> determine if short or long break
                if (currentState.currentRound >= currentState.totalRounds) {
                    // Final round -> Long break or finish
                    _uiState.value = currentState.copy(
                        pomodoroPhase = PomodoroPhase.LONG_BREAK,
                        targetDurationSeconds = currentState.pomodoroLongBreakMinutes * 60L,
                        remainingSeconds = currentState.pomodoroLongBreakMinutes * 60L,
                        progress = 0f
                    )
                } else {
                    // Short break
                    _uiState.value = currentState.copy(
                        pomodoroPhase = PomodoroPhase.SHORT_BREAK,
                        targetDurationSeconds = currentState.pomodoroShortBreakMinutes * 60L,
                        remainingSeconds = currentState.pomodoroShortBreakMinutes * 60L,
                        progress = 0f
                    )
                }
                resetPhaseTimers(currentState.autoStartBreaks)
            }
            PomodoroPhase.SHORT_BREAK -> {
                // Break ended -> next focus round
                val nextRound = currentState.currentRound + 1
                _uiState.value = currentState.copy(
                    pomodoroPhase = PomodoroPhase.FOCUS,
                    currentRound = nextRound,
                    targetDurationSeconds = currentState.pomodoroFocusMinutes * 60L,
                    remainingSeconds = currentState.pomodoroFocusMinutes * 60L,
                    progress = 0f
                )
                resetPhaseTimers(currentState.autoStartFocus)
            }
            PomodoroPhase.LONG_BREAK -> {
                // All rounds finished
                finishSession(isEarly = false)
            }
        }
    }

    private fun resetPhaseTimers(autoStart: Boolean) {
        val now = System.currentTimeMillis()
        runningSegmentStartMs = now
        priorFocusedSeconds = 0L
        priorPausedSeconds = 0L
        pauseSegmentStartMs = pomodoroPauseStartedAt(autoStart, now)

        if (autoStart) {
            _uiState.value = _uiState.value.copy(status = TimerStatus.RUNNING)
            updateServiceState(TimerStatus.RUNNING)
        } else {
            _uiState.value = _uiState.value.copy(status = TimerStatus.PAUSED)
            updateServiceState(TimerStatus.PAUSED)
        }
    }

    private fun playCompletionSignals() {
        try {
            if (_uiState.value.soundEnabled) {
                val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                scope.launch {
                    delay(500L)
                    toneGen.release()
                }
            }
            if (_uiState.value.vibrationEnabled) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vibratorManager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 300, 150, 300), -1)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateServiceState(status: TimerStatus) {
        try {
            when (status) {
                TimerStatus.RUNNING -> StudyTimerService.startOrUpdate(context, _uiState.value)
                TimerStatus.PAUSED -> StudyTimerService.startOrUpdate(context, _uiState.value)
                TimerStatus.IDLE, TimerStatus.COMPLETED -> StudyTimerService.stop(context)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
