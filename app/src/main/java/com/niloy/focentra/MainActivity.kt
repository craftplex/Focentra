package com.niloy.focentra

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.niloy.focentra.ui.components.FocentraFloatingNavBar
import com.niloy.focentra.ui.screens.*
import com.niloy.focentra.ui.theme.FocentraTheme
import com.niloy.focentra.service.StudyReminderScheduler
import com.niloy.focentra.viewmodel.MainViewModel
import com.niloy.focentra.viewmodel.NavigationTab

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent?.getStringExtra("navigate_to") == "NOTIFICATIONS") {
            viewModel.navigateTo(NavigationTab.NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.retrySystemNotifications()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()

        setContent {
            val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val isFullScreenFocus by viewModel.isFullScreenFocus.collectAsStateWithLifecycle()
            val timerState by viewModel.timerEngine.uiState.collectAsStateWithLifecycle()
            val reminders by viewModel.reminders.collectAsStateWithLifecycle()
            val chapters by viewModel.chapters.collectAsStateWithLifecycle()
            val activeNotifications by viewModel.activeNotifications.collectAsStateWithLifecycle()
            val revisionSkips by viewModel.dbRevisionSkips.collectAsStateWithLifecycle()
            val revisionHistory by viewModel.revisionHistory.collectAsStateWithLifecycle()
            val sessions by viewModel.sessions.collectAsStateWithLifecycle()
            val hasMandatoryRevision = remember(
                activeNotifications,
                revisionSkips,
                revisionHistory,
                sessions
            ) {
                viewModel.pendingMandatoryRevision() != null
            }

            val isBottomBarVisible = remember { mutableStateOf(true) }
            val nestedScrollConnection = remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                        if (available.y < -15f) {
                            isBottomBarVisible.value = false
                        } else if (available.y > 15f) {
                            isBottomBarVisible.value = true
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                }
            }

            // Keep screen on during active focus sessions
            DisposableEffect(timerState.status) {
                if (timerState.status == com.niloy.focentra.engine.TimerStatus.RUNNING && timerState.keepScreenAwake) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // Notification permission request for Android 13+
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) viewModel.retrySystemNotifications()
            }

            LaunchedEffect(reminders, chapters) {
                StudyReminderScheduler.scheduleAll(context, reminders, chapters)
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

            }

            // Handle phone back button for all sub-screens
            if (currentTab != NavigationTab.DASHBOARD) {
                BackHandler {
                    viewModel.navigateTo(NavigationTab.DASHBOARD)
                }
            }

            FocentraTheme(themeName = currentTheme) {
                Crossfade(
                    targetState = isFullScreenFocus,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                    label = "focus_mode_transition"
                ) { showFocusMode ->
                if (showFocusMode) {
                    FullScreenFocusScreen(viewModel = viewModel)
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
                        topBar = {
                            TopAppBar(
                                title = {
                                    if (currentTab == NavigationTab.DASHBOARD) {
                                        Text(
                                            text = "Focentra",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = (-0.8).sp,
                                                fontSize = 22.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    } else {
                                        val isSubPage = currentTab == NavigationTab.SUBJECTS ||
                                                currentTab == NavigationTab.ACHIEVEMENTS

                                        Text(
                                            text = when (currentTab) {
                                                NavigationTab.DASHBOARD -> "Focentra"
                                                NavigationTab.TIMER -> "Study Timer"
                                                NavigationTab.ANALYTICS -> "Analytics & Insights"
                                                NavigationTab.CALENDAR -> "Study Calendar"
                                                NavigationTab.HISTORY -> "Session History"
                                                NavigationTab.SUBJECTS -> "Subjects & Topics"
                                                NavigationTab.ACHIEVEMENTS -> "Achievements"
                                                NavigationTab.PRESETS -> "Study Presets"
                                                NavigationTab.SETTINGS -> "Settings"
                                                NavigationTab.NOTIFICATIONS -> "Notification"
                                                NavigationTab.REVISION_HUB -> "Revision Hub"
                                            },
                                            style = if (isSubPage || currentTab == NavigationTab.ANALYTICS) {
                                                MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 17.sp,
                                                    letterSpacing = (-0.2).sp
                                                )
                                            } else {
                                                MaterialTheme.typography.titleLarge.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = (-0.3).sp
                                                )
                                            },
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                },
                                navigationIcon = {
                                    if (currentTab != NavigationTab.DASHBOARD &&
                                        currentTab != NavigationTab.TIMER &&
                                        currentTab != NavigationTab.ANALYTICS &&
                                        currentTab != NavigationTab.CALENDAR &&
                                        currentTab != NavigationTab.HISTORY
                                    ) {
                                        IconButton(onClick = { viewModel.navigateTo(NavigationTab.DASHBOARD) }) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                contentDescription = "Back"
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    val isMainScreen = currentTab == NavigationTab.DASHBOARD ||
                                            currentTab == NavigationTab.TIMER ||
                                            currentTab == NavigationTab.ANALYTICS ||
                                            currentTab == NavigationTab.CALENDAR ||
                                            currentTab == NavigationTab.HISTORY ||
                                            currentTab == NavigationTab.SETTINGS ||
                                            currentTab == NavigationTab.PRESETS

                                    val activeNotifications by viewModel.activeNotifications.collectAsStateWithLifecycle()
                                    val unseenCount by viewModel.unseenNotificationCount.collectAsStateWithLifecycle()

                                    if (isMainScreen) {
                                        // Notification Bell Icon with unseen counter badge
                                        IconButton(onClick = { viewModel.navigateTo(NavigationTab.NOTIFICATIONS) }) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = if (unseenCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                                    contentDescription = "Notifications",
                                                    tint = if (unseenCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (unseenCount > 0) {
                                                    val countText = if (unseenCount > 9) "9+" else unseenCount.toString()
                                                    Box(
                                                        modifier = Modifier
                                                            .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                                            .background(MaterialTheme.colorScheme.error)
                                                            .align(Alignment.TopEnd)
                                                            .padding(horizontal = 3.dp, vertical = 1.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = countText,
                                                            color = MaterialTheme.colorScheme.onError,
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                lineHeight = 9.sp
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // Subjects Quick Access Icon
                                        IconButton(onClick = { viewModel.navigateTo(NavigationTab.SUBJECTS) }) {
                                            Icon(
                                                imageVector = Icons.Default.School,
                                                contentDescription = "Subjects",
                                                tint = if (currentTab == NavigationTab.SUBJECTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Achievements Trophy Icon
                                        IconButton(onClick = { viewModel.navigateTo(NavigationTab.ACHIEVEMENTS) }) {
                                            Icon(
                                                imageVector = Icons.Default.EmojiEvents,
                                                contentDescription = "Achievements",
                                                tint = if (currentTab == NavigationTab.ACHIEVEMENTS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // More Menu items (hidden when in Revision Hub, Subjects, or Achievements)
                                    if (currentTab != NavigationTab.REVISION_HUB &&
                                        currentTab != NavigationTab.SUBJECTS &&
                                        currentTab != NavigationTab.ACHIEVEMENTS
                                    ) {
                                        var showMenu by remember { mutableStateOf(false) }
                                        IconButton(onClick = { showMenu = true }) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "More",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showMenu,
                                            onDismissRequest = { showMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Revision Hub") },
                                                leadingIcon = { Icon(Icons.Default.Autorenew, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.navigateTo(NavigationTab.REVISION_HUB)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Study Presets") },
                                                leadingIcon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.navigateTo(NavigationTab.PRESETS)
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Settings & Backup") },
                                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                                onClick = {
                                                    showMenu = false
                                                    viewModel.navigateTo(NavigationTab.SETTINGS)
                                                }
                                            )
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.background,
                                    titleContentColor = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            AnimatedContent(
                                targetState = currentTab,
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
                                label = "tab_navigation",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = innerPadding.calculateTopPadding())
                            ) { tab ->
                                when (tab) {
                                    NavigationTab.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                                    NavigationTab.TIMER -> TimerScreen(viewModel = viewModel)
                                    NavigationTab.ANALYTICS -> AnalyticsScreen(viewModel = viewModel)
                                    NavigationTab.CALENDAR -> CalendarScreen(viewModel = viewModel)
                                    NavigationTab.HISTORY -> HistoryScreen(viewModel = viewModel)
                                    NavigationTab.SUBJECTS -> SubjectsScreen(viewModel = viewModel, isFabVisible = isBottomBarVisible.value)
                                    NavigationTab.ACHIEVEMENTS -> AchievementsScreen(viewModel = viewModel)
                                    NavigationTab.PRESETS -> PresetsScreen(viewModel = viewModel, isFabVisible = isBottomBarVisible.value)
                                    NavigationTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                                    NavigationTab.NOTIFICATIONS -> NotificationScreen(viewModel = viewModel)
                                    NavigationTab.REVISION_HUB -> RevisionHubScreen(viewModel = viewModel)
                                }
                            }

                            val shouldShowBottomBar = currentTab != NavigationTab.SUBJECTS &&
                                    currentTab != NavigationTab.ACHIEVEMENTS &&
                                    currentTab != NavigationTab.PRESETS &&
                                    currentTab != NavigationTab.NOTIFICATIONS &&
                                    currentTab != NavigationTab.REVISION_HUB &&
                                    !(currentTab == NavigationTab.TIMER &&
                                        hasMandatoryRevision &&
                                        timerState.status == com.niloy.focentra.engine.TimerStatus.IDLE)

                            AnimatedVisibility(
                                visible = isBottomBarVisible.value && shouldShowBottomBar,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                FocentraFloatingNavBar(
                                    currentTab = currentTab,
                                    onTabSelected = { tab ->
                                        viewModel.navigateTo(tab)
                                    }
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
