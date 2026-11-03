package com.drengr.launcher.integrations.system

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The next alarm set in a clock app.
 *
 * Android reports only the single earliest "alarm clock" scheduled by any app, and reminder,
 * calendar or wake-up apps use the same mechanism. So the alarm counts only when the app that
 * created it is a clock app (one that handles the standard alarm intents); otherwise Home shows
 * nothing rather than a time the user never set.
 */
@Singleton
class AlarmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** Trigger time (epoch ms) of the next clock-app alarm, or null. Updates as alarms change. */
    fun nextAlarm(): Flow<Long?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(current())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        trySend(current())
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    /** Reads the alarm now, e.g. after the time passes the one shown. */
    fun current(): Long? {
        val alarm = alarms.nextAlarmClock ?: return null
        val creator = alarm.showIntent?.creatorPackage
        // No show intent means the creator is unknown; most clock apps provide one, so allow it.
        if (creator != null && creator !in clockPackages()) return null
        return alarm.triggerTime
    }

    private fun clockPackages(): Set<String> {
        val pm = context.packageManager
        return listOf(AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM)
            .flatMap { action -> pm.queryIntentActivities(Intent(action), 0) }
            .map { it.activityInfo.packageName }
            .toSet()
    }
}
