package com.niloy.focentra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.data.local.entity.PresetEntity
import com.niloy.focentra.ui.components.EmptyStateView
import com.niloy.focentra.viewmodel.MainViewModel

@Composable
fun PresetsScreen(
    viewModel: MainViewModel,
    isFabVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var presetToEdit by remember { mutableStateOf<PresetEntity?>(null) }
    var presetDetailToShow by remember { mutableStateOf<PresetEntity?>(null) }
    var presetToDelete by remember { mutableStateOf<PresetEntity?>(null) }

    if (showCreateDialog) {
        PresetEditDialog(
            existingPreset = null,
            subjects = subjects.map { it.name },
            onDismiss = { showCreateDialog = false },
            onSave = { newPreset ->
                viewModel.savePreset(newPreset)
                showCreateDialog = false
            }
        )
    }

    if (presetDetailToShow != null) {
        PresetDetailDialog(
            preset = presetDetailToShow!!,
            onDismiss = { presetDetailToShow = null },
            onStart = {
                val p = presetDetailToShow!!
                presetDetailToShow = null
                viewModel.startTimerWithPreset(p)
            },
            onEdit = {
                val p = presetDetailToShow!!
                presetDetailToShow = null
                presetToEdit = p
            }
        )
    }

    if (presetToEdit != null) {
        PresetEditDialog(
            existingPreset = presetToEdit,
            subjects = subjects.map { it.name },
            onDismiss = { presetToEdit = null },
            onSave = { updatedPreset ->
                viewModel.savePreset(updatedPreset)
                presetToEdit = null
            }
        )
    }

    if (presetToDelete != null) {
        AlertDialog(
            onDismissRequest = { presetToDelete = null },
            title = { Text("Delete Preset?") },
            text = { Text("Are you sure you want to delete «${presetToDelete!!.title}»?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePreset(presetToDelete!!)
                        presetToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { presetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (presets.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.BookmarkBorder,
                title = "No study presets",
                description = "Save your favorite timer configurations for one-tap starting.",
                actionButtonText = "Create Preset",
                onActionClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
            ) {
                items(presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onStartClick = { viewModel.startTimerWithPreset(preset) },
                        onEditClick = { presetToEdit = preset },
                        onDeleteClick = { presetToDelete = preset },
                        onCardClick = { presetDetailToShow = preset }
                    )
                }
            }
        }

        if (presets.isNotEmpty()) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isFabVisible,
                enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 86.dp)
            ) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Preset")
                }
            }
        }
    }
}

@Composable
fun PresetCard(
    preset: PresetEntity,
    onStartClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCardClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (preset.type == "POMODORO") Icons.Default.AvTimer else Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    val detailText = when (preset.type) {
                        "POMODORO" -> "${preset.pomodoroFocusMin}m focus / ${preset.pomodoroShortBreakMin}m break • ${preset.pomodoroRounds}r"
                        "STOPWATCH" -> "Open stopwatch"
                        else -> "${preset.durationMinutes}m duration"
                    }

                    Text(
                        text = "${preset.subject} • $detailText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEditClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                IconButton(onClick = onDeleteClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                FilledTonalButton(
                    onClick = onStartClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Start",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun PresetDetailDialog(
    preset: PresetEntity,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (preset.type == "POMODORO") Icons.Default.AvTimer else Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Mode: ${preset.type}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Subject: ${preset.subject}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (preset.topic.isNotBlank()) {
                            Text(
                                text = "Topic: ${preset.topic}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Timer Configuration",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        when (preset.type) {
                            "POMODORO" -> {
                                Text(
                                    text = "• Focus Time: ${preset.pomodoroFocusMin} minutes\n" +
                                        "• Short Break: ${preset.pomodoroShortBreakMin} minutes\n" +
                                        "• Long Break: ${preset.pomodoroLongBreakMin} minutes\n" +
                                        "• Total Rounds: ${preset.pomodoroRounds}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            "COUNTDOWN" -> {
                                Text(
                                    text = "• Duration: ${preset.durationMinutes} minutes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            "STOPWATCH" -> {
                                Text(
                                    text = "• Open-ended study time\n• No fixed duration",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            else -> {
                                Text(
                                    text = "• Duration: ${preset.durationMinutes} minutes",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Start Timer")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) {
                    Text("Edit")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditDialog(
    existingPreset: PresetEntity?,
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (PresetEntity) -> Unit
) {
    var title by remember(existingPreset?.id) { mutableStateOf(existingPreset?.title ?: "") }
    var selectedType by remember(existingPreset?.id) { mutableStateOf(existingPreset?.type ?: "POMODORO") }
    var selectedSubject by remember(existingPreset?.id, subjects) {
        mutableStateOf(existingPreset?.subject ?: subjects.firstOrNull() ?: "General Study")
    }
    var topic by remember(existingPreset?.id) { mutableStateOf(existingPreset?.topic ?: "") }
    var durationMinutes by remember(existingPreset?.id) { mutableStateOf((existingPreset?.durationMinutes ?: 25).toString()) }
    var pomodoroFocusMin by remember(existingPreset?.id) { mutableStateOf((existingPreset?.pomodoroFocusMin ?: 25).toString()) }
    var pomodoroShortBreakMin by remember(existingPreset?.id) { mutableStateOf((existingPreset?.pomodoroShortBreakMin ?: 5).toString()) }
    var pomodoroLongBreakMin by remember(existingPreset?.id) { mutableStateOf((existingPreset?.pomodoroLongBreakMin ?: 15).toString()) }
    var pomodoroRounds by remember(existingPreset?.id) { mutableStateOf((existingPreset?.pomodoroRounds ?: 4).toString()) }

    val subjectOptions = if (subjects.isEmpty()) listOf("General Study") else subjects
    var subjectExpanded by remember { mutableStateOf(false) }

    val titleLimit = 50
    val topicLimit = 40

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingPreset != null) "Edit Study Preset" else "Create Study Preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(titleLimit) },
                    label = { Text("Preset Title") },
                    placeholder = { Text("e.g. Deep Math Sprint") },
                    supportingText = {
                        Text(
                            text = "${title.length}/$titleLimit",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Text("Subject", style = MaterialTheme.typography.labelSmall)

                ExposedDropdownMenuBox(
                    expanded = subjectExpanded,
                    onExpandedChange = { subjectExpanded = !subjectExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedSubject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Subject") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = subjectExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenu(
                        expanded = subjectExpanded,
                        onDismissRequest = { subjectExpanded = false }
                    ) {
                        subjectOptions.forEach { subject ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(subject) },
                                onClick = {
                                    selectedSubject = subject
                                    subjectExpanded = false
                                }
                            )
                        }
                    }
                }

                Text("Mode", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val modes = listOf(
                        "POMODORO" to "Pomodoro",
                        "COUNTDOWN" to "Countdown",
                        "STOPWATCH" to "Stopwatch"
                    )

                    modes.forEach { (type, label) ->
                        val isSelected = selectedType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { selectedType = type }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                when (selectedType) {
                    "POMODORO" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pomodoroFocusMin,
                                onValueChange = { pomodoroFocusMin = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Focus (m)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = pomodoroShortBreakMin,
                                onValueChange = { pomodoroShortBreakMin = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Break (m)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = pomodoroLongBreakMin,
                                onValueChange = { pomodoroLongBreakMin = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Long Break (m)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = pomodoroRounds,
                                onValueChange = { pomodoroRounds = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Rounds") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    "COUNTDOWN" -> {
                        OutlinedTextField(
                            value = durationMinutes,
                            onValueChange = { durationMinutes = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Duration (minutes)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    "STOPWATCH" -> {
                        // no fixed duration for stopwatch preset
                    }
                }

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it.take(topicLimit) },
                    label = { Text("Topic (Optional)") },
                    supportingText = {
                        Text(
                            text = "${topic.length}/$topicLimit",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val sanitizedTitle = title.trim()
                    val safeSelectedSubject = selectedSubject.trim().ifBlank { "General Study" }

                    if (sanitizedTitle.isBlank()) return@Button

                    val finalDurationMinutes = durationMinutes.toPositiveIntOrDefault(25)
                    val finalFocus = pomodoroFocusMin.toPositiveIntOrDefault(25)
                    val finalShortBreak = pomodoroShortBreakMin.toPositiveIntOrDefault(5)
                    val finalLongBreak = pomodoroLongBreakMin.toPositiveIntOrDefault(15)
                    val finalRounds = pomodoroRounds.toPositiveIntOrDefault(4)

                    onSave(
                        PresetEntity(
                            id = existingPreset?.id ?: 0L,
                            title = sanitizedTitle,
                            type = selectedType,
                            subject = safeSelectedSubject,
                            topic = topic.trim(),
                            durationMinutes = if (selectedType == "COUNTDOWN") finalDurationMinutes else 25,
                            pomodoroFocusMin = if (selectedType == "POMODORO") finalFocus else 25,
                            pomodoroShortBreakMin = if (selectedType == "POMODORO") finalShortBreak else 5,
                            pomodoroLongBreakMin = if (selectedType == "POMODORO") finalLongBreak else 15,
                            pomodoroRounds = if (selectedType == "POMODORO") finalRounds else 4
                        )
                    )
                }
            ) {
                Text("Save Preset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun String.toPositiveIntOrDefault(default: Int): Int {
    return this.trim().toIntOrNull()?.takeIf { it > 0 } ?: default
}
