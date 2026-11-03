package com.drengr.launcher.integrations.notifications

import android.app.Notification
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notification counts per app, fed by [DrengrNotificationListener]. Nothing is read until the
 * user grants notification access; only counts are kept, never notification content.
 */
@Singleton
class NotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val userManager = context.getSystemService(UserManager::class.java)

    val listenerComponent = ComponentName(context, DrengrNotificationListener::class.java)

    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Notification count by [NotificationDots.key]. Empty without notification access. */
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun hasAccess(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    /** Opens the system screen where notification access is granted, as directly as the device allows. */
    fun openAccessSettings() {
        val direct = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, listenerComponent.flattenToString())
        } else {
            null
        }
        val list = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        for (intent in listOfNotNull(direct, list)) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // Some devices drop the detail screen; fall back to the list.
            }
        }
    }

    internal fun publish(notifications: Array<StatusBarNotification>, ranking: NotificationListenerService.RankingMap?) {
        val scratch = NotificationListenerService.Ranking()
        val entries = notifications.map { sbn ->
            NotificationEntry(
                packageName = sbn.packageName,
                userSerial = userManager.getSerialNumberForUser(sbn.user),
                ongoing = sbn.isOngoing,
                groupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
                canShowBadge = ranking?.getRanking(sbn.key, scratch) != true || scratch.canShowBadge(),
            )
        }
        _counts.value = NotificationDots.count(entries)
    }

    internal fun clear() {
        _counts.value = emptyMap()
    }
}
