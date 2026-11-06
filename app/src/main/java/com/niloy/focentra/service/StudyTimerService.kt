package com.niloy.focentra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.niloy.focentra.MainActivity
import com.niloy.focentra.engine.PomodoroPhase
import com.niloy.focentra.engine.SessionMode
import com.niloy.focentra.engine.TimerStatus
import com.niloy.focentra.engine.TimerUiState

class StudyTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "focentra_study_timer_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PAUSE = "com.niloy.focentra.ACTION_PAUSE"
        const val ACTION_RESUME = "com.niloy.focentra.ACTION_RESUME"
        const val ACTION_FINISH = "com.niloy.focentra.ACTION_FINISH"
        const val ACTION_STOP = "com.niloy.focentra.ACTION_STOP"

        private const val EXTRA_SUBJECT = "extra_subject"
        private const val EXTRA_TOPIC = "extra_topic"
        private const val EXTRA_TIME_TEXT = "extra_time_text"
        private const val EXTRA_IS_PAUSED = "extra_is_paused"
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_REMAINING_SEC = "extra_remaining_sec"
        private const val EXTRA_FOCUSED_SEC = "extra_focused_sec"
        private const val EXTRA_POMO_PHASE = "extra_pomo_phase"
        private const val EXTRA_POMO_ROUND = "extra_pomo_round"
        private const val EXTRA_POMO_TOTAL_ROUNDS = "extra_pomo_total_rounds"

        fun startOrUpdate(context: Context, state: TimerUiState) {
            val intent = Intent(context, StudyTimerService::class.java).apply {
                putExtra(EXTRA_SUBJECT, state.subject)
                putExtra(EXTRA_TOPIC, state.topic)
                putExtra(EXTRA_IS_PAUSED, state.status == TimerStatus.PAUSED)
                putExtra(EXTRA_MODE, state.mode.name)
                putExtra(EXTRA_REMAINING_SEC, state.remainingSeconds)
                putExtra(EXTRA_FOCUSED_SEC, state.focusedSeconds)
                putExtra(EXTRA_POMO_PHASE, state.pomodoroPhase.name)
                putExtra(EXTRA_POMO_ROUND, state.currentRound)
                putExtra(EXTRA_POMO_TOTAL_ROUNDS, state.totalRounds)

                val timeFormatted = when (state.mode) {
                    SessionMode.COUNTDOWN -> {
                        val min = state.remainingSeconds / 60
                        val sec = state.remainingSeconds % 60
                        String.format("%02d:%02d remaining", min, sec)
                    }
                    SessionMode.POMODORO -> {
                        val min = state.remainingSeconds / 60
                        val sec = state.remainingSeconds % 60
                        val phaseLabel = when (state.pomodoroPhase) {
                            PomodoroPhase.FOCUS -> "Focus"
                            PomodoroPhase.SHORT_BREAK -> "Short Break"
                            PomodoroPhase.LONG_BREAK -> "Long Break"
                        }
                        String.format("%02d:%02d • %s (R%d/%d)", min, sec, phaseLabel, state.currentRound, state.totalRounds)
                    }
                    SessionMode.STOPWATCH -> {
                        val hrs = state.focusedSeconds / 3600
                        val min = (state.focusedSeconds % 3600) / 60
                        val sec = state.focusedSeconds % 60
                        if (hrs > 0) String.format("%02d:%02d:%02d focused", hrs, min, sec)
                        else String.format("%02d:%02d focused", min, sec)
                    }
                }
                putExtra(EXTRA_TIME_TEXT, timeFormatted)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StudyTimerService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val action = intent?.action
        if (action != null) {
            handleNotificationAction(action)
            return START_NOT_STICKY
        }

        val subject = intent?.getStringExtra(EXTRA_SUBJECT) ?: "Focus Study"
        val topic = intent?.getStringExtra(EXTRA_TOPIC) ?: ""
        val timeText = intent?.getStringExtra(EXTRA_TIME_TEXT) ?: "Session in progress"
        val isPaused = intent?.getBooleanExtra(EXTRA_IS_PAUSED, false) ?: false
        val modeStr = intent?.getStringExtra(EXTRA_MODE) ?: SessionMode.COUNTDOWN.name
        val remainingSec = intent?.getLongExtra(EXTRA_REMAINING_SEC, 0L) ?: 0L
        val focusedSec = intent?.getLongExtra(EXTRA_FOCUSED_SEC, 0L) ?: 0L

        val notification = buildNotification(
            subject = subject,
            topic = topic,
            timeText = timeText,
            isPaused = isPaused,
            modeStr = modeStr,
            remainingSec = remainingSec,
            focusedSec = focusedSec
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return START_NOT_STICKY
    }

    private fun handleNotificationAction(action: String) {
        val app = applicationContext as? com.niloy.focentra.FocentraApp
        val engine = app?.timerEngine
        when (action) {
            ACTION_PAUSE -> engine?.pauseSession()
            ACTION_RESUME -> engine?.resumeSession()
            ACTION_FINISH -> engine?.finishAndSaveSessionDirectly(isEarly = false)
            ACTION_STOP -> engine?.cancelSession()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        autoFinishAndSaveActiveSession()
        stopSelf()
    }

    override fun onDestroy() {
        autoFinishAndSaveActiveSession()
        super.onDestroy()
    }

    private fun autoFinishAndSaveActiveSession() {
        val app = applicationContext as? com.niloy.focentra.FocentraApp
        val engine = app?.timerEngine
        if (engine != null) {
            val status = engine.uiState.value.status
            if (status == TimerStatus.RUNNING || status == TimerStatus.PAUSED) {
                engine.finishAndSaveSessionDirectly(isEarly = false)
            }
        }
    }

    private fun buildNotification(
        subject: String,
        topic: String,
        timeText: String,
        isPaused: Boolean,
        modeStr: String,
        remainingSec: Long,
        focusedSec: Long
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeAction = if (isPaused) {
            val resumeIntent = Intent(this, StudyTimerService::class.java).apply { action = ACTION_RESUME }
            val resumePending = PendingIntent.getService(this, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_play, "Resume", resumePending).build()
        } else {
            val pauseIntent = Intent(this, StudyTimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(this, 2, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Action.Builder(android.R.drawable.ic_media_pause, "Pause", pausePending).build()
        }

        val finishIntent = Intent(this, StudyTimerService::class.java).apply { action = ACTION_FINISH }
        val finishPending = PendingIntent.getService(this, 3, finishIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val finishAction = NotificationCompat.Action.Builder(android.R.drawable.checkbox_on_background, "Finish", finishPending).build()

        val statusLabel = if (isPaused) "Paused" else "Studying"
        val headerTitle = if (topic.isNotBlank()) "Focentra • $subject ($topic)" else "Focentra • $subject"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(headerTitle)
            .setContentText(timeText)
            .setSubText(statusLabel)
            .setSmallIcon(com.niloy.focentra.R.drawable.ic_notification)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeAction)
            .addAction(finishAction)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // Realtime Android Chronometer support
        if (!isPaused) {
            builder.setUsesChronometer(true)
            builder.setShowWhen(true)
            if (modeStr == SessionMode.COUNTDOWN.name || modeStr == SessionMode.POMODORO.name) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(true)
                }
                builder.setWhen(System.currentTimeMillis() + (remainingSec * 1000L))
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    builder.setChronometerCountDown(false)
                }
                builder.setWhen(System.currentTimeMillis() - (focusedSec * 1000L))
            }
        } else {
            builder.setUsesChronometer(false)
            builder.setShowWhen(false)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Focus Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows realtime study session countdown and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
