package com.drengr.launcher.integrations.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Bound by Android once the user grants notification access. Powers notification dots and
 * lets the Media page see active media sessions. Recounts from scratch on every change, so
 * counts never drift.
 */
@AndroidEntryPoint
class DrengrNotificationListener : NotificationListenerService() {
    @Inject lateinit var repository: NotificationRepository

    override fun onListenerConnected() = refresh(currentRanking)

    override fun onListenerDisconnected() = repository.clear()

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh(rankingMap)

    private fun refresh(ranking: RankingMap?) {
        // Throws or returns null if the system has already unbound us.
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        repository.publish(active, ranking)
    }
}
