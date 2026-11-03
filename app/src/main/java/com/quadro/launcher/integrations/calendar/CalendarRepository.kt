package com.quadro.launcher.integrations.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Instances
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One occurrence of a calendar event. All-day events carry dates only. */
@Immutable
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int?,
    /** Local dates covered: for all-day events taken from the UTC dates the provider stores. */
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
)

/**
 * Reads upcoming events from the device calendars (Google, Samsung, Exchange…) via
 * CalendarContract. Needs READ_CALENDAR, which the Home page requests only when the user
 * taps the Agenda card.
 */
@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Emits on subscribe and whenever any calendar changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()

    /** Events that haven't ended yet, starting within [days] days, soonest first. */
    fun upcoming(days: Long = 7): Flow<List<CalendarEvent>> =
        changes().map { query(days) }.flowOn(Dispatchers.IO)

    fun query(days: Long = 7): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        // Start the window a day early so all-day events (stored in UTC) and ongoing events are included.
        val windowStart = now.minusDays(1).toInstant().toEpochMilli()
        val windowEnd = now.truncatedTo(ChronoUnit.DAYS).plusDays(days + 1).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, windowStart)
            ContentUris.appendId(it, windowEnd)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.DISPLAY_COLOR,
        )
        val selection = "${Instances.VISIBLE} = 1 AND ${Instances.SELF_ATTENDEE_STATUS} != ${Attendees.ATTENDEE_STATUS_DECLINED}"

        val events = mutableListOf<CalendarEvent>()
        val cursor = runCatching {
            context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")
        }.getOrNull() ?: return emptyList()
        cursor.use { c ->
            while (c.moveToNext()) {
                val begin = c.getLong(2)
                val end = c.getLong(3).coerceAtLeast(begin)
                val allDay = c.getInt(4) == 1
                val startDate: LocalDate
                val endDate: LocalDate
                if (allDay) {
                    // All-day events are stored as UTC midnights; the end is exclusive.
                    startDate = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                    endDate = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1)
                        .coerceAtLeast(startDate)
                } else {
                    startDate = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate()
                    endDate = Instant.ofEpochMilli(end).atZone(zone).toLocalDate()
                }
                events += CalendarEvent(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)",
                    begin = begin,
                    end = end,
                    allDay = allDay,
                    location = c.getString(5)?.takeIf { it.isNotBlank() },
                    color = if (c.isNull(6)) null else c.getInt(6),
                    startDate = startDate,
                    endDateInclusive = endDate,
                )
            }
        }
        return events
    }

    /** Opens the event in the user's calendar app. */
    fun viewIntent(event: CalendarEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.eventId))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
