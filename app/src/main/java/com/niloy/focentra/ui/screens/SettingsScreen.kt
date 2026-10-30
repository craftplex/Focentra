package com.niloy.focentra.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.BuildConfig
import com.niloy.focentra.data.local.entity.ReminderEntity
import com.niloy.focentra.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val isDaynexaConnected by viewModel.isDaynexaConnected.collectAsStateWithLifecycle()

    var showGoalEditDialog by remember { mutableStateOf<String?>(null) }
    var showAddReminderDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCsvTextImportDialog by remember { mutableStateOf(false) }
    var showCsvTextExportDialog by remember { mutableStateOf(false) }
    var exportedCsvText by remember { mutableStateOf("") }
    var showDaynexaConsentDialog by remember { mutableStateOf(false) }
    var showDaynexaDisconnectDialog by remember { mutableStateOf(false) }
    var showClearAllDataDialog by remember { mutableStateOf(false) }

    var importJsonText by remember { mutableStateOf("") }
    var importCsvText by remember { mutableStateOf("") }
    var importReplace by remember { mutableStateOf(false) }
    var reminderToDelete by remember { mutableStateOf<ReminderEntity?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    var importError by remember { mutableStateOf<String?>(null) }
    var csvError by remember { mutableStateOf<String?>(null) }

    val dailyGoal = goals.find { it.periodType == "DAILY" }?.targetMinutes ?: 480
    val weeklyGoal = goals.find { it.periodType == "WEEKLY" }?.targetMinutes ?: 2400
    val monthlyGoal = goals.find { it.periodType == "MONTHLY" }?.targetMinutes ?: 9600

    if (showGoalEditDialog != null) {
        val periodType = showGoalEditDialog!!
        val currentTarget = when (periodType) {
            "DAILY" -> dailyGoal
            "WEEKLY" -> weeklyGoal
            "MONTHLY" -> monthlyGoal
            else -> dailyGoal
        }

        GoalEditDialog(
            periodType = periodType,
            currentMinutes = currentTarget,
            onDismiss = { showGoalEditDialog = null },
            onSave = { newMins ->
                viewModel.updateGoalTarget(periodType, newMins)
                showGoalEditDialog = null
            }
        )
    }

    if (showClearAllDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDataDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Clear All App Data?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete all study sessions, subjects, presets, goals, and exam milestones? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllDataDialog = false
                        viewModel.clearAllAppData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDataDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (reminderToDelete != null) {
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            title = { Text("Delete Reminder?") },
            text = { Text("Are you sure you want to delete reminder «${reminderToDelete!!.title}»?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteReminder(reminderToDelete!!)
                        reminderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddReminderDialog) {
        AddReminderDialog(
            onDismiss = { showAddReminderDialog = false },
            onSave = { reminder ->
                viewModel.saveReminder(reminder)
                showAddReminderDialog = false
            }
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        viewModel.repository.exportDataJsonStream(output)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            importStatusMessage = "Importing JSON file, please wait..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val success = if (stream != null) {
                        viewModel.repository.importDataJsonStream(stream, false)
                    } else false

                    withContext(Dispatchers.Main) {
                        if (success) viewModel.rescheduleReminders()
                        isImporting = false
                        importStatusMessage =
                            if (success) "JSON Import completed successfully!" else "JSON Import failed. Please check file format."
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage = "JSON Import failed: ${e.message}"
                    }
                }
            }
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isImporting = true
            importStatusMessage = "Importing CSV file, please wait..."
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val stream = context.contentResolver.openInputStream(uri)
                    val success = if (stream != null) {
                        viewModel.repository.importSessionsCsvStream(stream)
                    } else false

                    withContext(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage =
                            if (success) "CSV Import completed successfully!" else "CSV Import failed."
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        importStatusMessage = "CSV Import failed: ${e.message}"
                    }
                }
            }
        }
    }

    if (showCsvTextExportDialog) {
        AlertDialog(
            onDismissRequest = { showCsvTextExportDialog = false },
            title = { Text("Export Sessions as CSV Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = exportedCsvText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("CSV Output Data") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(exportedCsvText))
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, exportedCsvText)
                                type = "text/csv"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, "Export Study Sessions (CSV)")
                            context.startActivity(shareIntent)
                            showCsvTextExportDialog = false
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCsvTextExportDialog = false }) { Text("Close") }
            }
        )
    }

    if (showCsvTextImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showCsvTextImportDialog = false
                csvError = null
            },
            title = { Text("Import Sessions from CSV Text") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Paste or enter CSV text below:", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    importCsvText = clipText
                                    csvError = null
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste")
                        }
                    }

                    OutlinedTextField(
                        value = importCsvText,
                        onValueChange = {
                            importCsvText = it
                            csvError = null
                        },
                        label = { Text("CSV Text Area") },
                        placeholder = { Text("Tap Paste or paste CSV contents here...") },
                        isError = csvError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = importReplace, onCheckedChange = { importReplace = it })
                        Text("Replace sessions", style = MaterialTheme.typography.bodySmall)
                    }

                    if (csvError != null) {
                        Text(
                            text = csvError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importCsvText.isBlank()) {
                            csvError = "Please enter or paste CSV content"
                            return@Button
                        }

                        isImporting = true
                        importStatusMessage = "Importing CSV text, please wait..."
                        showCsvTextImportDialog = false

                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val inputStream = importCsvText.byteInputStream(Charsets.UTF_8)
                                val success = viewModel.repository.importSessionsCsvStream(
                                    inputStream,
                                    replaceExisting = importReplace
                                )

                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    importStatusMessage =
                                        if (success) "CSV Text Import completed successfully!" else "CSV Text Import failed. Please check formatting."
                                    importCsvText = ""
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    importStatusMessage = "CSV Text Import failed: ${e.message}"
                                }
                            }
                        }
                    },
                    enabled = importCsvText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCsvTextImportDialog = false
                    csvError = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                importError = null
            },
            title = { Text("Import Data Backup (JSON)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = {
                            importJsonText = it
                            importError = null
                        },
                        label = { Text("JSON Text Area") },
                        placeholder = { Text("Paste JSON backup content here...") },
                        isError = importError != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = importReplace, onCheckedChange = { importReplace = it })
                        Text("Replace all content", style = MaterialTheme.typography.bodySmall)
                    }

                    if (importError != null) {
                        Text(
                            text = importError ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importJsonText.isBlank()) {
                            importError = "Please enter or paste JSON backup content"
                            return@Button
                        }
                        coroutineScope.launch {
                            val success = viewModel.importJson(importJsonText, importReplace)
                            if (success) {
                                showImportDialog = false
                                importJsonText = ""
                                importError = null
                            } else {
                                importError = "Invalid JSON format. Please verify your backup string."
                            }
                        }
                    },
                    enabled = importJsonText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Import Backup")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importError = null
                }) { Text("Cancel") }
            }
        )
    }

    if (showDaynexaConsentDialog) {
        AlertDialog(
            onDismissRequest = { showDaynexaConsentDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.SyncAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Connect Daynexa Integration",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Daynexa can securely read your completed study sessions to synchronize study progress locally on this device.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "• Provider Authority: com.focentra.provider",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Endpoint: content://com.focentra.provider/sessions",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "• Schema: Version 1 (Read-Only)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "• Data Shared: Session ID, Subject, Topic, Start/End Time, Duration, Completion Status, Focus Score.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "• Protection: Strictly read-only. Daynexa cannot modify, create, or delete your Focentra data. 100% on-device.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "You can disconnect and revoke access at any time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDaynexaConsent(true)
                        showDaynexaConsentDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Authorize & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDaynexaConsentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (importStatusMessage != null) {
        AlertDialog(
            onDismissRequest = { if (!isImporting) importStatusMessage = null },
            title = { Text(if (isImporting) "Importing Data..." else "Import Status") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isImporting) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }
                    Text(text = importStatusMessage ?: "")
                }
            },
            confirmButton = {
                if (!isImporting) {
                    Button(onClick = { importStatusMessage = null }) {
                        Text("OK")
                    }
                }
            }
        )
    }

    if (showDaynexaDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDaynexaDisconnectDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Disconnect Daynexa?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Daynexa will immediately lose access to query study sessions and receive completion broadcasts from Focentra. You can reconnect whenever you wish.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setDaynexaConsent(false)
                        showDaynexaDisconnectDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDaynexaDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)
    ) {
        // Section 1: Study Goals
        item {
            Text(
                text = "STUDY TARGET GOALS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingGoalItem(
                        icon = Icons.Default.Today,
                        title = "Daily Study Target",
                        targetMinutes = dailyGoal,
                        subtitle = "Daily focus commitment",
                        onClick = { showGoalEditDialog = "DAILY" }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingGoalItem(
                        icon = Icons.Default.DateRange,
                        title = "Weekly Study Target",
                        targetMinutes = weeklyGoal,
                        subtitle = "7-day cumulative focus",
                        onClick = { showGoalEditDialog = "WEEKLY" }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SettingGoalItem(
                        icon = Icons.Default.CalendarMonth,
                        title = "Monthly Study Target",
                        targetMinutes = monthlyGoal,
                        subtitle = "Long-term consistency",
                        onClick = { showGoalEditDialog = "MONTHLY" }
                    )
                }
            }
        }

        // Section 2: Study Reminders
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Study Reminders",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = { showAddReminderDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Reminder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (reminders.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsNone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No active reminders. Tap '+' to add study alarms.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            reminders.forEach { reminder ->
                                val h12 = when {
                                    reminder.hour == 0 -> 12
                                    reminder.hour > 12 -> reminder.hour - 12
                                    else -> reminder.hour
                                }
                                val amPm = if (reminder.hour >= 12) "PM" else "AM"
                                val formattedTime = "${h12}:${String.format("%02d", reminder.minute)}"

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = reminder.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 15.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formattedTime,
                                                style = MaterialTheme.typography.headlineMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 32.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = amPm,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 16.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Switch(
                                            checked = reminder.enabled,
                                            onCheckedChange = { viewModel.toggleReminder(reminder) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.surface,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        )

                                        IconButton(
                                            onClick = { reminderToDelete = reminder },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Data Management & Backup
        item {
            Text(
                text = "DATA PORTABILITY & BACKUP",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        icon = Icons.Default.CloudDownload,
                        title = "JSON Export",
                        subtitle = "Save full .json backup file locally",
                        onClick = {
                            val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            val timestamp = formatter.format(Date())
                            exportLauncher.launch("focentra_backup_$timestamp.json")
                        }
                    )

                    ActionTile(
                        icon = Icons.Default.CloudUpload,
                        title = "JSON Import",
                        subtitle = "Restore database from a .json backup file",
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )

                    ActionTile(
                        icon = Icons.Default.TableChart,
                        title = "CSV Text Export",
                        subtitle = "Export study sessions as CSV text to share or copy",
                        onClick = {
                            coroutineScope.launch {
                                exportedCsvText = viewModel.exportCsv()
                                showCsvTextExportDialog = true
                            }
                        }
                    )

                    ActionTile(
                        icon = Icons.Default.EditNote,
                        title = "CSV Text Import",
                        subtitle = "Paste raw CSV text directly to import sessions",
                        onClick = {
                            showCsvTextImportDialog = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ActionTile(
                        icon = Icons.Default.DeleteForever,
                        title = "Clear All App Data",
                        subtitle = "Wipe database, sessions and restore default settings",
                        onClick = { showClearAllDataDialog = true }
                    )
                }
            }
        }

        // Section 4: External Integrations
        item {
            Text(
                text = "INTEGRATIONS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isDaynexaConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDaynexaConnected) Icons.Default.CheckCircle else Icons.Default.SyncAlt,
                                    contentDescription = null,
                                    tint = if (isDaynexaConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Daynexa",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "API v1",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                text = if (isDaynexaConnected) "Connected (Provider + Broadcast Push)" else "Disconnected • Tap to configure access",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDaynexaConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isDaynexaConnected) {
                            OutlinedButton(
                                onClick = { showDaynexaDisconnectDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                            ) {
                                Text("Disconnect")
                            }
                        } else {
                            Button(
                                onClick = { showDaynexaConsentDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Connect")
                            }
                        }
                    }

                    if (isDaynexaConnected) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Endpoint: content://com.focentra.provider/sessions",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Status: Active • Secure 100% on-device sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 5: Offline Guarantee & About
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "100% Offline & Private Study OS",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Focentra v${BuildConfig.VERSION_NAME} uses on-device Room SQLite database. Your study sessions, personal records, and goals never leave your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "“Learn. Build. Improve. Repeat.”",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "© 2026 Niloy Mitra. All rights reserved.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SettingGoalItem(
    icon: ImageVector,
    title: String,
    targetMinutes: Int,
    subtitle: String,
    onClick: () -> Unit
) {
    val hrs = targetMinutes / 60
    val mins = targetMinutes % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${hrs}h ${String.format("%02d", mins)}m",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GoalEditDialog(
    periodType: String,
    currentMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var hours by remember(currentMinutes) { mutableStateOf((currentMinutes / 60).coerceAtLeast(0).toString()) }
    var mins by remember(currentMinutes) { mutableStateOf((currentMinutes % 60).coerceAtLeast(0).toString()) }

    val validGoal = remember(hours, mins) {
        val parsedHours = hours.toIntOrNull() ?: -1
        val parsedMinutes = mins.toIntOrNull() ?: -1
        parsedHours >= 0 && parsedMinutes in 0..59
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set $periodType Goal") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Hours") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = mins,
                    onValueChange = { mins = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Minutes") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = validGoal,
                onClick = {
                    val h = hours.toIntOrNull() ?: 0
                    val m = mins.toIntOrNull() ?: 0
                    onSave((h * 60) + m)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun formatReminderDays(daysStr: String): String {
    val upper = daysStr.uppercase()
    val allDays = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val weekdays = listOf("MON", "TUE", "WED", "THU", "FRI")
    val weekends = listOf("SAT", "SUN")

    val parts = upper.split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return when {
        parts.containsAll(allDays) -> "Every day"
        parts.containsAll(weekdays) && parts.size >= 5 -> "Weekdays (Mon-Fri)"
        parts.containsAll(weekends) && parts.size >= 2 -> "Weekends (Sat-Sun)"
        parts.isNotEmpty() -> parts.joinToString(", ") {
            it.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }
        }
        else -> "Every day"
    }
}

@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onSave: (ReminderEntity) -> Unit
) {
    var label by remember { mutableStateOf("Deep Study Session") }
    var hour12 by remember { mutableStateOf("07") }
    var minute by remember { mutableStateOf("00") }
    var isPm by remember { mutableStateOf(true) }
    var selectedFrequency by remember { mutableStateOf("Every day") }

    val parsedHour12 = hour12.toIntOrNull()
    val parsedMinute = minute.toIntOrNull()
    val hasValidTime = parsedHour12 != null && parsedHour12 in 1..12 &&
        parsedMinute != null && parsedMinute in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Study Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Reminder Label") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour12,
                        onValueChange = { hour12 = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Hour (1-12)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = parsedHour12 == null || parsedHour12 !in 1..12
                    )
                    OutlinedTextField(
                        value = minute,
                        onValueChange = { minute = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Minute (0-59)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = parsedMinute == null || parsedMinute !in 0..59
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isPm,
                        onClick = { isPm = false },
                        label = { Text("AM") },
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    FilterChip(
                        selected = isPm,
                        onClick = { isPm = true },
                        label = { Text("PM") },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Text("Repeat Schedule", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Every day", "Weekdays", "Weekends").forEach { freq ->
                        val isSelected = selectedFrequency == freq
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFrequency = freq },
                            label = { Text(freq, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = label.isNotBlank() && hasValidTime,
                onClick = {
                    if (!hasValidTime) return@Button

                    val parsedH12 = requireNotNull(parsedHour12)
                    val m = requireNotNull(parsedMinute)

                    val h24 = when {
                        isPm && parsedH12 < 12 -> parsedH12 + 12
                        !isPm && parsedH12 == 12 -> 0
                        else -> parsedH12
                    }

                    val days = when (selectedFrequency) {
                        "Weekdays" -> "Mon,Tue,Wed,Thu,Fri"
                        "Weekends" -> "Sat,Sun"
                        else -> "Mon,Tue,Wed,Thu,Fri,Sat,Sun"
                    }

                    onSave(
                        ReminderEntity(
                            title = label.ifBlank { "Study Session" },
                            hour = h24,
                            minute = m,
                            daysOfWeek = days
                        )
                    )
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add Reminder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
