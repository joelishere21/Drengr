package com.drengr.launcher.feature.home

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import com.drengr.launcher.launcher.LauncherNavigator
import com.drengr.launcher.launcher.LauncherUiState
import com.drengr.launcher.launcher.components.AppItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val FAVORITE_COLUMNS = 4
private const val FAVORITE_MAX_ROWS = 3
private const val AGENDA_LIMIT = 3
private val FAVORITE_ROW_HEIGHT = 92.dp

/**
 * Gestures: swipe down → Command Center (configurable), swipe up → Applications,
 * long-press empty space → page editing (Settings).
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
    val swipeDownEnabled by rememberUpdatedState(state.settings.swipeDownForCommandCenter)
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }

    val use24h = state.settings.use24Hour ?: DateFormat.is24HourFormat(context)
    val timeFormat = remember(use24h) { DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }
    val nextAlarm = remember(now, use24h) { nextAlarmText(context, use24h) }
    val agendaItems = remember(agenda, now, use24h) {
        (agenda as? AgendaUi.Ready)?.let { Agenda.build(it.events, now, use24h, limit = AGENDA_LIMIT) }.orEmpty()
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

    Column(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total > threshold && swipeDownEnabled -> navigator.openCommandCenter()
                            total < -threshold -> navigator.goTo(PageId.Applications)
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { navigator.openSettings() }) }
            .padding(horizontal = DrengrTheme.spacing.pageHorizontal),
    ) {
        Spacer(Modifier.height(36.dp))

        // Clock: tap opens the alarm app.
        Column(
            Modifier.clickable(onClickLabel = "Open alarms") {
                runCatching {
                    context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                }
            },
        ) {
            Text(now.format(timeFormat), style = DrengrTheme.typography.clock.onWallpaper())
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

        Spacer(Modifier.height(20.dp))
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
        Spacer(Modifier.height(10.dp))
        QuickNoteCard(
            note = state.settings.quickNote,
            onClick = { navigator.goTo(PageId.Productivity) },
        )

        Spacer(Modifier.height(20.dp))
        Favorites(state.favorites, Modifier.weight(1f))
    }
}

/** Shows as many favorite rows as fit; the rest stay reachable on the Applications page. */
@Composable
private fun Favorites(favorites: List<AppInfo>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
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
                val rows = ((available - 32.dp) / FAVORITE_ROW_HEIGHT).toInt().coerceIn(1, FAVORITE_MAX_ROWS)
                favorites.take(rows * FAVORITE_COLUMNS).chunked(FAVORITE_COLUMNS).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { app -> AppItem(app, Modifier.weight(1f)) }
                        repeat(FAVORITE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

private fun nextAlarmText(context: android.content.Context, use24h: Boolean): String? {
    val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
    val time = Instant.ofEpochMilli(alarm.triggerTime).atZone(ZoneId.systemDefault())
    val pattern = if (use24h) "EEE HH:mm" else "EEE h:mm a"
    return "Alarm " + time.format(DateTimeFormatter.ofPattern(pattern))
}
