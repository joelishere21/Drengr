package com.drengr.launcher.integrations.media

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import com.drengr.launcher.integrations.notifications.NotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Follows the active media session (Spotify, YouTube Music, podcasts…). Android only shares
 * sessions with apps that hold notification access, so without it [nowPlaying] emits null and
 * the controls fall back to media keys via [AudioController].
 */
@Singleton
class MediaSessionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifications: NotificationRepository,
    private val audio: AudioController,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)

    @Volatile private var active: MediaController? = null

    /** Updates on track, playback and session changes. Collect on the main thread. */
    fun nowPlaying(): Flow<NowPlaying?> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        var current: MediaController? = null
        var artFor: MediaMetadata? = null
        var art: ImageBitmap? = null

        fun emit() {
            val controller = current
            val metadata = controller?.metadata
            if (metadata !== artFor) {
                artFor = metadata
                art = metadata?.artwork()?.asImageBitmap()
            }
            trySend(controller?.toNowPlaying(art))
        }

        lateinit var callback: MediaController.Callback

        fun pick(controllers: List<MediaController>?) {
            // Some apps (e.g. the Google app) keep an empty, idle session open; skip those.
            val list = controllers.orEmpty().filter { it.hasSomethingToShow() }
            // The system lists sessions by priority; prefer one that is actually playing.
            val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            if (best?.sessionToken != current?.sessionToken) {
                current?.unregisterCallback(callback)
                current = best
                best?.registerCallback(callback, handler)
            }
            active = current
            emit()
        }

        callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = emit()
            override fun onPlaybackStateChanged(state: PlaybackState?) = emit()
            override fun onSessionDestroyed() = pick(activeSessions())
        }
        val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }

        val listening = notifications.hasAccess() && runCatching {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, notifications.listenerComponent, handler)
        }.isSuccess
        if (listening) pick(activeSessions()) else trySend(null)

        awaitClose {
            if (listening) sessions.removeOnActiveSessionsChangedListener(sessionsChanged)
            current?.unregisterCallback(callback)
            active = null
        }
    }

    fun playPause() {
        val controller = active ?: return audio.playPause()
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
            controller.transportControls.pause()
        } else {
            controller.transportControls.play()
        }
    }

    fun next() = active?.transportControls?.skipToNext() ?: audio.next()

    fun previous() = active?.transportControls?.skipToPrevious() ?: audio.previous()

    fun seekTo(positionMs: Long) {
        active?.transportControls?.seekTo(positionMs)
    }

    /** Opens the playing app at its player screen when it offers one. */
    fun openPlayer(packageName: String) {
        val sessionActivity = active?.takeIf { it.packageName == packageName }?.sessionActivity
        if (sessionActivity != null) {
            val sent = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    sessionActivity.send(context, 0, null, null, null, null, options.toBundle())
                } else {
                    sessionActivity.send()
                }
            }.isSuccess
            if (sent) return
        }
        context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { context.startActivity(it) } }
    }

    private fun MediaController.hasSomethingToShow(): Boolean {
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        val idle = state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED ||
            state == PlaybackState.STATE_ERROR
        return !idle || metadata?.text(MediaMetadata.METADATA_KEY_TITLE) != null
    }

    private fun activeSessions(): List<MediaController> =
        runCatching { sessions.getActiveSessions(notifications.listenerComponent) }.getOrDefault(emptyList())

    private fun MediaController.toNowPlaying(art: ImageBitmap?): NowPlaying {
        val m = metadata
        val state = playbackState
        val actions = state?.actions ?: 0L
        return NowPlaying(
            packageName = packageName,
            appLabel = appLabel(packageName),
            title = m?.text(MediaMetadata.METADATA_KEY_TITLE) ?: m?.text(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = m?.text(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.text(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            album = m?.text(MediaMetadata.METADATA_KEY_ALBUM),
            art = art,
            durationMs = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = state?.position ?: 0,
            positionUpdatedAt = state?.lastPositionUpdateTime ?: 0,
            speed = state?.playbackSpeed ?: 1f,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            canSeek = actions and PlaybackState.ACTION_SEEK_TO != 0L,
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun MediaMetadata.text(key: String): String? = getText(key)?.toString()?.trim()?.ifEmpty { null }

    /** Album art, scaled down: some apps hand over full-resolution covers. */
    private fun MediaMetadata.artwork(): Bitmap? {
        val bitmap = getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            ?: return null
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_ART_PX) return bitmap
        val factor = MAX_ART_PX.toFloat() / longest
        return bitmap.scale((bitmap.width * factor).toInt().coerceAtLeast(1), (bitmap.height * factor).toInt().coerceAtLeast(1))
    }

    private companion object {
        const val MAX_ART_PX = 512
    }
}
