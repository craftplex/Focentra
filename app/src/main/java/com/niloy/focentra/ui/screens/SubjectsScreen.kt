package com.niloy.focentra.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.data.local.entity.ChapterEntity
import com.niloy.focentra.data.local.entity.SubjectEntity
import com.niloy.focentra.data.local.entity.TopicEntity
import com.niloy.focentra.ui.components.EmptyStateView
import com.niloy.focentra.ui.components.getSubjectIcon
import com.niloy.focentra.ui.components.parseColorHex
import com.niloy.focentra.viewmodel.MainViewModel

@Composable
fun SubjectsScreen(
    viewModel: MainViewModel,
    isFabVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val topics by viewModel.topics.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()

    var showCreateDialog by remember { mutableStateOf(false) }
    var subjectToEdit by remember { mutableStateOf<SubjectEntity?>(null) }
    var subjectToAddTopic by remember { mutableStateOf<SubjectEntity?>(null) }
    var subjectToAddChapter by remember { mutableStateOf<SubjectEntity?>(null) }
    var chapterToEdit by remember { mutableStateOf<ChapterEntity?>(null) }
    var chapterToDelete by remember { mutableStateOf<ChapterEntity?>(null) }
    var subjectToDelete by remember { mutableStateOf<SubjectEntity?>(null) }
    var topicToDelete by remember { mutableStateOf<TopicEntity?>(null) }

    // safer aggregation: normalize subject name, avoid duplicate-key collisions
    val studyMinsBySubject = remember(sessions) {
        sessions
            .groupBy { it.subject.trim() }
            .mapValues { entry ->
                entry.value.sumOf { it.actualFocusedSeconds } / 60L
            }
    }

    if (showCreateDialog) {
        CreateSubjectDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, colorHex, icon, targetHrs, desc ->
                viewModel.createSubject(name, colorHex, icon, targetHrs, desc)
                showCreateDialog = false
            }
        )
    }

    if (chapterToEdit != null) {
        EditChapterDialog(
            chapter = chapterToEdit!!,
            onDismiss = { chapterToEdit = null },
            onSave = { updated ->
                viewModel.updateChapter(updated)
                chapterToEdit = null
            }
        )
    }

    if (chapterToDelete != null) {
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text("Delete Syllabus Chapter?") },
            text = { Text("Are you sure you want to delete chapter «${chapterToDelete!!.chapterName}»?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChapter(chapterToDelete!!)
                        chapterToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (subjectToAddChapter != null) {
        AddChapterDialog(
            subject = subjectToAddChapter!!,
            onDismiss = { subjectToAddChapter = null },
            onAdd = { chapterName ->
                viewModel.addChapter(
                    subjectToAddChapter!!.id,
                    subjectToAddChapter!!.name,
                    chapterName
                )
                subjectToAddChapter = null
            }
        )
    }

    if (subjectToEdit != null) {
        EditSubjectDialog(
            subject = subjectToEdit!!,
            onDismiss = { subjectToEdit = null },
            onUpdate = { updatedSubject ->
                viewModel.updateSubject(updatedSubject)
                subjectToEdit = null
            }
        )
    }

    if (topicToDelete != null) {
        AlertDialog(
            onDismissRequest = { topicToDelete = null },
            title = { Text("Delete Topic?") },
            text = { Text("Are you sure you want to delete topic «${topicToDelete!!.name}»?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTopic(topicToDelete!!)
                        topicToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { topicToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (subjectToAddTopic != null) {
        AddTopicDialog(
            subject = subjectToAddTopic!!,
            onDismiss = { subjectToAddTopic = null },
            onAdd = { topicName ->
                viewModel.createTopic(
                    subjectToAddTopic!!.id,
                    subjectToAddTopic!!.name,
                    topicName
                )
                subjectToAddTopic = null
            }
        )
    }

    if (subjectToDelete != null) {
        AlertDialog(
            onDismissRequest = { subjectToDelete = null },
            title = { Text("Delete Subject?") },
            text = {
                Text(
                    "Are you sure you want to delete «${subjectToDelete!!.name}»? Its topics and chapters will also be deleted; existing study sessions will remain in history."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubject(subjectToDelete!!)
                        subjectToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { subjectToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (subjects.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.School,
                title = "No subjects added",
                description = "Create your study subjects and topics to organize your focus.",
                actionButtonText = "Create Subject",
                onActionClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 108.dp)
            ) {
                if (chapters.isNotEmpty()) {
                    item {
                        SyllabusOverviewHeaderCard(chapters = chapters)
                    }
                }

                items(subjects, key = { it.id }) { subject ->
                    val subTopics = topics.filter { it.subjectId == subject.id }
                    val studiedMins = studyMinsBySubject[subject.name.trim()] ?: 0L
                    val targetHrs = subject.targetHours.coerceAtLeast(1f)
                    val progress = ((studiedMins / 60f) / targetHrs).coerceIn(0f, 1f)

                    val subChapters = chapters.filter { it.subjectId == subject.id }

                    SubjectDetailCard(
                        subject = subject,
                        topics = subTopics,
                        chapters = subChapters,
                        studiedMins = studiedMins,
                        progress = progress,
                        onEditSubjectClick = { subjectToEdit = subject },
                        onAddTopicClick = { subjectToAddTopic = subject },
                        onAddChapterClick = { subjectToAddChapter = subject },
                        onUpdateChapter = { updatedChapter -> viewModel.updateChapter(updatedChapter) },
                        onTapChapter = { chapter -> viewModel.handleChapterTap(chapter) },
                        onUpdateChapterStatus = { chapter, status -> viewModel.updateChapterStatus(chapter, status) },
                        onEditChapter = { chapter -> chapterToEdit = chapter },
                        onDeleteChapter = { chapter -> chapterToDelete = chapter },
                        onDeleteTopic = { topic -> topicToDelete = topic },
                        onDeleteSubjectClick = { subjectToDelete = subject }
                    )
                }
            }
        }

        if (subjects.isNotEmpty()) {
            AnimatedVisibility(
                visible = isFabVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 12.dp)
            ) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Subject")
                }
            }
        }
    }
}

@Composable
fun SyllabusOverviewHeaderCard(
    chapters: List<ChapterEntity>
) {
    if (chapters.isEmpty()) return

    val totalChapters = chapters.size
    val finishedChapters = chapters.count { it.tapCount >= 3 || it.status == "অধ্যায় Completely শেষ" }
    val inProgressChapters = chapters.count {
        it.status != "পড়া শুরু হয় নাই" &&
            it.status != "অধ্যায় Completely শেষ" &&
            it.tapCount < 3
    }
    val progressPct = if (totalChapters > 0) {
        (finishedChapters.toFloat() / totalChapters * 100f).coerceIn(0f, 100f)
    } else {
        0f
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
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

                    val pendingChapters = (totalChapters - finishedChapters).coerceAtLeast(0)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "সিলেবাস ট্র্যাকার",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "পড়া বাকি: $pendingChapters টি অধ্যায় • শেষ: $finishedChapters টি",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${progressPct.toInt()}% সম্পূর্ণ",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            val animatedProgressPct by animateFloatAsState(
                targetValue = progressPct / 100f,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "subject_chapter_progress"
            )

            LinearProgressIndicator(
                progress = { animatedProgressPct },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "চলছে: $inProgressChapters টি অধ্যায়",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "সম্পূর্ণ শেষ: $finishedChapters / $totalChapters",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SubjectDetailCard(
    subject: SubjectEntity,
    topics: List<TopicEntity>,
    chapters: List<ChapterEntity>,
    studiedMins: Long,
    progress: Float,
    onEditSubjectClick: () -> Unit,
    onAddTopicClick: () -> Unit,
    onAddChapterClick: () -> Unit,
    onUpdateChapter: (ChapterEntity) -> Unit,
    onTapChapter: (ChapterEntity) -> Unit,
    onUpdateChapterStatus: (ChapterEntity, String) -> Unit,
    onEditChapter: (ChapterEntity) -> Unit,
    onDeleteChapter: (ChapterEntity) -> Unit,
    onDeleteTopic: (TopicEntity) -> Unit,
    onDeleteSubjectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subjectColor = parseColorHex(subject.colorHex)
    val studiedHrs = studiedMins / 60L
    val studiedRemMins = studiedMins % 60L

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(subjectColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getSubjectIcon(subject.iconName),
                            contentDescription = null,
                            tint = subjectColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (subject.description.isNotBlank()) {
                            Text(
                                text = subject.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEditSubjectClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Subject",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDeleteSubjectClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val animatedCardProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                label = "subject_card_progress"
            )

            LinearProgressIndicator(
                progress = { animatedCardProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = subjectColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${studiedHrs}h ${studiedRemMins}m studied",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = subjectColor
                )
                Text(
                    text = "Goal: ${subject.targetHours.toInt()}h (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            var isChapterDropdownExpanded by remember { mutableStateOf(false) }

            val pendingSubChapters = chapters.count {
                it.tapCount < 3 && it.status != "অধ্যায় Completely শেষ"
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isChapterDropdownExpanded = !isChapterDropdownExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Syllabus Chapters (${chapters.size})",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "পড়া বাকি: $pendingSubChapters টি অধ্যায়",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onAddChapterClick,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Chapter",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isChapterDropdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand Chapters",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isChapterDropdownExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        if (chapters.isEmpty()) {
                            Text(
                                text = "No chapters added yet. Tap 'Add Chapter' to structure syllabus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                chapters.forEach { chapter ->
                                    ChapterSequentialTrackerView(
                                        chapter = chapter,
                                        onUpdate = onUpdateChapter,
                                        onTap = { onTapChapter(chapter) },
                                        onStatusChange = { status -> onUpdateChapterStatus(chapter, status) },
                                        onEdit = { onEditChapter(chapter) },
                                        onDelete = { onDeleteChapter(chapter) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Optional: show list of topics if needed
            if (topics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Topics",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onAddTopicClick) {
                        Text("Add topic")
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    topics.forEach { topic ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = topic.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(
                                    onClick = { onDeleteTopic(topic) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Topic",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
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

val CHAPTER_STATUS_OPTIONS = listOf(
    "পড়া শুরু হয় নাই",
    "পড়া শুরু হয়েছে",
    "পড়া চলছে",
    "পড়া শেষ পর্যায়ে",
    "পড়া শেষ",
    "রিভিশন চলছে",
    "অধ্যায় Completely শেষ"
)

@Composable
fun ChapterSequentialTrackerView(
    chapter: ChapterEntity,
    onUpdate: (ChapterEntity) -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onTap: (() -> Unit)? = null,
    onStatusChange: ((String) -> Unit)? = null
) {
    var dropdownExpanded by remember { mutableStateOf(false) }

    val currentStatus = chapter.status.ifBlank { "পড়া শুরু হয় নাই" }
    val isFinished = chapter.tapCount >= 3 || currentStatus == "অধ্যায় Completely শেষ"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isFinished) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isFinished) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (onTap != null) {
                        onTap()
                    } else {
                        val now = System.currentTimeMillis()
                        val nextTap = if (chapter.tapCount >= 3) 0 else chapter.tapCount + 1
                        val nextStatus = when (nextTap) {
                            0 -> "পড়া শুরু হয় নাই"
                            1 -> "পড়া শুরু হয়েছে"
                            2 -> "পড়া চলছে"
                            3 -> "অধ্যায় Completely শেষ"
                            else -> currentStatus
                        }

                        val newChapter = chapter.copy(
                            tapCount = nextTap.coerceIn(0, 3),
                            status = nextStatus,
                            lastStudiedTimestamp = now,
                            completedTimestamp = if (nextTap >= 3) now else 0L
                        )
                        onUpdate(newChapter)
                    }
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isFinished) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )

                    Text(
                        text = chapter.chapterName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isFinished) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Chapter",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Chapter",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status: $currentStatus",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isFinished) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Box {
                    OutlinedButton(
                        onClick = { dropdownExpanded = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        CHAPTER_STATUS_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = if (option == currentStatus) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                },
                                onClick = {
                                    dropdownExpanded = false
                                    if (onStatusChange != null) {
                                        onStatusChange(option)
                                    } else {
                                        val now = System.currentTimeMillis()
                                        val newTapCount = when (option) {
                                            "পড়া শুরু হয় নাই" -> 0
                                            "পড়া শুরু হয়েছে" -> 1
                                            "পড়া চলছে" -> 2
                                            "পড়া শেষ পর্যায়ে" -> 2
                                            "পড়া শেষ" -> 2
                                            "রিভিশন চলছে" -> 2
                                            "অধ্যায় Completely শেষ" -> 3
                                            else -> chapter.tapCount
                                        }

                                        val updated = chapter.copy(
                                            status = option,
                                            tapCount = newTapCount,
                                            lastStudiedTimestamp = now,
                                            completedTimestamp = if (newTapCount >= 3) now else 0L
                                        )
                                        onUpdate(updated)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectIconSelector(
    icons: List<String>,
    selectedIcon: String,
    onIconSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icons.forEach { iconName ->
            val isSelected = selectedIcon == iconName
            IconButton(
                onClick = { onIconSelected(iconName) },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
            ) {
                Icon(
                    imageVector = getSubjectIcon(iconName),
                    contentDescription = iconName,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun CreateSubjectDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, colorHex: String, icon: String, targetHrs: Float, desc: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var targetHours by remember { mutableStateOf("100") }
    var selectedColor by remember { mutableStateOf("#6366F1") }
    var selectedIcon by remember { mutableStateOf("School") }

    val colors = listOf(
        "#6366F1", "#10B981", "#F59E0B", "#EC4899",
        "#8B5CF6", "#06B6D4", "#EF4444", "#3B82F6", "#14B8A6"
    )
    val icons = listOf("School", "Code", "Science", "Calculate", "Biotech", "MenuBook", "HistoryEdu", "Psychology", "Language")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name") },
                    placeholder = { Text("e.g. Organic Chemistry") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetHours,
                    onValueChange = { targetHours = it },
                    label = { Text("Target Study Hours") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = parsePositiveHours(targetHours) == null
                )

                Text("Color Theme", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    colors.forEach { c ->
                        val isSelected = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseColorHex(c))
                                .then(
                                    if (isSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Text("Subject Icon", style = MaterialTheme.typography.labelSmall)
                SubjectIconSelector(
                    icons = icons,
                    selectedIcon = selectedIcon,
                    onIconSelected = { selectedIcon = it }
                )
            }
        },
        confirmButton = {
            val validTargetHours = parsePositiveHours(targetHours)
            Button(
                enabled = name.isNotBlank() && validTargetHours != null,
                onClick = {
                    if (name.isNotBlank() && validTargetHours != null) {
                        onCreate(
                            name.trim(),
                            selectedColor,
                            selectedIcon,
                            validTargetHours,
                            desc.trim()
                        )
                    }
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditSubjectDialog(
    subject: SubjectEntity,
    onDismiss: () -> Unit,
    onUpdate: (SubjectEntity) -> Unit
) {
    var name by remember { mutableStateOf(subject.name) }
    var desc by remember { mutableStateOf(subject.description) }
    var targetHours by remember { mutableStateOf(subject.targetHours.toString()) }
    var selectedColor by remember { mutableStateOf(subject.colorHex) }
    var selectedIcon by remember { mutableStateOf(subject.iconName) }

    val colors = listOf(
        "#6366F1", "#10B981", "#F59E0B", "#EC4899",
        "#8B5CF6", "#06B6D4", "#EF4444", "#3B82F6", "#14B8A6"
    )
    val icons = listOf("School", "Code", "Science", "Calculate", "Biotech", "MenuBook", "HistoryEdu", "Psychology", "Language")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                OutlinedTextField(
                    value = targetHours,
                    onValueChange = { targetHours = it },
                    label = { Text("Target Study Hours") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = parsePositiveHours(targetHours) == null
                )

                Text("Color Theme", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    colors.forEach { c ->
                        val isSelected = selectedColor == c
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(parseColorHex(c))
                                .then(
                                    if (isSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                                )
                                .clickable { selectedColor = c }
                        )
                    }
                }

                Text("Subject Icon", style = MaterialTheme.typography.labelSmall)
                SubjectIconSelector(
                    icons = icons,
                    selectedIcon = selectedIcon,
                    onIconSelected = { selectedIcon = it }
                )
            }
        },
        confirmButton = {
            val validTargetHours = parsePositiveHours(targetHours)
            Button(
                enabled = name.isNotBlank() && validTargetHours != null,
                onClick = {
                    if (name.isNotBlank() && validTargetHours != null) {
                        onUpdate(
                            subject.copy(
                                name = name.trim(),
                                description = desc.trim(),
                                targetHours = validTargetHours,
                                colorHex = selectedColor,
                                iconName = selectedIcon
                            )
                        )
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditChapterDialog(
    chapter: ChapterEntity,
    onDismiss: () -> Unit,
    onSave: (ChapterEntity) -> Unit
) {
    var chapterName by remember { mutableStateOf(chapter.chapterName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Chapter Title") },
        text = {
            OutlinedTextField(
                value = chapterName,
                onValueChange = { chapterName = it },
                label = { Text("Chapter Name / Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = chapterName.trim()
                    if (trimmed.isNotBlank()) {
                        onSave(chapter.copy(chapterName = trimmed))
                    }
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddChapterDialog(
    subject: SubjectEntity,
    onDismiss: () -> Unit,
    onAdd: (chapterName: String) -> Unit
) {
    var chapterName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Chapter to «${subject.name}»") },
        text = {
            OutlinedTextField(
                value = chapterName,
                onValueChange = { chapterName = it },
                label = { Text("Chapter Name / Title") },
                placeholder = { Text("e.g. ১. ভৌত জগৎ ও পরিমাপ") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = chapterName.trim()
                    if (trimmed.isNotBlank()) {
                        onAdd(trimmed)
                    }
                }
            ) {
                Text("Add Chapter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddTopicDialog(
    subject: SubjectEntity,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var topicName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Topic to «${subject.name}»") },
        text = {
            OutlinedTextField(
                value = topicName,
                onValueChange = { topicName = it },
                label = { Text("Topic Name") },
                placeholder = { Text("e.g. Chapter 4: Integration") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = topicName.trim()
                    if (trimmed.isNotBlank()) {
                        onAdd(trimmed)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parsePositiveHours(value: String): Float? {
    val parsed = value.trim().toFloatOrNull() ?: return null
    return if (parsed.isFinite() && parsed > 0f) parsed else null
}
