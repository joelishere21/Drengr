package com.quadro.launcher.feature.home

import android.app.AlarmManager
import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quadro.launcher.core.designsystem.IconBadge
import com.quadro.launcher.core.designsystem.QuadroCard
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.core.designsystem.SectionHeader
import com.quadro.launcher.core.domain.PageId
import com.quadro.launcher.integrations.battery.BatteryState
import com.quadro.launcher.integrations.connectivity.NetworkState
import com.quadro.launcher.integrations.connectivity.NetworkType
import com.quadro.launcher.launcher.LauncherNavigator
import com.quadro.launcher.launcher.LauncherUiState
import com.quadro.launcher.launcher.components.AppItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val FAVORITE_COLUMNS = 4
private const val FAVORITE_MAX = 12

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
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle()
    val now by rememberCurrentTime()
    val swipeDownEnabled by rememberUpdatedState(state.settings.swipeDownForCommandCenter)
    val threshold = with(LocalDensity.current) { 96.dp.toPx() }

    val use24h = state.settings.use24Hour ?: DateFormat.is24HourFormat(context)
    val timeFormat = remember(use24h) { DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm") }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, MMMM d") }
    val nextAlarm = remember(now, use24h) { nextAlarmText(context, use24h) }

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
            .padding(horizontal = QuadroTheme.spacing.pageHorizontal),
    ) {
        StatusRow(battery, network, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(40.dp))

        // Clock: tap opens the alarm app.
        Column(
            Modifier.clickable(onClickLabel = "Open alarms") {
                runCatching {
                    context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
                }
            },
        ) {
            Text(now.format(timeFormat), style = QuadroTheme.typography.clock)
            Text(
                now.format(dateFormat),
                style = QuadroTheme.typography.title,
                color = QuadroTheme.colors.textSecondary,
            )
            if (nextAlarm != null) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Alarm, null, tint = QuadroTheme.colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(nextAlarm, style = QuadroTheme.typography.mono, color = QuadroTheme.colors.accent)
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlanceCard(
                icon = Icons.Outlined.CalendarToday,
                title = "Agenda",
                body = "Open your calendar",
                modifier = Modifier.weight(1f),
                onClick = { navigator.runActionById("calendar") },
            )
            GlanceCard(
                icon = Icons.Outlined.Edit,
                title = "Quick note",
                body = state.settings.quickNote.ifBlank { "Tap to write something down" },
                modifier = Modifier.weight(1f),
                onClick = { navigator.goTo(PageId.Productivity) },
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionHeader("Favorites")
        Spacer(Modifier.height(8.dp))

        if (state.favorites.isEmpty()) {
            Text(
                "Long-press any app on the Applications page and choose “Add to favorites”.",
                style = QuadroTheme.typography.caption,
                color = QuadroTheme.colors.textSecondary,
            )
        } else {
            state.favorites.take(FAVORITE_MAX).chunked(FAVORITE_COLUMNS).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { app -> AppItem(app, Modifier.weight(1f)) }
                    repeat(FAVORITE_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusRow(battery: BatteryState, network: NetworkState, modifier: Modifier = Modifier) {
    val colors = QuadroTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("QUADRO", style = QuadroTheme.typography.label, color = colors.accent, modifier = Modifier.weight(1f))
        Icon(network.icon(), contentDescription = network.type.label, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(12.dp))
        Icon(
            if (battery.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
            contentDescription = null,
            tint = if (battery.level <= 15 && !battery.isCharging) colors.accent else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("${battery.level}%", style = QuadroTheme.typography.mono, color = colors.textSecondary)
    }
}

@Composable
private fun GlanceCard(
    icon: ImageVector,
    title: String,
    body: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuadroCard(modifier = modifier.heightIn(min = 112.dp), onClick = onClick) {
        IconBadge(icon, size = 32.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = QuadroTheme.typography.title)
        Text(
            body,
            style = QuadroTheme.typography.caption,
            color = QuadroTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun NetworkState.icon(): ImageVector = when (type) {
    NetworkType.Wifi -> Icons.Outlined.Wifi
    NetworkType.Cellular -> Icons.Outlined.SignalCellularAlt
    NetworkType.Ethernet -> Icons.Outlined.SettingsEthernet
    NetworkType.Vpn -> Icons.Outlined.VpnKey
    NetworkType.Other -> Icons.Outlined.Wifi
    NetworkType.None -> Icons.Outlined.SignalWifiOff
}

private fun nextAlarmText(context: android.content.Context, use24h: Boolean): String? {
    val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
    val time = Instant.ofEpochMilli(alarm.triggerTime).atZone(ZoneId.systemDefault())
    val pattern = if (use24h) "EEE HH:mm" else "EEE h:mm a"
    return "Alarm " + time.format(DateTimeFormatter.ofPattern(pattern))
}
