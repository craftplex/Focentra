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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
    val elapsedSeconds: Long = 0L,
    val focusedSeconds: Long = 0L,
    val pausedSeconds: Long = 0L,
    val remainingSeconds: Long = 25 * 60L,
    val progress: Float = 0f,
    val pomodoroPhase: PomodoroPhase = PomodoroPhase.FOCUS,
    val currentRound: Int = 1,
    val totalRounds: Int = 4,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val autoStartBreaks: Boolean = false,
    val autoStartFocus: Boolean = false,
    val distractionCount: Int = 0,
    val distractions: List<DistractionLog> = emptyList(),
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

    private var sessionStartMs: Long = 0L
    private var runningSegmentStartMs: Long = 0L
    private var pauseSegmentStartMs: Long = 0L
    private var priorFocusedSeconds: Long = 0L
    private var priorPausedSeconds: Long = 0L
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
            return
        }

        val safeDurationMinutes = durationMinutes.coerceAtLeast(1)
        val safeFocusMinutes = pomodoroFocusMin.coerceAtLeast(1)
        val safeRounds = pomodoroRounds.coerceAtLeast(1)

        val targetSec = when (mode) {
            SessionMode.COUNTDOWN -> safeDurationMinutes * 60L
            SessionMode.STOPWATCH -> 0L
            SessionMode.POMODORO -> safeFocusMinutes * 60L
        }

        resetTrackingState()

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
            pomodoroShortBreakMinutes = pomodoroShortBreakMin.coerceAtLeast(1),
            pomodoroLongBreakMinutes = pomodoroLongBreakMin.coerceAtLeast(1),
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
        pauseSegmentStartMs = 0L
        priorFocusedSeconds = 0L
        priorPausedSeconds = 0L
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
        val segmentSeconds = (now - runningSegmentStartMs) / 1000L
        priorFocusedSeconds += segmentSeconds
        pauseSegmentStartMs = now

        val ui = _uiState.value
        _uiState.value = ui.copy(
            status = TimerStatus.PAUSED,
            focusedSeconds = priorFocusedSeconds,
            elapsedSeconds = priorFocusedSeconds + priorPausedSeconds,
            remainingSeconds = computeRemainingSeconds(ui, priorFocusedSeconds, priorPausedSeconds),
            progress = computeProgress(ui, priorFocusedSeconds, priorPausedSeconds)
        )

        updateServiceState(TimerStatus.PAUSED)
    }

    fun resumeSession() {
        if (_uiState.value.status != TimerStatus.PAUSED) return

        val now = System.currentTimeMillis()
        if (pauseSegmentStartMs > 0L) {
            priorPausedSeconds += (now - pauseSegmentStartMs) / 1000L
        }
        runningSegmentStartMs = now
        pauseSegmentStartMs = 0L

        val ui = _uiState.value
        _uiState.value = ui.copy(
            status = TimerStatus.RUNNING,
            pausedSeconds = priorPausedSeconds,
            elapsedSeconds = priorFocusedSeconds + priorPausedSeconds,
            remainingSeconds = computeRemainingSeconds(ui, priorFocusedSeconds, priorPausedSeconds),
            progress = computeProgress(ui, priorFocusedSeconds, priorPausedSeconds)
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

        val currentState = _uiState.value
        val finalFocused = currentFocusedSeconds()
        val finalPaused = currentPausedSeconds()

        val totals = if (currentState.mode == SessionMode.POMODORO) {
            completedPomodoroTotals.includePhase(currentState.pomodoroPhase, finalFocused, finalPaused)
        } else {
            PomodoroSessionTotals(focusedSeconds = finalFocused, pausedSeconds = finalPaused)
        }

        val totalDuration = if (currentState.mode == SessionMode.POMODORO) {
            totals.durationSeconds
        } else {
            finalFocused + finalPaused
        }

        val distractionSummary = currentState.distractions
            .groupBy { it.category }
            .map { "${it.key}: ${it.value.size}" }
            .joinToString(", ")

        val safeFocused = totals.focusedSeconds.coerceAtLeast(1L)
        val safeDuration = totalDuration.coerceAtLeast(1L)

        val sessionEntity = StudySessionEntity(
            startTime = if (sessionStartMs > 0L) sessionStartMs else System.currentTimeMillis() - (safeDuration * 1000L),
            endTime = System.currentTimeMillis(),
            durationSeconds = safeDuration,
            actualFocusedSeconds = safeFocused,
            pausedSeconds = totals.pausedSeconds,
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

        _uiState.value = currentState.copy(
            status = TimerStatus.COMPLETED,
            focusedSeconds = totals.focusedSeconds,
            pausedSeconds = totals.pausedSeconds,
            elapsedSeconds = totalDuration,
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

        val currentState = _uiState.value
        val finalFocused = currentFocusedSeconds()
        val finalPaused = currentPausedSeconds()
        val totals = if (currentState.mode == SessionMode.POMODORO) {
            completedPomodoroTotals.includePhase(currentState.pomodoroPhase, finalFocused, finalPaused)
        } else {
            PomodoroSessionTotals(focusedSeconds = finalFocused, pausedSeconds = finalPaused)
        }

        val totalDuration = if (currentState.mode == SessionMode.POMODORO) {
            totals.durationSeconds
        } else {
            finalFocused + finalPaused
        }

        val distractionSummary = currentState.distractions
            .groupBy { it.category }
            .map { "${it.key}: ${it.value.size}" }
            .joinToString(", ")

        val safeFocused = totals.focusedSeconds.coerceAtLeast(1L)
        val safeDuration = totalDuration.coerceAtLeast(1L)

        val sessionEntity = StudySessionEntity(
            startTime = if (sessionStartMs > 0L) sessionStartMs else System.currentTimeMillis() - (safeDuration * 1000L),
            endTime = System.currentTimeMillis(),
            durationSeconds = safeDuration,
            actualFocusedSeconds = safeFocused,
            pausedSeconds = totals.pausedSeconds,
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
            if (sessionEntity.topic.contains("Revision", ignoreCase = true) || sessionEntity.sessionType.equals("REVISION", ignoreCase = true)) {
                saveRevisionRecordDirectly(sessionEntity, currentState.selectedChapter)
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
            val revisionHistoryJson = runBlocking(Dispatchers.IO) {
                repository.getSetting("revision_history_json", "")
            }

            val arr = if (revisionHistoryJson.isNotBlank()) {
                org.json.JSONArray(revisionHistoryJson)
            } else {
                org.json.JSONArray()
            }

            val durMins = (session.actualFocusedSeconds / 60).toInt().coerceAtLeast(1)
            val obj = org.json.JSONObject().apply {
                put("id", java.util.UUID.randomUUID().toString())
                put("subjectName", session.subject.ifBlank { "General" })
                put("chapterName", if (chapterName.isNotBlank()) chapterName else "General Chapter")
                put("durationMinutes", durMins)
                put("startTime", session.startTime)
                put("endTime", session.endTime)
                put("timestamp", session.endTime)
                put("revisionStage", if (session.topic.isNotBlank()) session.topic else "Revision")
            }

            val updated = org.json.JSONArray()
            updated.put(obj)
            for (i in 0 until arr.length()) {
                updated.put(arr.get(i))
            }

            runBlocking(Dispatchers.IO) {
                repository.setSetting("revision_history_json", updated.toString())
            }
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

        if (completed.topic.contains("Revision", ignoreCase = true) || completed.sessionType.equals("REVISION", ignoreCase = true)) {
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
        val completed = _uiState.value.completedSessionData ?: run {
            _uiState.value = _uiState.value.copy(
                showCompletionSheet = false,
                status = TimerStatus.IDLE,
                completedSessionData = null
            )
            return
        }

        val isRevisionLike = completed.topic.contains("Revision", ignoreCase = true) ||
            completed.sessionType.equals("REVISION", ignoreCase = true)

        if (!isRevisionLike) {
            // Do NOT auto-save on dismiss.
            // Let user explicitly choose Save/Cancel.
            // This avoids duplicate insertion.
        }

        _uiState.value = _uiState.value.copy(
            showCompletionSheet = false,
            status = TimerStatus.IDLE,
            completedSessionData = null
        )
    }

    fun cancelSession() {
        stopTicker()
        resetTrackingState()
        _uiState.value = _uiState.value.copy(
            status = TimerStatus.IDLE,
            focusedSeconds = 0L,
            pausedSeconds = 0L,
            elapsedSeconds = 0L,
            remainingSeconds = if (_uiState.value.mode == SessionMode.STOPWATCH) 0L else _uiState.value.targetDurationSeconds,
            distractionCount = 0,
            distractions = emptyList(),
            showCompletionSheet = false,
            completedSessionData = null
        )
        updateServiceState(TimerStatus.IDLE)
    }

    private fun resetTrackingState() {
        sessionStartMs = 0L
        runningSegmentStartMs = 0L
        pauseSegmentStartMs = 0L
        priorFocusedSeconds = 0L
        priorPausedSeconds = 0L
        completedPomodoroTotals = PomodoroSessionTotals()
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
        val currentFocused = currentFocusedSeconds(now)
        val totalElapsed = currentFocused + priorPausedSeconds

        val currentState = _uiState.value

        when (currentState.mode) {
            SessionMode.COUNTDOWN -> {
                val remaining = (currentState.targetDurationSeconds - currentFocused).coerceAtLeast(0L)
                val progress = if (currentState.targetDurationSeconds > 0L) {
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
                    remainingSeconds = 0L,
                    progress = 1f
                )
            }

            SessionMode.POMODORO -> {
                val phaseTarget = when (currentState.pomodoroPhase) {
                    PomodoroPhase.FOCUS -> currentState.pomodoroFocusMinutes * 60L
                    PomodoroPhase.SHORT_BREAK -> currentState.pomodoroShortBreakMinutes * 60L
                    PomodoroPhase.LONG_BREAK -> currentState.pomodoroLongBreakMinutes * 60L
                }

                val remaining = (phaseTarget - currentFocused).coerceAtLeast(0L)
                val progress = if (phaseTarget > 0L) {
                    (currentFocused.toFloat() / phaseTarget).coerceIn(0f, 1f)
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
    }

    private fun handlePomodoroPhaseTransition() {
        val currentState = _uiState.value
        val phaseFocusedSeconds = currentFocusedSeconds()
        val phasePausedSeconds = currentPausedSeconds()

        completedPomodoroTotals = completedPomodoroTotals.includePhase(
            currentState.pomodoroPhase,
            phaseFocusedSeconds,
            phasePausedSeconds
        )

        when (currentState.pomodoroPhase) {
            PomodoroPhase.FOCUS -> {
                val nextPhase: PomodoroPhase
                val nextTargetSec: Long
                val nextRound: Int

                if (currentState.currentRound >= currentState.totalRounds) {
                    nextPhase = PomodoroPhase.LONG_BREAK
                    nextTargetSec = currentState.pomodoroLongBreakMinutes * 60L
                    nextRound = currentState.currentRound
                } else {
                    nextPhase = PomodoroPhase.SHORT_BREAK
                    nextTargetSec = currentState.pomodoroShortBreakMinutes * 60L
                    nextRound = currentState.currentRound
                }

                _uiState.value = currentState.copy(
                    pomodoroPhase = nextPhase,
                    currentRound = nextRound,
                    targetDurationSeconds = nextTargetSec,
                    remainingSeconds = nextTargetSec,
                    progress = 0f,
                    focusedSeconds = 0L,
                    elapsedSeconds = 0L
                )

                resetPhaseTimers(currentState.autoStartBreaks)
            }

            PomodoroPhase.SHORT_BREAK -> {
                val nextRound = currentState.currentRound + 1
                val focusTargetSec = currentState.pomodoroFocusMinutes * 60L

                _uiState.value = currentState.copy(
                    pomodoroPhase = PomodoroPhase.FOCUS,
                    currentRound = nextRound,
                    targetDurationSeconds = focusTargetSec,
                    remainingSeconds = focusTargetSec,
                    progress = 0f,
                    focusedSeconds = 0L,
                    elapsedSeconds = 0L
                )

                resetPhaseTimers(currentState.autoStartFocus)
            }

            PomodoroPhase.LONG_BREAK -> {
                finishSession(isEarly = false)
            }
        }
    }

    private fun resetPhaseTimers(autoStart: Boolean) {
        val now = System.currentTimeMillis()
        runningSegmentStartMs = now
        pauseSegmentStartMs = if (autoStart) 0L else now
        priorFocusedSeconds = 0L
        priorPausedSeconds = 0L

        val ui = _uiState.value
        _uiState.value = ui.copy(
            status = if (autoStart) TimerStatus.RUNNING else TimerStatus.PAUSED,
            focusedSeconds = 0L,
            pausedSeconds = 0L,
            elapsedSeconds = 0L,
            remainingSeconds = ui.targetDurationSeconds,
            progress = 0f
        )

        updateServiceState(_uiState.value.status)
    }

    private fun currentFocusedSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        val segmentSeconds = if (_uiState.value.status == TimerStatus.RUNNING) {
            (nowMillis - runningSegmentStartMs) / 1000L
        } else {
            0L
        }
        return priorFocusedSeconds + segmentSeconds
    }

    private fun currentPausedSeconds(): Long {
        return priorPausedSeconds + if (_uiState.value.status == TimerStatus.PAUSED && pauseSegmentStartMs > 0L) {
            (System.currentTimeMillis() - pauseSegmentStartMs) / 1000L
        } else {
            0L
        }
    }

    private fun computeRemainingSeconds(
        ui: TimerUiState,
        focusedSeconds: Long,
        pausedSeconds: Long
    ): Long {
        return when (ui.mode) {
            SessionMode.COUNTDOWN -> {
                (ui.targetDurationSeconds - focusedSeconds).coerceAtLeast(0L)
            }
            SessionMode.STOPWATCH -> {
                0L
            }
            SessionMode.POMODORO -> {
                val phaseTarget = when (ui.pomodoroPhase) {
                    PomodoroPhase.FOCUS -> ui.pomodoroFocusMinutes * 60L
                    PomodoroPhase.SHORT_BREAK -> ui.pomodoroShortBreakMinutes * 60L
                    PomodoroPhase.LONG_BREAK -> ui.pomodoroLongBreakMinutes * 60L
                }
                (phaseTarget - focusedSeconds).coerceAtLeast(0L)
            }
        }
    }

    private fun computeProgress(
        ui: TimerUiState,
        focusedSeconds: Long,
        pausedSeconds: Long
    ): Float {
        return when (ui.mode) {
            SessionMode.COUNTDOWN -> {
                if (ui.targetDurationSeconds > 0L) {
                    (focusedSeconds.toFloat() / ui.targetDurationSeconds).coerceIn(0f, 1f)
                } else 1f
            }
            SessionMode.STOPWATCH -> 1f
            SessionMode.POMODORO -> {
                val phaseTarget = when (ui.pomodoroPhase) {
                    PomodoroPhase.FOCUS -> ui.pomodoroFocusMinutes * 60L
                    PomodoroPhase.SHORT_BREAK -> ui.pomodoroShortBreakMinutes * 60L
                    PomodoroPhase.LONG_BREAK -> ui.pomodoroLongBreakMinutes * 60L
                }
                if (phaseTarget > 0L) {
                    (focusedSeconds.toFloat() / phaseTarget).coerceIn(0f, 1f)
                } else 1f
            }
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
