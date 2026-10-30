package com.niloy.focentra.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.engine.AmbientSoundEngine
import com.niloy.focentra.engine.AmbientSoundType
import com.niloy.focentra.engine.SessionMode
import com.niloy.focentra.engine.TimerStatus
import com.niloy.focentra.ui.components.*
import com.niloy.focentra.viewmodel.MainViewModel
import com.niloy.focentra.viewmodel.NotificationType

@Composable
fun TimerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val timerState by viewModel.timerEngine.uiState.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val examTargets by viewModel.examTargets.collectAsStateWithLifecycle()

    var selectedMode by remember { mutableStateOf(SessionMode.COUNTDOWN) }
    var selectedSubject by remember { mutableStateOf("Mathematics") }
    var selectedChapter by remember { mutableStateOf("") }
    var topicText by remember { mutableStateOf("") }
    var goalText by remember { mutableStateOf("") }
    var selectedDurationMinutes by remember { mutableIntStateOf(25) }

    // Pomodoro specific setup
    var pomodoroFocusMin by remember { mutableIntStateOf(25) }
    var pomodoroShortBreakMin by remember { mutableIntStateOf(5) }
    var pomodoroLongBreakMin by remember { mutableIntStateOf(15) }
    var pomodoroRounds by remember { mutableIntStateOf(4) }
    var autoStartBreaks by remember { mutableStateOf(false) }

    var showDistractionDialog by remember { mutableStateOf(false) }
    var showBrainDumpDialog by remember { mutableStateOf(false) }
    var showManualDataInputDialog by remember { mutableStateOf(false) }
    var showCancelConfirmDialog by remember { mutableStateOf(false) }
    var warningTitle by remember { mutableStateOf("") }
    var warningMessage by remember { mutableStateOf("") }
    var showSelectionWarningDialog by remember { mutableStateOf(false) }
    val brainDumpNotes by viewModel.brainDumpNotes.collectAsStateWithLifecycle()

    // Sync subject default if subjects loaded
    LaunchedEffect(subjects) {
        if (subjects.isNotEmpty() && !subjects.any { it.name == selectedSubject }) {
            selectedSubject = subjects.first().name
        }
    }

    if (timerState.showCompletionSheet && timerState.completedSessionData != null) {
        val completed = timerState.completedSessionData
        if (completed != null) {
            SessionCompletionDialog(
                session = completed,
                examTargets = examTargets,
                onDismiss = { viewModel.timerEngine.dismissCompletionSheet() },
                onSave = { rating, mood, energy, notes, loc, targetId ->
                    viewModel.timerEngine.saveCompletedSessionWithFeedback(rating, mood, energy, notes, loc, targetId)
                }
            )
        }
    }

    if (showDistractionDialog) {
        DistractionLoggerDialog(
            onDismiss = { showDistractionDialog = false },
            onLogCategory = { cat ->
                viewModel.timerEngine.addDistraction(cat)
            }
        )
    }

    if (showCancelConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmDialog = false },
            title = {
                Text(
                    text = "Cancel Active Session?",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to stop and cancel this study session? Progress recorded so far won't be saved as a full session.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmDialog = false
                        viewModel.timerEngine.cancelSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirmDialog = false }) {
                    Text("Keep Studying")
                }
            }
        )
    }

    if (showSelectionWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSelectionWarningDialog = false },
            title = {
                Text(
                    text = warningTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = warningMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSelectionWarningDialog = false },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK")
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showBrainDumpDialog) {
        BrainDumpDialog(
            notes = brainDumpNotes,
            onDismiss = { showBrainDumpDialog = false },
            onAddNote = { text -> viewModel.addBrainDumpNote(text, selectedSubject) },
            onToggleDone = { id -> viewModel.toggleBrainDumpDone(id) },
            onDeleteNote = { id -> viewModel.deleteBrainDumpNote(id) },
            onClearCompleted = { viewModel.clearCompletedBrainDumps() }
        )
    }

    if (showManualDataInputDialog) {
        com.niloy.focentra.ui.components.ManualStudyDataDialog(
            subjects = subjects.map { it.name },
            examTargets = examTargets,
            onDismiss = { showManualDataInputDialog = false },
            onSave = { subject, topic, durMin, category, notes, rating, targetId ->
                viewModel.addManualSession(
                    subject = subject,
                    topic = topic,
                    durationMinutes = durMin,
                    category = category,
                    notes = notes,
                    rating = rating,
                    targetId = targetId
                )
            }
        )
    }

    // Lightweight Breathing Gradient Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "TimerBreathingGradient")
    val animAlpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = timerState.status == TimerStatus.RUNNING || timerState.status == TimerStatus.PAUSED,
            transitionSpec = {
                val enteringSession = targetState
                if (enteringSession) {
                    (fadeIn(tween(260, easing = FastOutSlowInEasing)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 12 }) togetherWith
                        (fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it / 12 })
                } else {
                    (fadeIn(tween(260, easing = FastOutSlowInEasing)) + slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { -it / 12 }) togetherWith
                        (fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 12 })
                }
            },
            label = "timer_session_transition"
        ) { isSessionActive ->
        if (isSessionActive) {
            // Active Timer View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                TimerProgressRing(
                    state = timerState,
                    onPauseClick = { viewModel.timerEngine.pauseSession() },
                    onResumeClick = { viewModel.timerEngine.resumeSession() },
                    onFinishClick = { viewModel.timerEngine.finishSession(isEarly = true) },
                    onCancelClick = { showCancelConfirmDialog = true },
                    onLogDistractionClick = { showDistractionDialog = true },
                    onToggleFullScreen = { viewModel.setFullScreenFocus(true) }
                )
            }
        } else {
    // Check for Overdue Revision Blocker
    val activeNotifications by viewModel.activeNotifications.collectAsStateWithLifecycle()
    val dbRevisionSkips by viewModel.dbRevisionSkips.collectAsStateWithLifecycle()
    val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()

    val overdueRevision = remember(activeNotifications, dbRevisionSkips, revisionHistory) {
        activeNotifications.firstOrNull { notif ->
            if (notif.type != NotificationType.REVISION_DUE) return@firstOrNull false
            val skips = dbRevisionSkips.filter {
                it.subjectName.equals(notif.subjectName, ignoreCase = true) &&
                it.chapterName.equals(notif.chapterName, ignoreCase = true)
            }
            val skipCount = skips.size
            val hasActiveUnexpiredSkip = skipCount < 5 && skips.any { it.scheduledRecapTimestamp > now }
            if (hasActiveUnexpiredSkip) return@firstOrNull false

            val requiredMins = if (skipCount >= 5) 600 else 60
            val completedMins = revisionHistory.filter {
                it.subjectName.equals(notif.subjectName, ignoreCase = true) &&
                it.chapterName.equals(notif.chapterName, ignoreCase = true) &&
                it.timestamp >= notif.timestamp
            }.sumOf { it.durationMinutes }

            completedMins < requiredMins
        }
    }

    var showDismissDialog by remember { mutableStateOf(false) }

    if (showDismissDialog && overdueRevision != null) {
        val currentSkips = dbRevisionSkips.count {
            it.subjectName.equals(overdueRevision.subjectName, ignoreCase = true) &&
            it.chapterName.equals(overdueRevision.chapterName, ignoreCase = true)
        }
        SkipRevisionDialog(
            subjectName = overdueRevision.subjectName,
            chapterName = overdueRevision.chapterName,
            currentSkipCount = currentSkips,
            onDismiss = { showDismissDialog = false },
            onConfirmSkip = { reason, recapTs ->
                viewModel.recordRevisionSkip(
                    overdueRevision.subjectName,
                    overdueRevision.chapterName,
                    reason,
                    recapTs
                ) { success, msg ->
                    if (success) {
                        showDismissDialog = false
                    } else {
                        warningTitle = "Dismiss Failed"
                        warningMessage = msg
                        showSelectionWarningDialog = true
                    }
                }
            }
        )
    }

    if (overdueRevision != null) {
        val currentSkips = dbRevisionSkips.count {
            it.subjectName.equals(overdueRevision.subjectName, ignoreCase = true) &&
            it.chapterName.equals(overdueRevision.chapterName, ignoreCase = true)
        }
        // Blank Screen View with 2 Buttons: Revision and Dismiss
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090A10))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )

                    Text(
                        text = "রিভিশন দেওয়া আবশ্যক",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "«${overdueRevision.chapterName}» (${overdueRevision.subjectName})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Text(
                        text = if (currentSkips >= 5) {
                            "আপনি এই অধ্যায়টি সর্বোচ্চ ৫ বার Dismiss করেছেন! অন্য কোনো সাবজেক্ট পড়ার আগে এই অধ্যায় ১০ ঘণ্টা রিভিশন করা বাধ্যতামূলক।"
                        } else {
                            "Ebbinghaus Forgetting Curve অনুযায়ী এই অধ্যায়ের রিভিশন দেওয়া বাকি আছে। রিভিশন না দিলে অন্য কোনো সাবজেক্টের টাইমার অন করা যাবে না।"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (currentSkips >= 5) {
                                    warningTitle = "Dismiss Limit Reached"
                                    warningMessage = "আপনি ইতোমধ্যে ৫ বার Dismiss করেছেন। এখন এই অধ্যায় ১০ ঘণ্টা রিভিশন পড়তে হবে।"
                                    showSelectionWarningDialog = true
                                } else {
                                    showDismissDialog = true
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Dismiss", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }

                        Button(
                            onClick = {
                                viewModel.timerEngine.configureSession(
                                    mode = SessionMode.STOPWATCH,
                                    subject = overdueRevision.subjectName,
                                    selectedChapter = overdueRevision.chapterName,
                                    topic = "Mandatory Revision"
                                )
                                viewModel.timerEngine.startSession()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Revision", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    } else {
            // Setup & Configuration View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
        // Informational Scheduled Revision Banner (If any future revision is scheduled)
        val activeSkips = dbRevisionSkips.filter { it.scheduledRecapTimestamp > now }
        if (activeSkips.isNotEmpty()) {
            val nextSkip = activeSkips.minByOrNull { it.scheduledRecapTimestamp }!!
            val dateFormatted = remember(nextSkip.scheduledRecapTimestamp) {
                java.text.SimpleDateFormat("MMM d, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(nextSkip.scheduledRecapTimestamp))
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "আপনার «${nextSkip.chapterName}» (${nextSkip.subjectName}) অধ্যায়ের রিভিশন $dateFormatted তারিখে স্কেজুল করা আছে।",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
                Spacer(modifier = Modifier.height(4.dp))

                // Mode Selector Tabs
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedMode == SessionMode.COUNTDOWN,
                        onClick = { selectedMode = SessionMode.COUNTDOWN },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.HourglassBottom,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("Timer")
                    }

                    SegmentedButton(
                        selected = selectedMode == SessionMode.POMODORO,
                        onClick = { selectedMode = SessionMode.POMODORO },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.AvTimer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("Pomodoro")
                    }

                    SegmentedButton(
                        selected = selectedMode == SessionMode.STOPWATCH,
                        onClick = { selectedMode = SessionMode.STOPWATCH },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text("Stopwatch")
                    }
                }

                // Subject Picker
                Column {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(subjects) { sub ->
                            val isSelected = sub.name == selectedSubject
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedSubject = sub.name
                                    selectedChapter = ""
                                },
                                label = { Text(sub.name) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(parseColorHex(sub.colorHex))
                                    )
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                // Chapter Picker (if chapters exist for selected subject)
                val matchingChapters = remember(chapters, selectedSubject) {
                    chapters.filter { it.subjectName.equals(selectedSubject, ignoreCase = true) }
                }

                if (matchingChapters.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Chapter",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(matchingChapters) { ch ->
                                val cleanName = ch.chapterName.replace(Regex("(?i)\\bnoun\\b"), "").replace("Noun", "").replace("noun", "").trim()
                                val isSelected = ch.chapterName == selectedChapter
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedChapter = if (isSelected) "" else ch.chapterName },
                                    label = { Text(cleanName) },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // Topic & Goal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = topicText,
                        onValueChange = { topicText = it },
                        label = { Text("Topic (Optional)") },
                        placeholder = { Text("e.g. Calculus") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { goalText = it },
                        label = { Text("Session Goal") },
                        placeholder = { Text("e.g. 10 problems") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )
                }

                // Mode-specific configuration
                AnimatedContent(
                    targetState = selectedMode,
                    transitionSpec = {
                        val isForward = targetState.ordinal > initialState.ordinal
                        (
                            fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(
                                    animationSpec = tween(320, easing = FastOutSlowInEasing)
                                ) { width -> if (isForward) width / 12 else -width / 12 }
                        ) togetherWith (
                            fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                slideOutHorizontally(
                                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                                ) { width -> if (isForward) -width / 12 else width / 12 }
                        )
                    },
                    label = "timer_mode_transition"
                ) { mode ->
                    when (mode) {
                        SessionMode.COUNTDOWN -> {
                            Column {
                                Text(
                                    text = "Duration",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val durationOptions = listOf(5, 10, 15, 25, 30, 45, 60, 90, 120)
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(durationOptions) { mins ->
                                        val isSelected = selectedDurationMinutes == mins
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { selectedDurationMinutes = mins },
                                            label = { Text("${mins}m") },
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                        SessionMode.POMODORO -> {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Pomodoro Intervals",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Focus Duration", style = MaterialTheme.typography.bodyMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { if (pomodoroFocusMin > 5) pomodoroFocusMin -= 5 }) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                                            }
                                            Text("${pomodoroFocusMin}m", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                            IconButton(onClick = { if (pomodoroFocusMin < 120) pomodoroFocusMin += 5 }) {
                                                Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Short Break", style = MaterialTheme.typography.bodyMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { if (pomodoroShortBreakMin > 1) pomodoroShortBreakMin -= 1 }) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                                            }
                                            Text("${pomodoroShortBreakMin}m", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                            IconButton(onClick = { if (pomodoroShortBreakMin < 30) pomodoroShortBreakMin += 1 }) {
                                                Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total Rounds", style = MaterialTheme.typography.bodyMedium)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { if (pomodoroRounds > 1) pomodoroRounds -= 1 }) {
                                                Icon(Icons.Default.RemoveCircleOutline, contentDescription = null)
                                            }
                                            Text("$pomodoroRounds", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                                            IconButton(onClick = { if (pomodoroRounds < 12) pomodoroRounds += 1 }) {
                                                Icon(Icons.Default.AddCircleOutline, contentDescription = null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SessionMode.STOPWATCH -> {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Stopwatch counts open-ended study time. You can pause or finish anytime.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Presets quick picks
                if (presets.isNotEmpty()) {
                    Column {
                        Text(
                            text = "Preset Templates",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets) { preset ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.clickable {
                                        viewModel.startTimerWithPreset(preset)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.BookmarkBorder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = preset.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Offline Focus Ambient Sound Card
                val ambientEngine = remember { AmbientSoundEngine.getInstance() }
                val activeAmbientSound by ambientEngine.currentSound.collectAsStateWithLifecycle()
                val ambientVolume by ambientEngine.volume.collectAsStateWithLifecycle()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Headphones,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Focus Ambient Sound",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (activeAmbientSound != AmbientSoundType.NONE) {
                                Text(
                                    text = "Playing",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(AmbientSoundType.values()) { snd ->
                                val isSelected = activeAmbientSound == snd
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { ambientEngine.setSound(snd) },
                                    label = { Text(snd.title) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }

                        if (activeAmbientSound != AmbientSoundType.NONE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Slider(
                                    value = ambientVolume,
                                    onValueChange = { ambientEngine.setVolume(it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Quick Study Tools (Brain Dump & Manual Input)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showBrainDumpDialog = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Brain Dump (${brainDumpNotes.size})", maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = { showManualDataInputDialog = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Study Data Input", maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Big Start Button
                Button(
                    onClick = {
                        if (selectedSubject.isBlank() && selectedChapter.isBlank()) {
                            warningTitle = "Subject & Chapter Required"
                            warningMessage = "Please select both a subject and a chapter before starting your focus session."
                            showSelectionWarningDialog = true
                        } else if (selectedChapter.isBlank()) {
                            warningTitle = "Chapter Selection Required"
                            warningMessage = "Please select a chapter before starting your focus session."
                            showSelectionWarningDialog = true
                        } else {
                            val (isBlocked, blockReason) = viewModel.isTimerBlockedFor(selectedSubject, selectedChapter)
                            if (isBlocked) {
                                warningTitle = "Mandatory Revision Required"
                                warningMessage = blockReason
                                showSelectionWarningDialog = true
                            } else {
                                viewModel.timerEngine.configureSession(
                                    mode = selectedMode,
                                    subject = selectedSubject,
                                    selectedChapter = selectedChapter,
                                    topic = topicText,
                                    goal = goalText,
                                    durationMinutes = selectedDurationMinutes,
                                    pomodoroFocusMin = pomodoroFocusMin,
                                    pomodoroShortBreakMin = pomodoroShortBreakMin,
                                    pomodoroLongBreakMin = pomodoroLongBreakMin,
                                    pomodoroRounds = pomodoroRounds,
                                    autoStartBreaks = autoStartBreaks
                                )
                                viewModel.timerEngine.startSession()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val startLabel = when (selectedMode) {
                        SessionMode.COUNTDOWN -> "Start ${selectedDurationMinutes}m Focus Timer"
                        SessionMode.POMODORO -> "Start Pomodoro ($pomodoroFocusMin/$pomodoroShortBreakMin)"
                        SessionMode.STOPWATCH -> "Start Stopwatch"
                    }
                    Text(
                        text = startLabel,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}
}
}
