package com.drengr.launcher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.onWallpaper
import com.drengr.launcher.integrations.calendar.CalendarEvent
import com.drengr.launcher.integrations.weather.Weather
import com.drengr.launcher.integrations.weather.WeatherCondition

/** One line under the date: "☁ 12° Cloudy · H 14° L 5° · Oslo". */
@Composable
fun WeatherLine(state: WeatherUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (state == WeatherUi.Hidden) return
    val colors = DrengrTheme.colors
    val type = DrengrTheme.typography
    Row(
        modifier
            .clip(DrengrTheme.shapes.small)
            .clickable(onClickLabel = "Weather", onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            is WeatherUi.Ready -> {
                val w = state.weather
                Icon(w.icon(), null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("${w.temperature}°", style = type.title.onWallpaper(), color = colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    w.summary(),
                    style = type.body.onWallpaper(),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            WeatherUi.NeedsLocation -> {
                Icon(Icons.Outlined.MyLocation, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Show weather · tap to set up", style = type.body.onWallpaper(), color = colors.accent)
            }
            WeatherUi.Loading -> {
                Icon(Icons.Outlined.Thermostat, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Getting weather…", style = type.body.onWallpaper(), color = colors.textSecondary)
            }
            is WeatherUi.Error -> {
                Icon(Icons.Outlined.Thermostat, null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.message} · tap to retry",
                    style = type.caption.onWallpaper(),
                    color = colors.textSecondary,
                    maxLines = 2,
                )
            }
            WeatherUi.Hidden -> Unit
        }
    }
}

private fun Weather.summary(): String = buildList {
    add(condition.label)
    add("H $high° L $low°")
    precipitationChance?.takeIf { it >= 30 }?.let { add("$it% rain") }
    place?.let { add(it) }
}.joinToString(" · ")

private fun Weather.icon(): ImageVector = when (condition) {
    WeatherCondition.Clear -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    WeatherCondition.PartlyCloudy -> Icons.Outlined.FilterDrama
    WeatherCondition.Cloudy, WeatherCondition.Fog -> Icons.Outlined.Cloud
    WeatherCondition.Drizzle -> Icons.Outlined.Grain
    WeatherCondition.Rain -> Icons.Outlined.Umbrella
    WeatherCondition.FreezingRain -> Icons.Outlined.WaterDrop
    WeatherCondition.Snow -> Icons.Outlined.AcUnit
    WeatherCondition.Thunderstorm -> Icons.Outlined.Thunderstorm
    WeatherCondition.Unknown -> Icons.Outlined.Thermostat
}

/** Upcoming events from the device calendars; asks for calendar access on first tap. */
@Composable
fun AgendaCard(
    state: AgendaUi,
    items: List<AgendaItem>,
    onRequestAccess: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = DrengrTheme.colors
    val needsAccess = state == AgendaUi.NeedsPermission
    DrengrCard(
        modifier = modifier.fillMaxWidth(),
        onClick = if (needsAccess) onRequestAccess else onOpenCalendar,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.CalendarToday, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text("Agenda", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = "Open calendar",
                tint = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            needsAccess -> AgendaMessage("Tap to show your upcoming events. Drengr reads your calendar on this phone only.")
            state == AgendaUi.Loading -> AgendaMessage(" ")
            items.isEmpty() -> AgendaMessage("Nothing scheduled in the next 7 days")
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { item -> AgendaRow(item, onClick = { onOpenEvent(item.event) }) }
            }
        }
    }
}

@Composable
private fun AgendaMessage(text: String) {
    Text(
        text,
        style = DrengrTheme.typography.caption,
        color = DrengrTheme.colors.textSecondary,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AgendaRow(item: AgendaItem, onClick: () -> Unit) {
    val colors = DrengrTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DrengrTheme.shapes.small)
            .clickable(onClickLabel = "Open ${item.event.title}", onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(DrengrTheme.shapes.small)
                .background(item.event.color?.let { Color(it) } ?: colors.accent),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.event.title,
                style = DrengrTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(item.day, item.time, item.event.location).joinToString(" · "),
                style = DrengrTheme.typography.caption,
                color = if (item.ongoing) colors.accent else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun QuickNoteCard(note: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    DrengrCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Outlined.Edit, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                note.ifBlank { "Quick note · tap to write something down" },
                style = DrengrTheme.typography.body,
                color = if (note.isBlank()) DrengrTheme.colors.textSecondary else DrengrTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
