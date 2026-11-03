package com.drengr.launcher.feature.home

import android.Manifest
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
import com.drengr.launcher.core.designsystem.LocalLauncherLayout
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.SectionHeader
import com.drengr.launcher.core.designsystem.onWallpaper
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.PageId
import com.drengr.launcher.feature.common.rememberPermissionRequest
import com.drengr.launcher.core.domain.GestureTargets
import com.drengr.launcher.feature.productivity.HomeTasksCard
import com.drengr.launcher.feature.productivity.NotesAndTasksViewModel
import com.drengr.launcher.core.domain.HomeGesture
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.ui.unit.LayoutDirection
import com.drengr.launcher.integrations.weather.GoogleWeather
import com.drengr.launcher.launcher.LauncherNavigator
import com.drengr.launcher.launcher.LauncherUiState
import com.drengr.launcher.launcher.components.AppItem
import java.time.format.DateTimeFormatter

private const val FAVORITE_MAX_ROWS = 3
private val FAVORITE_ROW_HEIGHT = 92.dp
private val PANE_GAP = 32.dp

/**
 * Gestures on empty space: swipe up, swipe down and double-tap do what Settings → Gestures says
 * (by default Command Center and the notification shade); long-press opens Settings.
 */
@Composable
fun HomePage(
    state: LauncherUiState,
    navigator: LauncherNavigator,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val now by rememberCurrentTime()
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()
    val notesAndTasks: NotesAndTasksViewModel = hiltViewModel()
    val notes by notesAndTasks.notes.collectAsStateWithLifecycle()
    val tasks by notesAndTasks.tasks.collectAsStateWithLifecycle()
    val settings by rememberUpdatedState(state.settings)
    val density = LocalDensity.current
    val threshold = with(density) { 96.dp.toPx() }
    // Android's back-gesture strips, at least 24 dp even with button navigation.
    val systemEdge = WindowInsets.systemGestures.let {
        maxOf(it.getLeft(density, LayoutDirection.Ltr), it.getRight(density, LayoutDirection.Ltr))
    }
    val edgePx by rememberUpdatedState(maxOf(systemEdge.toFloat(), with(density) { 24.dp.toPx() }))
    val doubleTapTarget = state.settings.gestureTarget(HomeGesture.DoubleTap)

    val use24h = state.settings.use24Hour ?: DateFormat.is24HourFormat(context)
    val timeFormat = remember(use24h) { DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }
    val alarmAt by viewModel.nextAlarm.collectAsStateWithLifecycle()
    val nextAlarm = remember(alarmAt, now, use24h) { alarmAt?.let { AlarmLabel.format(it, now, use24h) } }
    val layout = LocalLauncherLayout.current
    val agendaLimit = layout.homeAgendaLimit
    val agendaItems = remember(agenda, now, use24h, agendaLimit) {
        (agenda as? AgendaUi.Ready)?.let { Agenda.build(it.events, now, use24h, limit = agendaLimit) }.orEmpty()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    // Minute ticks: refreshes weather once it is 30 minutes old (no-op otherwise).
    LaunchedEffect(now) { viewModel.refreshWeather() }

    val requestCalendar = rememberPermissionRequest(
        permission = Manifest.permission.READ_CALENDAR,
        blockedMessage = "Calendar access is off. Tap Agenda again to open App info → Permissions.",
        onResult = { viewModel.onCalendarPermissionResult() },
    )
    val requestLocation = rememberPermissionRequest(
        permission = Manifest.permission.ACCESS_COARSE_LOCATION,
        blockedMessage = "Location access is off. You can pick a city in Settings → Weather instead.",
        onResult = { granted ->
            if (granted) {
                viewModel.refreshWeather(force = true)
            } else {
                Toast.makeText(context, "No location? Pick a city in Settings → Weather.", Toast.LENGTH_LONG).show()
            }
        },
    )

    val clockStyle = DrengrTheme.typography.clock.let { style ->
        when {
            layout.isShort -> style.copy(fontSize = 56.sp)
            layout.isLargeScreen -> style.copy(fontSize = 96.sp)
            else -> style
        }
    }
    val clockAndStatus: @Composable () -> Unit = {
        // Clock: tap opens the alarm app.
        Column(
            Modifier.clickable(onClickLabel = "Open alarms") {
                runCatching {
                    context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                }
            },
        ) {
            Text(now.format(timeFormat), style = clockStyle.onWallpaper())
            Text(
                now.format(dateFormat),
                style = DrengrTheme.typography.title.onWallpaper(),
                color = DrengrTheme.colors.textSecondary,
            )
        }
        WeatherLine(
            state = weather,
            onClick = {
                when (weather) {
                    WeatherUi.NeedsLocation -> requestLocation()
                    is WeatherUi.Ready -> {
                        if (!GoogleWeather.open(context, viewModel.weatherQuery())) {
                            Toast.makeText(context, "Couldn't open Google weather", Toast.LENGTH_SHORT).show()
                        }
                    }
                    else -> viewModel.refreshWeather(force = true)
                }
            },
            modifier = Modifier.padding(top = 4.dp),
        )
        if (nextAlarm != null) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, null, tint = DrengrTheme.colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(nextAlarm, style = DrengrTheme.typography.mono.onWallpaper(), color = DrengrTheme.colors.accent)
            }
        }
    }
    val cards: @Composable () -> Unit = {
        AgendaCard(
            state = agenda,
            items = agendaItems,
            onRequestAccess = requestCalendar,
            onOpenCalendar = { navigator.runActionById("calendar") },
            onOpenEvent = { event ->
                runCatching { context.startActivity(viewModel.calendarIntent(event)) }
                    .onFailure { navigator.runActionById("calendar") }
            },
        )
        if (tasks.any { !it.done }) {
            Spacer(Modifier.height(10.dp))
            HomeTasksCard(
                tasks = tasks,
                limit = layout.homeTaskLimit,
                onToggle = { notesAndTasks.setDone(it, !it.done) },
                onOpenAll = { navigator.goTo(PageId.Productivity) },
            )
        }
        Spacer(Modifier.height(10.dp))
        val latest = notes.firstOrNull()
        QuickNoteCard(
            note = latest,
            onOpen = { if (latest != null) notesAndTasks.openNote(latest) else notesAndTasks.newNote() },
            onNew = notesAndTasks::newNote,
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                var ignored = false
                detectVerticalDragGestures(
                    onDragStart = { start ->
                        total = 0f
                        ignored = settings.gestureEdgeGuard &&
                            HomeGestures.startsAtEdge(start.x, size.width.toFloat(), edgePx)
                    },
                    onDragEnd = {
                        if (!ignored) {
                            HomeGestures.swipe(total, threshold)?.let { gesture ->
                                navigator.runGestureTarget(settings.gestureTarget(gesture))
                            }
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        if (!ignored) change.consume()
                        total += amount
                    },
                )
            }
            .pointerInput(doubleTapTarget) {
                detectTapGestures(
                    onDoubleTap = if (doubleTapTarget == GestureTargets.NONE) null else {
                        { navigator.runGestureTarget(doubleTapTarget) }
                    },
                    onLongPress = { navigator.openSettings() },
                )
            }
            .padding(horizontal = DrengrTheme.spacing.pageHorizontal),
    ) {
        if (layout.twoPaneHome) {
            // Wider than a phone: clock and favorites on the left, agenda and note on the right.
            // Short windows (phone landscape) scroll the right pane and move favorites into it.
            Spacer(Modifier.height(if (layout.isShort) 8.dp else 36.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(PANE_GAP)) {
                Column(Modifier.weight(1f)) {
                    clockAndStatus()
                    if (!layout.isShort) {
                        Spacer(Modifier.height(24.dp))
                        Favorites(state.favorites, Modifier.weight(1f))
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (layout.isShort) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                ) {
                    cards()
                    if (layout.isShort) {
                        Spacer(Modifier.height(16.dp))
                        Favorites(state.favorites, rowLimit = FAVORITE_MAX_ROWS)
                    }
                }
            }
        } else {
            Spacer(Modifier.height(36.dp))
            clockAndStatus()
            Spacer(Modifier.height(20.dp))
            cards()
            Spacer(Modifier.height(20.dp))
            Favorites(state.favorites, Modifier.weight(1f))
        }
    }
}

/**
 * Shows as many favorite rows as fit (or [rowLimit] rows inside a scrolling pane); the rest stay
 * reachable on the Applications page. Wider panes get more columns.
 */
@Composable
private fun Favorites(favorites: List<AppInfo>, modifier: Modifier = Modifier, rowLimit: Int? = null) {
    val layout = LocalLauncherLayout.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = layout.favoriteColumns(maxWidth.value.toInt())
        val available = maxHeight
        Column {
            SectionHeader("Favorites")
            Spacer(Modifier.height(8.dp))
            if (favorites.isEmpty()) {
                Text(
                    "Long-press any app on the Applications page and choose “Add to favorites”.",
                    style = DrengrTheme.typography.caption.onWallpaper(),
                    color = DrengrTheme.colors.textSecondary,
                )
            } else {
                val rows = rowLimit
                    ?: ((available - 32.dp) / FAVORITE_ROW_HEIGHT).toInt().coerceIn(1, FAVORITE_MAX_ROWS)
                favorites.take(rows * columns).chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app -> AppItem(app, Modifier.weight(1f)) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
