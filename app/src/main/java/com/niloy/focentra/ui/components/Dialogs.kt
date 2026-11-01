package com.niloy.focentra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.niloy.focentra.data.local.entity.StudySessionEntity
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@Composable
fun FocentraPopupDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismissRequest() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 380.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun DistractionLoggerDialog(
    onDismiss: () -> Unit,
    onLogCategory: (String) -> Unit
) {
    val categories = listOf(
        Pair("Phone Notification", Icons.Default.PhoneAndroid),
        Pair("Social Media", Icons.Default.Public),
        Pair("Talking / Family", Icons.Default.RecordVoiceOver),
        Pair("Snack / Water", Icons.Default.LocalCafe),
        Pair("Background Noise", Icons.Default.VolumeUp),
        Pair("Mind Wandering", Icons.Default.Psychology),
        Pair("Other Distraction", Icons.Default.WarningAmber)
    )

    FocentraPopupDialog(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NotificationsOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log Distraction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Acknowledge the interruption and return immediately to deep focus:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            categories.forEach { (catName, icon) ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLogCategory(catName)
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = catName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FocusNowStartDialog(
    subjects: List<com.niloy.focentra.data.local.entity.SubjectEntity>,
    chapters: List<com.niloy.focentra.data.local.entity.ChapterEntity>,
    target: com.niloy.focentra.viewmodel.ExamTarget? = null,
    onDismiss: () -> Unit,
    onStart: (subject: String, chapter: String, targetId: String) -> Unit
) {
    var selectedSubject by remember {
        mutableStateOf(subjects.firstOrNull()?.name ?: "")
    }
    var selectedChapter by remember { mutableStateOf("") }
    var showWarning by remember { mutableStateOf(false) }

    val matchingChapters = remember(chapters, selectedSubject) {
        if (selectedSubject.isBlank()) emptyList()
        else chapters.filter { it.subjectName.equals(selectedSubject, ignoreCase = true) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 380.dp)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) {},
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Focus Now",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Start Stopwatch Session",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (target != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Goal: ${target.title}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Subject Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Select Subject *",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subjects.isEmpty()) {
                            Text(
                                text = "No subjects found. Please add subjects first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(subjects.size) { index ->
                                    val sub = subjects[index]
                                    val isSelected = sub.name == selectedSubject
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedSubject = sub.name
                                            selectedChapter = ""
                                            showWarning = false
                                        },
                                        label = { Text(sub.name) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(parseColorHex(sub.colorHex))
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Chapter Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Select Chapter *",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (matchingChapters.isEmpty()) {
                            Text(
                                text = if (selectedSubject.isBlank()) "Please select a subject first." else "No chapters added for $selectedSubject.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(matchingChapters.size) { index ->
                                    val ch = matchingChapters[index]
                                    val cleanName = ch.chapterName.replace(Regex("(?i)\\bnoun\\b"), "").trim()
                                    val isSelected = ch.chapterName == selectedChapter
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedChapter = if (isSelected) "" else ch.chapterName
                                            showWarning = false
                                        },
                                        label = { Text(cleanName) },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (showWarning) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ Subject & Chapter selection required to start focus!",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (selectedSubject.isBlank() || selectedChapter.isBlank()) {
                                    showWarning = true
                                } else {
                                    onStart(selectedSubject, selectedChapter, target?.id ?: "")
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

private fun parseColorHex(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF818CF8)
    }
}

@Composable
fun NotificationCenterDialog(
    notifications: List<com.niloy.focentra.viewmodel.AppNotification>,
    onDismiss: () -> Unit,
    onStartRevision: ((subjectName: String, chapterName: String) -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "নোটিফিকেশন ও অ্যালার্ট হাব",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (notifications.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "কোনো নতুন নোটিফিকেশন নেই",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "সবগুলো অধ্যায়ের পড়া ও রিভিশন সঠিক ট্র্যাকে রয়েছে!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        notifications.forEach { notif ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = when (notif.type) {
                                    com.niloy.focentra.viewmodel.NotificationType.REVISION_DUE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                    com.niloy.focentra.viewmodel.NotificationType.INACTIVITY_WARNING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
                                    com.niloy.focentra.viewmodel.NotificationType.GOAL_WARNING -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (notif.type) {
                                        com.niloy.focentra.viewmodel.NotificationType.REVISION_DUE -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (notif.type) {
                                                com.niloy.focentra.viewmodel.NotificationType.REVISION_DUE -> Icons.Default.Warning
                                                com.niloy.focentra.viewmodel.NotificationType.INACTIVITY_WARNING -> Icons.Default.Schedule
                                                com.niloy.focentra.viewmodel.NotificationType.GOAL_WARNING -> Icons.Default.Flag
                                                else -> Icons.Default.Lightbulb
                                            },
                                            contentDescription = null,
                                            tint = when (notif.type) {
                                                com.niloy.focentra.viewmodel.NotificationType.REVISION_DUE -> MaterialTheme.colorScheme.error
                                                else -> MaterialTheme.colorScheme.primary
                                            },
                                            modifier = Modifier.size(18.dp)
                                        )

                                        Text(
                                            text = notif.title,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = notif.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (notif.type == com.niloy.focentra.viewmodel.NotificationType.REVISION_DUE && onStartRevision != null) {
                                        Button(
                                            onClick = {
                                                onStartRevision(notif.subjectName, notif.chapterName)
                                                onDismiss()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("৩০ মি. রিভিশন শুরু করুন", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun RevisionLockWarningDialog(
    revisionSubject: String,
    revisionChapter: String,
    onDismiss: () -> Unit,
    onStartRevisionNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "⚠️ রিভিশন লক ওয়ার্নিং!",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "«$revisionSubject» বিষয়ের «$revisionChapter» অধ্যায়টি ৩ দিন আগে শেষ হয়েছে। রিভিশনের নিয়ম অনুযায়ী আগে এই অধ্যায়টি ৩০ মিনিট রিভিশন করতে হবে! রিভিশন শেষ না করা পর্যন্ত অন্য সাবজেক্ট শুরু করা লক থাকবে।",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onStartRevisionNow,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("৩০ মিনিট রিভিশন শুরু করুন")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("বাতিল")
            }
        }
    )
}

@Composable
fun SessionCompletionDialog(
    session: StudySessionEntity,
    examTargets: List<com.niloy.focentra.viewmodel.ExamTarget> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (productivity: Int, mood: String, energy: String, notes: String, location: String, targetId: String) -> Unit
) {
    var rating by remember { mutableStateOf(session.productivityRating) }
    var mood by remember { mutableStateOf(session.mood) }
    var energy by remember { mutableStateOf(session.energyLevel) }
    var notes by remember { mutableStateOf(session.notes) }
    var location by remember { mutableStateOf(session.location) }
    var selectedTargetId by remember { mutableStateOf(session.targetId) }

    val totalMins = session.durationSeconds / 60
    val focusedMins = session.actualFocusedSeconds / 60
    val pausedMins = session.pausedSeconds / 60

    FocentraPopupDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
                // Header Trophy/Check
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Session Completed!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = session.subject + if (session.topic.isNotBlank()) " • ${session.topic}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Stats summary strip
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Focused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${focusedMins}m",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Paused",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${pausedMins}m",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Distractions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${session.distractionCount}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = if (session.distractionCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Productivity Rating
                Text(
                    text = "Productivity Rating",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { rating = i },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i Stars",
                                tint = if (i <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mood selector with Icons
                Text(
                    text = "How do you feel?",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val moodList = listOf(
                        Triple("TERRIBLE", Icons.Default.SentimentVeryDissatisfied, "Tired"),
                        Triple("BAD", Icons.Default.SentimentDissatisfied, "Low"),
                        Triple("NORMAL", Icons.Default.SentimentNeutral, "Okay"),
                        Triple("GOOD", Icons.Default.SentimentSatisfied, "Good"),
                        Triple("EXCELLENT", Icons.Default.SentimentVerySatisfied, "Peak")
                    )
                    moodList.forEach { (mKey, mIcon, mLabel) ->
                        val isSelected = mood == mKey
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { mood = mKey }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mIcon,
                                    contentDescription = mLabel,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Text(
                                text = mLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Add to Target Selector
                if (examTargets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Add to Target:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val targetOptions = remember(examTargets) {
                            listOf(Pair("None", "")) + examTargets.map { Pair(it.title, it.id) }
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(targetOptions.size) { idx ->
                                val (tTitle, tId) = targetOptions[idx]
                                val isSelected = selectedTargetId == tId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedTargetId = tId },
                                    label = { Text(tTitle, maxLines = 1) },
                                    leadingIcon = if (isSelected && tId.isNotBlank()) {
                                        { Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("What did you accomplish?") },
                    placeholder = { Text("Key insights, topics covered...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Skip")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(rating, mood, energy, notes, location, selectedTargetId)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Session")
                    }
                }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ManualStudyDataDialog(
    subjects: List<String>,
    examTargets: List<com.niloy.focentra.viewmodel.ExamTarget> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (
        subject: String,
        topic: String,
        durationMinutes: Int,
        category: String,
        notes: String,
        rating: Int,
        targetId: String,
        timestamp: Long
    ) -> Unit
) {
    var subject by remember { mutableStateOf(subjects.firstOrNull() ?: "General") }
    var customSubject by remember { mutableStateOf("") }
    var useCustomSubject by remember { mutableStateOf(subjects.isEmpty()) }
    var topic by remember { mutableStateOf("") }
    var sessionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var durationMinutesStr by remember { mutableStateOf("30") }
    var category by remember { mutableStateOf("Study") }
    var notes by remember { mutableStateOf("") }
    var rating by remember { mutableIntStateOf(4) }
    var selectedTargetId by remember { mutableStateOf("") }

    val categories = listOf("Study", "Exam Prep", "Assignment", "Revision", "Research")
    val sessionDateText = remember(sessionTimestamp) {
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(sessionTimestamp))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EditNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log Study Data",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Manually record a completed study session into your analytics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Subject Selector / Input
                if (!useCustomSubject && subjects.isNotEmpty()) {
                    Text(
                        text = "Subject",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(subjects.size) { idx ->
                            val sub = subjects[idx]
                            FilterChip(
                                selected = subject == sub,
                                onClick = { subject = sub },
                                label = { Text(sub, maxLines = 1) }
                            )
                        }
                    }
                    TextButton(onClick = { useCustomSubject = true }) {
                        Text("+ Custom Subject")
                    }
                } else {
                    OutlinedTextField(
                        value = customSubject,
                        onValueChange = { customSubject = it },
                        label = { Text("Subject Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    if (subjects.isNotEmpty()) {
                        TextButton(onClick = { useCustomSubject = false }) {
                            Text("Select existing subject")
                        }
                    }
                }

                // Topic Input
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic / Focus Area") },
                    placeholder = { Text("e.g. Chapter 3 Calculus") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Date: $sessionDateText")
                }

                // Duration Input
                OutlinedTextField(
                    value = durationMinutesStr,
                    onValueChange = { durationMinutesStr = it.filter { char -> char.isDigit() } },
                    label = { Text("Duration (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                // Category Chips
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories.size) { idx ->
                        val cat = categories[idx]
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, maxLines = 1) }
                        )
                    }
                }

                // Rating Stars
                Text(
                    text = "Productivity Rating: $rating / 5",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$i Stars",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Add to Target Selector
                if (examTargets.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Add to Target:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val targetOptions = remember(examTargets) {
                            listOf(Pair("None", "")) + examTargets.map { Pair(it.title, it.id) }
                        }
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(targetOptions.size) { idx ->
                                val (tTitle, tId) = targetOptions[idx]
                                val isSelected = selectedTargetId == tId
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedTargetId = tId },
                                    label = { Text(tTitle, maxLines = 1) },
                                    leadingIcon = if (isSelected && tId.isNotBlank()) {
                                        { Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // Notes Input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / Key Accomplishments") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalSubject = if (useCustomSubject) customSubject.ifBlank { "General" } else subject
                    val durMin = durationMinutesStr.toIntOrNull() ?: 30
                    if (durMin > 0) {
                        onSave(
                            finalSubject,
                            topic,
                            durMin,
                            category,
                            notes,
                            rating,
                            selectedTargetId,
                            sessionTimestamp
                        )
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save Study Data")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = manualDatePickerMillisForTimestamp(sessionTimestamp)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            sessionTimestamp = manualSessionTimestampForDate(
                                selectedDateMillis = selectedDateMillis,
                                timestampWithTime = sessionTimestamp
                            )
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

internal fun manualDatePickerMillisForTimestamp(timestamp: Long): Long {
    val localDate = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localDate.get(Calendar.YEAR),
            localDate.get(Calendar.MONTH),
            localDate.get(Calendar.DAY_OF_MONTH),
            0,
            0,
            0
        )
    }.timeInMillis
}

internal fun manualSessionTimestampForDate(
    selectedDateMillis: Long,
    timestampWithTime: Long
): Long {
    val selectedDate = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = selectedDateMillis
    }
    return Calendar.getInstance().apply {
        timeInMillis = timestampWithTime
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
        set(Calendar.MONTH, selectedDate.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@Composable
fun EditSessionDialog(
    session: StudySessionEntity,
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (StudySessionEntity) -> Unit
) {
    var subject by remember { mutableStateOf(session.subject) }
    var topic by remember { mutableStateOf(session.topic) }
    var durationMinutes by remember { mutableStateOf((session.actualFocusedSeconds / 60).toString()) }
    var rating by remember { mutableStateOf(session.productivityRating) }
    var notes by remember { mutableStateOf(session.notes) }
    var tags by remember { mutableStateOf(session.tags) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Study Session",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Topic") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = durationMinutes,
                    onValueChange = { durationMinutes = it },
                    label = { Text("Focused Duration (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text(
                    text = "Productivity: $rating / 5",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..5) {
                        IconButton(onClick = { rating = i }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = if (i <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 3
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mins = durationMinutes.toLongOrNull() ?: (session.actualFocusedSeconds / 60)
                    onSave(
                        session.copy(
                            subject = subject,
                            topic = topic,
                            actualFocusedSeconds = mins * 60,
                            durationSeconds = mins * 60 + session.pausedSeconds,
                            productivityRating = rating,
                            notes = notes,
                            tags = tags
                        )
                    )
                }
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
