package com.quadro.launcher.feature.home

import androidx.compose.runtime.Immutable
import com.quadro.launcher.integrations.calendar.CalendarEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Immutable
data class AgendaItem(
    val event: CalendarEvent,
    /** "Today", "Tomorrow" or e.g. "Wed, Nov 4". */
    val day: String,
    /** "All day", "Now · until 3:30 PM" or "2:00 – 3:00 PM". */
    val time: String,
    val ongoing: Boolean,
)

/**
 * Turns raw calendar instances into what the Home Agenda shows: events not yet over, all-day
 * events first within each day, then by start time. Pure, so it is unit-tested.
 */
object Agenda {
    fun build(
        events: List<CalendarEvent>,
        now: LocalDateTime,
        use24h: Boolean,
        limit: Int = 4,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<AgendaItem> {
        val today = now.toLocalDate()
        val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
        val time = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)

        return events
            .filter { if (it.allDay) it.endDateInclusive >= today else it.end > nowMillis }
            .sortedWith(
                compareBy<CalendarEvent> { maxOf(it.startDate, today) }
                    .thenBy { !it.allDay }
                    .thenBy { it.begin },
            )
            .take(limit)
            .map { event ->
                val shownDate = maxOf(event.startDate, today)
                val ongoing = !event.allDay && event.begin <= nowMillis
                val start = Instant.ofEpochMilli(event.begin).atZone(zone)
                val end = Instant.ofEpochMilli(event.end).atZone(zone)
                AgendaItem(
                    event = event,
                    day = dayLabel(shownDate, today, locale),
                    time = when {
                        event.allDay -> "All day"
                        ongoing -> "Now · until " + end.format(time)
                        start.toLocalDate() != end.toLocalDate() -> start.format(time) + " – " + end.format(
                            DateTimeFormatter.ofPattern(if (use24h) "EEE HH:mm" else "EEE h:mm a", locale),
                        )
                        else -> start.format(time) + " – " + end.format(time)
                    },
                    ongoing = ongoing,
                )
            }
    }

    fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + ", " +
            date.format(DateTimeFormatter.ofPattern("MMM d", locale))
    }
}
