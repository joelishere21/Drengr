package com.drengr.launcher.integrations.notifications

import com.drengr.launcher.integrations.media.MediaSessionRepository
import com.drengr.launcher.integrations.media.PlaybackHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

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
    @Inject lateinit var media: MediaSessionRepository
    @Inject lateinit var history: PlaybackHistoryRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recording: Job? = null

    override fun onListenerConnected() {
        refresh(currentRanking)
        recordPlayback()
    }

    override fun onListenerDisconnected() {
        repository.clear()
        recording?.cancel()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Notes each track once it starts playing, for the Media page's "Recently played". */
    private fun recordPlayback() {
        recording?.cancel()
        recording = scope.launch {
            media.nowPlaying()
                .filter { it != null && it.isPlaying && it.title != null }
                .distinctUntilChangedBy { "${it?.packageName}|${it?.title}|${it?.artist}" }
                .collect { playing -> playing?.let { runCatching { history.record(it) } } }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) = refresh(rankingMap)

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) = refresh(rankingMap)

    private fun refresh(ranking: RankingMap?) {
        // Throws or returns null if the system has already unbound us.
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        repository.publish(active, ranking)
    }
}
