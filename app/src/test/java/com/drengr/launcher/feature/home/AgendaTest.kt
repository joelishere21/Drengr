package com.drengr.launcher.feature.home

import com.drengr.launcher.integrations.calendar.CalendarEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class AgendaTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = LocalDateTime.of(2026, 11, 2, 14, 30) // Monday 2:30 PM

    private fun at(hour: Int, minute: Int = 0): LocalDateTime = now.withHour(hour).withMinute(minute)

    private fun timed(title: String, start: LocalDateTime, end: LocalDateTime) = CalendarEvent(
        eventId = title.hashCode().toLong(),
        title = title,
        begin = start.atZone(zone).toInstant().toEpochMilli(),
        end = end.atZone(zone).toInstant().toEpochMilli(),
        allDay = false,
        location = null,
        color = null,
        startDate = start.toLocalDate(),
        endDateInclusive = end.toLocalDate(),
    )

    /** All-day events are stored as UTC midnights with an exclusive end, like CalendarContract. */
    private fun allDay(title: String, first: LocalDate, lastInclusive: LocalDate) = CalendarEvent(
        eventId = title.hashCode().toLong(),
        title = title,
        begin = first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        end = lastInclusive.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true,
        location = null,
        color = null,
        startDate = first,
        endDateInclusive = lastInclusive,
    )

    private fun build(vararg events: CalendarEvent, use24h: Boolean = false) =
        Agenda.build(events.toList(), now, use24h, zone = zone, locale = Locale.US)

    @Test fun finishedEventsAreDropped() {
        val items = build(
            timed("Standup", at(9), at(9, 15)),
            timed("Review", at(16), at(17)),
        )
        assertEquals(listOf("Review"), items.map { it.event.title })
    }

    @Test fun ongoingEventShowsUntil() {
        val item = build(timed("Workshop", at(14), at(15, 30))).single()
        assertTrue(item.ongoing)
        assertEquals("Now · until 3:30 PM", item.time)
        assertEquals("Today", item.day)
    }

    @Test fun allDayEventsComeFirstWithinADay() {
        val items = build(
            timed("Review", at(16), at(17)),
            allDay("Holiday", now.toLocalDate(), now.toLocalDate()),
        )
        assertEquals(listOf("Holiday", "Review"), items.map { it.event.title })
        assertEquals("All day", items.first().time)
    }

    @Test fun multiDayAllDayEventStaysUntilItsLastDay() {
        val trip = allDay("Trip", now.toLocalDate().minusDays(2), now.toLocalDate())
        val item = build(trip).single()
        assertEquals("Today", item.day)
        val over = allDay("Old trip", now.toLocalDate().minusDays(3), now.toLocalDate().minusDays(1))
        assertTrue(build(over).isEmpty())
    }

    @Test fun laterDaysAreLabelled() {
        val tomorrow = at(10).plusDays(1)
        val wednesday = at(10).plusDays(2)
        val items = build(
            timed("Dentist", wednesday, wednesday.plusHours(1)),
            timed("Lunch", tomorrow, tomorrow.plusHours(1)),
        )
        assertEquals(listOf("Tomorrow", "Wed, Nov 4"), items.map { it.day })
        assertEquals("10:00 AM – 11:00 AM", items.first().time)
    }

    @Test fun respects24HourClock() {
        val item = build(timed("Review", at(16), at(17)), use24h = true).single()
        assertEquals("16:00 – 17:00", item.time)
    }

    @Test fun limitsCount() {
        val events = (1..6).map { timed("E$it", now.plusHours(it.toLong()), now.plusHours(it + 1L)) }
        assertEquals(4, Agenda.build(events, now, false, zone = zone, locale = Locale.US).size)
    }
}
