package com.niloy.focentra

import com.niloy.focentra.engine.PomodoroPhase
import com.niloy.focentra.engine.PomodoroSessionTotals
import com.niloy.focentra.engine.TimerStatus
import com.niloy.focentra.engine.pomodoroPauseStartedAt
import com.niloy.focentra.engine.timerTimeSnapshot
import com.niloy.focentra.data.local.entity.ChapterEntity
import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.viewmodel.chapterAfterRevision
import com.niloy.focentra.viewmodel.isRevisionTopic
import com.niloy.focentra.viewmodel.revisionCompletionKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerAccountingTest {
    @Test
    fun pomodoroTotalsKeepFocusBreakAndPauseTimeAcrossPhases() {
        val totals = PomodoroSessionTotals()
            .includePhase(PomodoroPhase.FOCUS, elapsedSeconds = 1_500, pausedSeconds = 60)
            .includePhase(PomodoroPhase.SHORT_BREAK, elapsedSeconds = 300, pausedSeconds = 30)
            .includePhase(PomodoroPhase.FOCUS, elapsedSeconds = 1_200, pausedSeconds = 0)

        assertEquals(2_700L, totals.focusedSeconds)
        assertEquals(300L, totals.breakSeconds)
        assertEquals(90L, totals.pausedSeconds)
        assertEquals(3_090L, totals.durationSeconds)
    }

    @Test
    fun pomodoroTotalsIncludeAnEarlyFinishInTheCurrentBreak() {
        val totals = PomodoroSessionTotals()
            .includePhase(PomodoroPhase.FOCUS, elapsedSeconds = 1_500, pausedSeconds = 0)
            .includePhase(PomodoroPhase.SHORT_BREAK, elapsedSeconds = 45, pausedSeconds = 15)

        assertEquals(1_500L, totals.focusedSeconds)
        assertEquals(45L, totals.breakSeconds)
        assertEquals(15L, totals.pausedSeconds)
        assertEquals(1_560L, totals.durationSeconds)
    }

    @Test
    fun manuallyStartedPomodoroPhasesStartPauseTimingAtTheTransition() {
        assertEquals(123_456L, pomodoroPauseStartedAt(autoStart = false, nowMillis = 123_456L))
        assertEquals(0L, pomodoroPauseStartedAt(autoStart = true, nowMillis = 123_456L))
    }

    @Test
    fun pauseAndResumeDoNotCountPausedTimeAsFocusedTime() {
        val paused = timerTimeSnapshot(
            status = TimerStatus.PAUSED,
            priorFocusedSeconds = 20,
            runningSegmentStartMs = 0,
            priorPausedSeconds = 0,
            pauseSegmentStartMs = 20_000,
            nowMillis = 50_000
        )
        val resumed = timerTimeSnapshot(
            status = TimerStatus.RUNNING,
            priorFocusedSeconds = paused.focusedSeconds,
            runningSegmentStartMs = 50_000,
            priorPausedSeconds = paused.pausedSeconds,
            pauseSegmentStartMs = 0,
            nowMillis = 75_000
        )

        assertEquals(20L, paused.focusedSeconds)
        assertEquals(30L, paused.pausedSeconds)
        assertEquals(45L, resumed.focusedSeconds)
        assertEquals(30L, resumed.pausedSeconds)
    }

    @Test
    fun timerUsesElapsedClockTimeAfterLongBackgroundOrScreenOffPeriod() {
        val snapshot = timerTimeSnapshot(
            status = TimerStatus.RUNNING,
            priorFocusedSeconds = 120,
            runningSegmentStartMs = 1_000,
            priorPausedSeconds = 0,
            pauseSegmentStartMs = 0,
            nowMillis = 3_601_000
        )

        assertEquals(3_720L, snapshot.focusedSeconds)
    }

    @Test
    fun onlyRevisionTopicsAreClassifiedAsRevisionSessions() {
        assertTrue(isRevisionTopic("Revision Stopwatch"))
        assertTrue(isRevisionTopic("7-day revision"))
        assertFalse(isRevisionTopic("Calculus"))
        assertFalse(isRevisionTopic(""))
    }

    @Test
    fun revisionCompletionIdentityDoesNotUseTheUnpersistedSessionId() {
        val first = StudySessionEntity(
            startTime = 1_000L,
            endTime = 2_000L,
            durationSeconds = 1L,
            actualFocusedSeconds = 1L,
            pausedSeconds = 0L,
            sessionType = "STOPWATCH",
            subject = "Physics",
            topic = "Revision"
        )
        val second = first.copy(startTime = 3_000L, endTime = 4_000L)

        assertEquals(0L, first.id)
        assertEquals(0L, second.id)
        assertTrue(revisionCompletionKey(first) != revisionCompletionKey(second))
    }

    @Test
    fun completingRevisionDoesNotResetChapterCompletionDate() {
        val chapter = ChapterEntity(
            subjectId = 1L,
            subjectName = "Physics",
            chapterName = "Motion",
            tapCount = 3,
            status = "অধ্যায় Completely শেষ",
            lastStudiedTimestamp = 10_000L,
            completedTimestamp = 5_000L
        )

        val revisedChapter = chapterAfterRevision(chapter, revisedAt = 20_000L)

        assertEquals(20_000L, revisedChapter.lastStudiedTimestamp)
        assertEquals(5_000L, revisedChapter.completedTimestamp)
        assertEquals(chapter.status, revisedChapter.status)
        assertEquals(chapter.tapCount, revisedChapter.tapCount)
    }

    @Test
    fun revisionPreservesLegacyCompletionDateFromLastStudyTimestamp() {
        val chapter = ChapterEntity(
            subjectId = 1L,
            subjectName = "Physics",
            chapterName = "Motion",
            tapCount = 3,
            status = "অধ্যায় Completely শেষ",
            lastStudiedTimestamp = 5_000L,
            completedTimestamp = 0L
        )

        val revisedChapter = chapterAfterRevision(chapter, revisedAt = 20_000L)

        assertEquals(20_000L, revisedChapter.lastStudiedTimestamp)
        assertEquals(5_000L, revisedChapter.completedTimestamp)
    }
}
