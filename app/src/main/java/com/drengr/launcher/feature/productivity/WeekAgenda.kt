package com.drengr.launcher.feature.productivity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.feature.home.AgendaItem
import com.drengr.launcher.feature.home.AgendaRow
import com.drengr.launcher.feature.home.AgendaUi
import com.drengr.launcher.integrations.calendar.CalendarEvent

/** How many events the Productivity agenda lists (Home shows 3). */
const val AGENDA_LIMIT = 12

private const val TODAY = "Today"

/**
 * The next 7 days from the device calendars, grouped under day headings. Today always gets a
 * heading so an empty day reads as "nothing left today" rather than being skipped silently.
 */
@Composable
fun WeekAgenda(
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
            IconBadge(Icons.Outlined.CalendarToday)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Agenda", style = DrengrTheme.typography.title)
                Text("Next 7 days", style = DrengrTheme.typography.caption, color = colors.textSecondary)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Open calendar", tint = colors.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        when {
            needsAccess -> Message("Tap to show your events here. Drengr reads your calendar on this phone only.")
            state == AgendaUi.Loading -> Message(" ")
            items.isEmpty() -> Message("Nothing scheduled in the next 7 days")
            else -> {
                val byDay = items.groupBy { it.day }
                if (TODAY !in byDay) {
                    DayHeading(TODAY)
                    Message("Nothing else today")
                }
                byDay.forEach { (day, dayItems) ->
                    DayHeading(day)
                    dayItems.forEach { item ->
                        AgendaRow(item, onClick = { onOpenEvent(item.event) }, showDay = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeading(day: String) {
    Text(
        day.uppercase(),
        style = DrengrTheme.typography.label,
        color = if (day == TODAY) DrengrTheme.colors.accent else DrengrTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = DrengrTheme.typography.caption,
        color = DrengrTheme.colors.textSecondary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
