package com.niloy.focentra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.data.local.entity.ChapterEntity
import com.niloy.focentra.data.local.entity.PresetEntity
import com.niloy.focentra.data.local.entity.StudySessionEntity
import com.niloy.focentra.engine.SessionMode
import com.niloy.focentra.engine.TimerStatus
import com.niloy.focentra.ui.components.*
import com.niloy.focentra.ui.theme.*
import com.niloy.focentra.viewmodel.MainViewModel
import com.niloy.focentra.viewmodel.NavigationTab
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val timerState by viewModel.timerEngine.uiState.collectAsStateWithLifecycle()
    val brainDumpNotes by viewModel.brainDumpNotes.collectAsStateWithLifecycle()
    var showBrainDumpDialog by remember { mutableStateOf(false) }

    if (showBrainDumpDialog) {
        com.niloy.focentra.ui.components.BrainDumpDialog(
            notes = brainDumpNotes,
            onDismiss = { showBrainDumpDialog = false },
            onAddNote = { text -> viewModel.addBrainDumpNote(text, "General") },
            onToggleDone = { id -> viewModel.toggleBrainDumpDone(id) },
            onDeleteNote = { id -> viewModel.deleteBrainDumpNote(id) },
            onClearCompleted = { viewModel.clearCompletedBrainDumps() }
        )
    }
    val streakInfo by viewModel.streakInfo.collectAsStateWithLifecycle()
    val todayMins by viewModel.todayFocusedMinutes.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val periodStats by viewModel.periodStatistics.collectAsStateWithLifecycle()
    val heatmapData by viewModel.heatmapData.collectAsStateWithLifecycle()
    val examTargets by viewModel.examTargets.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()

    var showAddExamDialog by remember { mutableStateOf(false) }
    var showFocusNowDialog by remember { mutableStateOf(false) }
    var focusNowTarget by remember { mutableStateOf<com.niloy.focentra.viewmodel.ExamTarget?>(null) }
    var targetToEdit by remember { mutableStateOf<com.niloy.focentra.viewmodel.ExamTarget?>(null) }
    var targetToDelete by remember { mutableStateOf<com.niloy.focentra.viewmodel.ExamTarget?>(null) }
    var pendingQuickTimerMode by remember { mutableStateOf<Pair<SessionMode, Int>?>(null) }

    val dailyGoalMins = goals.find { it.periodType == "DAILY" }?.targetMinutes ?: 480
    val progressPct = if (dailyGoalMins > 0) {
        ((todayMins.toFloat() / dailyGoalMins) * 100f).coerceIn(0f, 100f)
    } else 0f

    val todayHrs = todayMins / 60
    val todayRemMins = todayMins % 60
    val goalHrs = dailyGoalMins / 60
    val goalRemMins = dailyGoalMins % 60

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)
    ) {
        // Active Session Banner (if running or paused)
        if (timerState.status == TimerStatus.RUNNING || timerState.status == TimerStatus.PAUSED) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { viewModel.navigateTo(NavigationTab.TIMER) },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (timerState.status == TimerStatus.RUNNING) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                                    )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (timerState.status == TimerStatus.RUNNING) "Active Session in Progress" else "Session Paused",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = timerState.subject + if (timerState.topic.isNotBlank()) " • ${timerState.topic}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Go to timer",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Exam & Target Milestone Countdown Card
        item {
            ExamMilestoneCountdownSection(
                examTargets = examTargets,
                sessions = sessions,
                onAddClick = { showAddExamDialog = true },
                onEditTarget = { target -> targetToEdit = target },
                onStartTimer = { target ->
                    focusNowTarget = target
                    showFocusNowDialog = true
                },
                onDeleteTarget = { id ->
                    targetToDelete = examTargets.find { it.id == id }
                }
            )
        }



        // Today's Focus Time Card (Styled matching Quick Study Modes)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Top Row: Title, Time & Fire Streak Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Today's Focus Time",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format("%02d", todayHrs)}h ${String.format("%02d", todayRemMins)}m",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Fire Streak Pill
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { viewModel.navigateTo(NavigationTab.ACHIEVEMENTS) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = "Streak",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${streakInfo.currentStreak} Days",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Progress Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${progressPct.toInt()}% of ${goalHrs}h ${goalRemMins}m goal",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val remainingMins = (dailyGoalMins - todayMins).coerceAtLeast(0)
                            val remHrs = remainingMins / 60
                            val remMins = remainingMins % 60
                            val leftText = when {
                                remMins == 0L -> "${remHrs}h"
                                remHrs == 0L -> "${remMins}m"
                                else -> "${remHrs}h ${remMins}m"
                            }
                            Text(
                                text = if (remainingMins > 0) "Left: $leftText" else "Goal Achieved!",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Solid Theme Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction = (progressPct / 100f).coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }

        // Quick Action Grid (Countdown, Pomodoro, Stopwatch, Flashcards)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Quick Study Modes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Countdown Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable { pendingQuickTimerMode = Pair(SessionMode.COUNTDOWN, 25) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Timer (25m)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Standard Sprint",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Pomodoro Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable { pendingQuickTimerMode = Pair(SessionMode.POMODORO, 25) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AvTimer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Pomodoro",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "25m / 5m intervals",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Stopwatch Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable { pendingQuickTimerMode = Pair(SessionMode.STOPWATCH, 0) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Stopwatch",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Open focus flow",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Study Presets Card
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(105.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                            .clickable { viewModel.navigateTo(NavigationTab.PRESETS) },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Presets",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Study Routines",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Pomodoro Quick Card & Presets
        if (presets.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Study Presets",
                    actionLabel = "See All (${presets.size})",
                    onActionClick = { viewModel.navigateTo(NavigationTab.PRESETS) }
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(presets) { preset ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                                .clickable { viewModel.startTimerWithPreset(preset) }
                                .widthIn(min = 145.dp),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (preset.type == "POMODORO") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (preset.type == "POMODORO") Icons.Default.AvTimer else Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = if (preset.type == "POMODORO") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = preset.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (preset.type == "POMODORO") "${preset.pomodoroFocusMin}/${preset.pomodoroShortBreakMin}m" else "${preset.durationMinutes}m",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stats Overview Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "This Week",
                    value = "${periodStats.totalFocusedSeconds / 3600}h ${(periodStats.totalFocusedSeconds % 3600) / 60}m",
                    subtitle = "${periodStats.sessionCount} sessions",
                    icon = Icons.Default.DateRange,
                    iconTint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(NavigationTab.ANALYTICS) }
                )
                StatCard(
                    title = "Focus Score",
                    value = "${periodStats.focusScore}/100",
                    subtitle = "Discipline Index",
                    icon = Icons.Default.Psychology,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.navigateTo(NavigationTab.ANALYTICS) }
                )
            }
        }

        // Study Heatmap Preview Card
        item {
            HeatmapGrid(
                cells = heatmapData,
                onCellClick = { viewModel.navigateTo(NavigationTab.CALENDAR) }
            )
        }

        // Daily Focus Affirmation & Study Intent
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Study Wisdom",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "“Small disciplines repeated with consistency every day lead to great achievements gained slowly over time.”",
                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Quick Active Tools Row (Brain Dump & Subjects)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.navigateTo(NavigationTab.SUBJECTS) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Subjects",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Manage Topics",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showBrainDumpDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Brain Dump",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Focus Shield",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Recent Activity Card Container (Clean Minimalism Card)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "See Stats",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.navigateTo(NavigationTab.ANALYTICS) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (sessions.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HourglassEmpty,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No study sessions yet",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Start your first session to begin tracking focus time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { viewModel.navigateTo(NavigationTab.TIMER) },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Timer")
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            sessions.take(4).forEachIndexed { index, session ->
                                val isLast = index == sessions.take(4).size - 1
                                MinimalRecentSessionRow(session = session, showDivider = !isLast)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFocusNowDialog) {
        FocusNowStartDialog(
            subjects = subjects,
            chapters = chapters,
            target = focusNowTarget,
            onDismiss = {
                showFocusNowDialog = false
                focusNowTarget = null
            },
            onStart = { subj, chap, targetId ->
                showFocusNowDialog = false
                viewModel.startTimerSession(
                    mode = SessionMode.STOPWATCH,
                    subject = subj,
                    chapter = chap,
                    goal = focusNowTarget?.title ?: "",
                    targetId = targetId,
                    destination = NavigationTab.TIMER
                )
                focusNowTarget = null
            }
        )
    }

    if (showAddExamDialog) {
        val availableSubjects = remember(subjects) { subjects.map { it.name }.filter { it.isNotBlank() } }
        AddExamTargetDialog(
            availableSubjects = availableSubjects,
            onDismiss = { showAddExamDialog = false },
            onSave = { title, dateEpoch, hours, subj, notes ->
                viewModel.addExamTarget(title, dateEpoch, hours, subj, notes)
                showAddExamDialog = false
            }
        )
    }

    if (targetToEdit != null) {
        val availableSubjects = remember(subjects) { subjects.map { it.name }.filter { it.isNotBlank() } }
        EditExamTargetDialog(
            target = targetToEdit!!,
            availableSubjects = availableSubjects,
            onDismiss = { targetToEdit = null },
            onSave = { updatedTarget ->
                viewModel.editExamTarget(updatedTarget)
                targetToEdit = null
            }
        )
    }

    if (targetToDelete != null) {
        AlertDialog(
            onDismissRequest = { targetToDelete = null },
            title = { Text("Delete Exam Milestone?") },
            text = { Text("Are you sure you want to delete «${targetToDelete!!.title}»? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExamTarget(targetToDelete!!.id)
                        targetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingQuickTimerMode?.let { (mode, defaultMins) ->
        val subjectNames = remember(subjects) {
            val list = mutableListOf<String>()
            subjects.forEach { if (!list.contains(it.name) && it.name != "General Study") list.add(it.name) }
            list
        }
        QuickTimerSubjectDialog(
            subjects = subjectNames,
            chapters = chapters,
            mode = mode,
            viewModel = viewModel,
            onDismiss = { pendingQuickTimerMode = null },
            onStart = { selectedSubj, selectedChap ->
                viewModel.startTimerSession(
                    mode = mode,
                    subject = selectedSubj,
                    chapter = selectedChap,
                    durationMinutes = defaultMins,
                    destination = NavigationTab.TIMER
                )
                pendingQuickTimerMode = null
            }
        )
    }
}

@Composable
fun QuickTimerSubjectDialog(
    subjects: List<String>,
    chapters: List<ChapterEntity>,
    mode: SessionMode,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onStart: (subject: String, chapter: String) -> Unit
) {
    var selectedSubject by remember { mutableStateOf(subjects.firstOrNull() ?: "") }
    var selectedChapter by remember { mutableStateOf("") }
    var customSubject by remember { mutableStateOf("") }
    var warningText by remember { mutableStateOf("") }

    val matchingChapters = remember(chapters, selectedSubject) {
        if (selectedSubject.isBlank()) emptyList()
        else chapters.filter { it.subjectName.equals(selectedSubject, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Start ${mode.name.lowercase().replaceFirstChar { it.uppercase() }} Session",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Select a subject for this session:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(subjects) { subj ->
                        FilterChip(
                            selected = selectedSubject == subj,
                            onClick = {
                                selectedSubject = subj
                                selectedChapter = ""
                                warningText = ""
                            },
                            label = { Text(subj) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (matchingChapters.isNotEmpty()) {
                    Text(
                        text = "Select a chapter:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(matchingChapters) { ch ->
                            val cleanName = ch.chapterName.replace(Regex("(?i)\\bnoun\\b"), "").replace("Noun", "").replace("noun", "").trim()
                            val isSelected = selectedChapter == ch.chapterName
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedChapter = if (isSelected) "" else ch.chapterName
                                    warningText = ""
                                },
                                label = { Text(cleanName) },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = customSubject,
                    onValueChange = {
                        customSubject = it
                        if (it.isNotBlank()) selectedSubject = it
                        warningText = ""
                    },
                    label = { Text("Or enter custom subject") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (warningText.isNotBlank()) {
                    Text(
                        text = warningText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalSubject = customSubject.ifBlank { selectedSubject }
                    if (finalSubject.isBlank() && selectedChapter.isBlank()) {
                        warningText = "Please select both a subject and a chapter."
                    } else if (finalSubject.isBlank()) {
                        warningText = "Please select a subject."
                    } else if (selectedChapter.isBlank()) {
                        warningText = "Please select a chapter."
                    } else {
                        val (isBlocked, blockReason) = viewModel.isTimerBlockedFor(finalSubject, selectedChapter)
                        if (isBlocked) {
                            warningText = blockReason
                        } else {
                            onStart(finalSubject, selectedChapter)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Start Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExamMilestoneCountdownSection(
    examTargets: List<com.niloy.focentra.viewmodel.ExamTarget>,
    sessions: List<StudySessionEntity>,
    onAddClick: () -> Unit,
    onEditTarget: (com.niloy.focentra.viewmodel.ExamTarget) -> Unit,
    onStartTimer: (com.niloy.focentra.viewmodel.ExamTarget) -> Unit,
    onDeleteTarget: (String) -> Unit
) {
    if (examTargets.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAddClick() },
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Flag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Target Exam & Milestone Countdown",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Set an upcoming exam & auto-calculate daily study pacing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onAddClick) {
                    Icon(
                        imageVector = Icons.Default.AddCircleOutline,
                        contentDescription = "Add Exam Target",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Exam & Goal Milestones",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Exam", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            examTargets.forEach { target ->
                ExamMilestoneCard(
                    target = target,
                    sessions = sessions,
                    onStartTimer = { onStartTimer(target) },
                    onEdit = { onEditTarget(target) },
                    onDelete = { onDeleteTarget(target.id) },
                    defaultExpanded = examTargets.size == 1
                )
            }
        }
    }
}

@Composable
fun ExamMilestoneCard(
    target: com.niloy.focentra.viewmodel.ExamTarget,
    sessions: List<StudySessionEntity>,
    onStartTimer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    defaultExpanded: Boolean = false
) {
    var isExpanded by rememberSaveable(target.id) { mutableStateOf(defaultExpanded) }

    val now = System.currentTimeMillis()
    val diffMs = target.targetDateEpochMs - now
    val daysRemaining = if (diffMs > 0) (diffMs / (1000L * 60 * 60 * 24)).toInt() else 0
    val hoursRemaining = if (diffMs > 0) ((diffMs % (1000L * 60 * 60 * 24)) / (1000L * 60 * 60)).toInt() else 0

    val relevantSessions = sessions.filter {
        (it.targetId.isNotBlank() && it.targetId == target.id) ||
            (it.targetId.isBlank() && (target.subject.equals("General", ignoreCase = true) || it.subject.equals(target.subject, ignoreCase = true)))
    }
    val studiedSec = relevantSessions.sumOf { it.actualFocusedSeconds }
    val studiedHours = studiedSec / 3600f
    val progressPct = (studiedHours / target.targetHours.coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)

    val remainingHoursToStudy = (target.targetHours - studiedHours).coerceAtLeast(0f)
    val dailyPace = if (daysRemaining > 0) remainingHoursToStudy / daysRemaining else remainingHoursToStudy
    val targetDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(target.targetDateEpochMs))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AnimatedVisibility(
                visible = !isExpanded,
                enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
            ) {
                // Collapsed View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Target: $targetDateFormat • ${progressPct.toInt()}% done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (daysRemaining <= 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = if (daysRemaining > 0) "$daysRemaining Days Left" else "${hoursRemaining}h Left",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (daysRemaining <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ExpandMore,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(250)),
                exit = shrinkVertically(animationSpec = tween(300, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200))
            ) {
                // Expanded View
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with Icon, Title, Edit, Delete, Chevron Up
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primaryContainer,
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Text(
                                text = target.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Milestone",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Milestone",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(onClick = { isExpanded = false }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.ExpandLess,
                                    contentDescription = "Collapse",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Row with Target Date and Days Left Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Date: $targetDateFormat",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (daysRemaining <= 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                        ) {
                            Text(
                                text = if (daysRemaining > 0) "$daysRemaining Days Left" else "${hoursRemaining}h Left",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = if (daysRemaining <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (target.notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = target.notes,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format(Locale.getDefault(), "Focus Target: %.1fh / %.0fh", studiedHours, target.targetHours),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${progressPct.toInt()}% Completed",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            val animatedSyllabusProgress by androidx.compose.animation.core.animateFloatAsState(
                                targetValue = progressPct / 100f,
                                animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                label = "dash_syllabus_progress"
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                LinearProgressIndicator(
                                    progress = { animatedSyllabusProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                    val indicatorOffset = maxWidth * animatedSyllabusProgress
                                    Box(
                                        modifier = Modifier
                                            .offset(x = (indicatorOffset - 3.dp).coerceAtLeast(0.dp))
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f hrs/day pace", dailyPace),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }

                        Button(
                            onClick = onStartTimer,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Focus Now",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyllabusCompilationReadinessCard(
    target: com.niloy.focentra.viewmodel.ExamTarget,
    chapters: List<ChapterEntity>,
    onUpdateChapter: (ChapterEntity) -> Unit,
    onDeleteChapter: (ChapterEntity) -> Unit
) {
    val targetChapters = remember(chapters, target) {
        chapters.filter {
            it.subjectName.equals(target.subject, ignoreCase = true) || target.subject.equals("General", ignoreCase = true) || target.subject.equals("All Subjects", ignoreCase = true)
        }
    }

    val totalChapters = targetChapters.size
    val finishedChapters = targetChapters.count { it.tapCount >= 3 || it.status == "অধ্যায় Completely শেষ" }
    val progressPct = if (totalChapters > 0) (finishedChapters.toFloat() / totalChapters * 100f).coerceIn(0f, 100f) else 0f

    var isExpanded by remember { mutableStateOf(true) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "সিলেবাস ট্র্যাকার",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (targetChapters.isNotEmpty()) "$totalChapters টি অধ্যায় সংযুক্ত • $finishedChapters টি শেষ" else "কোনো অধ্যায় সংযুক্ত নেই",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            progressPct >= 80f -> MaterialTheme.colorScheme.primaryContainer
                            progressPct >= 40f -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = "${progressPct.toInt()}% শেষ",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            val animatedCardProgressPct by androidx.compose.animation.core.animateFloatAsState(
                targetValue = progressPct / 100f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                label = "dash_card_progress"
            )

            LinearProgressIndicator(
                progress = { animatedCardProgressPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (progressPct >= 80f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            if (targetChapters.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "মোট অধ্যায়: $totalChapters",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "সম্পূর্ণ শেষ: $finishedChapters / $totalChapters",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (isExpanded && targetChapters.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    targetChapters.forEach { chapter ->
                        ChapterSequentialTrackerView(
                            chapter = chapter,
                            onUpdate = onUpdateChapter,
                            onDelete = { onDeleteChapter(chapter) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExamMilestoneDetailDialog(
    target: com.niloy.focentra.viewmodel.ExamTarget,
    sessions: List<StudySessionEntity>,
    onDismiss: () -> Unit,
    onStartTimer: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val diffMs = target.targetDateEpochMs - now
    val daysRemaining = if (diffMs > 0) (diffMs / (1000L * 60 * 60 * 24)).toInt() else 0
    val hoursRemaining = if (diffMs > 0) ((diffMs % (1000L * 60 * 60 * 24)) / (1000L * 60 * 60)).toInt() else 0

    val relevantSessions = sessions.filter {
        target.subject.equals("General", ignoreCase = true) || it.subject.equals(target.subject, ignoreCase = true)
    }
    val studiedSec = relevantSessions.sumOf { it.actualFocusedSeconds }
    val studiedHours = studiedSec / 3600f
    val progressPct = (studiedHours / target.targetHours.coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)

    val remainingHoursToStudy = (target.targetHours - studiedHours).coerceAtLeast(0f)
    val dailyPace = if (daysRemaining > 0) remainingHoursToStudy / daysRemaining else remainingHoursToStudy

    val targetDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(target.targetDateEpochMs))

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(28.dp))
                .animateContentSize(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Clean Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (daysRemaining <= 3) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (daysRemaining > 0) "$daysRemaining Days Left" else "${hoursRemaining}h Left",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (daysRemaining <= 3) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Body Content for Long Details
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Subject / Category Card
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Subject / Category",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = target.subject,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Syllabus & Description / Notes Card
                    if (target.notes.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Syllabus / Description",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = target.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    // Target Date & Progress Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Target Date: $targetDateFormat",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${progressPct.toInt()}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val animatedGoalProgressPct by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = progressPct / 100f,
                            animationSpec = androidx.compose.animation.core.tween(durationMillis = 500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            label = "dash_goal_progress"
                        )

                        LinearProgressIndicator(
                            progress = { animatedGoalProgressPct },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            text = String.format(Locale.getDefault(), "Focused: %.1f hrs / Target: %.0f hrs", studiedHours, target.targetHours),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Daily Pace Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "Daily Pace Needed: %.1f hrs/day", dailyPace),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Buttons cleanly arranged
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Milestone",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Milestone",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close")
                    }

                    Button(
                        onClick = onStartTimer,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Study", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
fun EditExamTargetDialog(
    target: com.niloy.focentra.viewmodel.ExamTarget,
    availableSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (com.niloy.focentra.viewmodel.ExamTarget) -> Unit
) {
    var title by remember { mutableStateOf(target.title) }
    var targetHoursText by remember { mutableStateOf(target.targetHours.toInt().toString()) }
    val now = System.currentTimeMillis()
    val diffMs = (target.targetDateEpochMs - now).coerceAtLeast(0L)
    val initialDays = (diffMs / (1000L * 60 * 60 * 24)).coerceAtLeast(1L).toString()
    var daysUntilExamText by remember { mutableStateOf(initialDays) }
    var notes by remember { mutableStateOf(target.notes) }

    val TITLE_LIMIT = 50
    val NOTES_LIMIT = 200

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Edit Exam / Milestone",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TITLE_LIMIT) },
                    label = { Text("Exam / Goal Title") },
                    supportingText = {
                        Text(
                            text = "${title.length}/$TITLE_LIMIT",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = daysUntilExamText,
                        onValueChange = { newVal -> daysUntilExamText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text("Days Left") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = targetHoursText,
                        onValueChange = { newVal -> targetHoursText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text("Target Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { newVal -> notes = newVal.take(NOTES_LIMIT) },
                    label = { Text("Syllabus / Notes") },
                    supportingText = {
                        Text(
                            text = "${notes.length}/$NOTES_LIMIT",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val days = daysUntilExamText.toLongOrNull() ?: 30L
                        val hours = targetHoursText.toFloatOrNull() ?: 40f
                        val targetEpoch = System.currentTimeMillis() + (days * 86400000L)
                        onSave(
                            target.copy(
                                title = title,
                                targetDateEpochMs = targetEpoch,
                                targetHours = hours,
                                subject = "All Subjects",
                                notes = notes
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddExamTargetDialog(
    availableSubjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, targetDateEpoch: Long, targetHours: Float, subject: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetHoursText by remember { mutableStateOf("40") }
    var daysUntilExamText by remember { mutableStateOf("30") }
    var notes by remember { mutableStateOf("") }

    val TITLE_LIMIT = 50
    val NOTES_LIMIT = 200

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Target Exam / Milestone",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(TITLE_LIMIT) },
                    label = { Text("Exam / Goal Title") },
                    placeholder = { Text("e.g. BCS Preliminary Exam") },
                    supportingText = {
                        Text(
                            text = "${title.length}/$TITLE_LIMIT",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = daysUntilExamText,
                        onValueChange = { newVal -> daysUntilExamText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text("Days Left") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = targetHoursText,
                        onValueChange = { newVal -> targetHoursText = newVal.filter { ch -> ch.isDigit() } },
                        label = { Text("Target Hours") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { newVal -> notes = newVal.take(NOTES_LIMIT) },
                    label = { Text("Syllabus / Notes") },
                    supportingText = {
                        Text(
                            text = "${notes.length}/$NOTES_LIMIT",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val days = daysUntilExamText.toLongOrNull() ?: 30L
                        val hours = targetHoursText.toFloatOrNull() ?: 40f
                        val targetEpoch = System.currentTimeMillis() + (days * 86400000L)
                        onSave(title, targetEpoch, hours, "All Subjects", notes)
                    }
                },
                enabled = title.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set Milestone")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun MinimalRecentSessionRow(
    session: StudySessionEntity,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hrs = session.actualFocusedSeconds / 3600
    val mins = (session.actualFocusedSeconds % 3600) / 60
    val secs = session.actualFocusedSeconds % 60
    val durationText = if (hrs > 0) "${hrs}h ${mins}m" else String.format("%02d:%02d", mins, secs)

    val iconVector = when {
        session.subject.contains("code", ignoreCase = true) || session.subject.contains("prog", ignoreCase = true) -> Icons.Default.Code
        session.subject.contains("math", ignoreCase = true) || session.subject.contains("calc", ignoreCase = true) -> Icons.Default.Calculate
        session.subject.contains("eng", ignoreCase = true) || session.subject.contains("lit", ignoreCase = true) || session.subject.contains("lang", ignoreCase = true) -> Icons.Default.Language
        session.subject.contains("sci", ignoreCase = true) || session.subject.contains("phys", ignoreCase = true) || session.subject.contains("chem", ignoreCase = true) -> Icons.Default.Science
        else -> Icons.Default.MenuBook
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Rounded Avatar Icon Box
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Subject, Topic, Focus Score
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = session.subject,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                val topicText = if (session.topic.isNotBlank()) "Topic: ${session.topic}" else "Standard Session"
                val focusRate = (session.productivityRating * 20).coerceIn(40, 100)
                Text(
                    text = "$topicText • $focusRate% Focus",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp, top = 6.dp),
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            )
        }
    }
}
