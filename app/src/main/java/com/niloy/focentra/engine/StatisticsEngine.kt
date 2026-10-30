package com.niloy.focentra.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.viewmodel.AppNotification
import com.niloy.focentra.viewmodel.MainViewModel
import com.niloy.focentra.viewmodel.NotificationType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NotificationScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val dbNotifications by viewModel.dbNotifications.collectAsStateWithLifecycle()
    val activeNotifications by viewModel.activeNotifications.collectAsStateWithLifecycle()
    val seenNotificationIds by viewModel.seenNotificationIds.collectAsStateWithLifecycle()
    val notificationStateLoaded by viewModel.notificationStateLoaded.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("All") }

    val allList = remember(dbNotifications, activeNotifications) {
        fun resolveType(rawType: String?, title: String?): NotificationType {
            val raw = rawType?.trim().orEmpty()
            val titleText = title?.lowercase(Locale.ROOT).orEmpty()

            if (titleText.contains("ambient focus") ||
                titleText.contains("peak focus") ||
                titleText.contains("night reflection") ||
                titleText.contains("weekly focus digest") ||
                titleText.contains("daily target") ||
                titleText.contains("goal")
            ) {
                return NotificationType.DAILY_TARGET
            }

            val normalizedRaw = raw.uppercase(Locale.ROOT)
            return try {
                with(NotificationType.valueOf(normalizedRaw)) {
                    if (this == NotificationType.GOAL_WARNING) NotificationType.DAILY_TARGET else this
                }
            } catch (_: Exception) {
                when {
                    normalizedRaw.contains("RECAP") || titleText.contains("recap") -> NotificationType.RECAP_REMINDER
                    normalizedRaw.contains("REVISION") ||
                        normalizedRaw.contains("INACTIVITY") ||
                        titleText.contains("revision") ||
                        titleText.contains("streak") ||
                        titleText.contains("inactivity") -> NotificationType.REVISION_DUE
                    else -> NotificationType.MOTIVATION
                }
            }
        }

        val mappedDb = dbNotifications.map { entity ->
            AppNotification(
                id = entity.id,
                type = resolveType(entity.type, entity.title),
                title = entity.title,
                message = entity.message,
                subjectName = entity.subjectName,
                chapterName = entity.chapterName,
                timestamp = entity.timestamp
            )
        }

        val mappedActive = activeNotifications.map { notif ->
            notif.copy(type = resolveType(notif.type.name, notif.title))
        }

        val mergedById = mutableMapOf<Long, AppNotification>()
        mappedActive.forEach { mergedById[it.id] = it }
        mappedDb.forEach { existing ->
            val current = mergedById[existing.id]
            if (current == null || existing.timestamp > current.timestamp) {
                mergedById[existing.id] = existing
            }
        }

        mergedById.values
            .toList()
            .sortedByDescending { it.timestamp }
    }

    val filteredNotifications = remember(allList, selectedFilter) {
        when (selectedFilter) {
            "Daily Target" -> allList.filter {
                it.type == NotificationType.GOAL_WARNING ||
                    it.type == NotificationType.DAILY_TARGET
            }
            "Revision Due" -> allList.filter {
                it.type == NotificationType.REVISION_DUE ||
                    it.type == NotificationType.INACTIVITY_WARNING
            }
            "Recap Reminder" -> allList.filter {
                it.type == NotificationType.RECAP_REMINDER
            }
            "Motivation" -> allList.filter {
                it.type == NotificationType.MOTIVATION
            }
            else -> allList
        }
    }

    LaunchedEffect(notificationStateLoaded, filteredNotifications) {
        if (notificationStateLoaded && filteredNotifications.isNotEmpty()) {
            viewModel.markNotificationsAsSeen(filteredNotifications.map { it.id }.toSet())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            val filters = listOf("All", "Daily Target", "Revision Due", "Recap Reminder", "Motivation")
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Notifications",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Your study reminders and revision alerts will be listed here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(filteredNotifications, key = { it.id }) { notif ->
                    val isUnseen = notificationStateLoaded && notif.id !in seenNotificationIds
                    NotificationCardItem(
                        notification = notif,
                        isUnseen = isUnseen,
                        onStartRevision = { subjectName, chapterName ->
                            if (subjectName.isNotBlank()) {
                                viewModel.startRevisionTimer(
                                    durationMinutes = 30,
                                    subjectName = subjectName,
                                    chapterName = chapterName
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCardItem(
    notification: AppNotification,
    isUnseen: Boolean,
    onStartRevision: (String, String) -> Unit
) {
    val cardBgColor = if (isUnseen) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val cardBorderColor = if (isUnseen) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

    val iconVector = when (notification.type) {
        NotificationType.REVISION_DUE -> Icons.Default.Autorenew
        NotificationType.INACTIVITY_WARNING -> Icons.Default.Warning
        NotificationType.GOAL_WARNING, NotificationType.DAILY_TARGET -> Icons.Default.AdsClick
        NotificationType.RECAP_REMINDER -> Icons.Default.Schedule
        NotificationType.MOTIVATION -> Icons.Default.Lightbulb
    }

    val iconTint = when (notification.type) {
        NotificationType.REVISION_DUE -> MaterialTheme.colorScheme.primary
        NotificationType.INACTIVITY_WARNING -> Color(0xFFF59E0B)
        NotificationType.GOAL_WARNING, NotificationType.DAILY_TARGET -> Color(0xFF10B981)
        NotificationType.RECAP_REMINDER -> Color(0xFF818CF8)
        NotificationType.MOTIVATION -> MaterialTheme.colorScheme.primary
    }

    val timeFormatted = remember(notification.timestamp) {
        val notificationDate = Calendar.getInstance().apply { timeInMillis = notification.timestamp }
        val today = Calendar.getInstance()
        val pattern = if (
            notificationDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            notificationDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        ) {
            "hh:mm a"
        } else {
            "MMM d, hh:mm a"
        }
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(notification.timestamp))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconTint.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )

                    if (isUnseen) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (notification.type == NotificationType.REVISION_DUE &&
                notification.subjectName.isNotBlank()
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        onStartRevision(notification.subjectName, notification.chapterName)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Start 30m Revision",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
