package com.drengr.launcher.integrations.media

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** What the active media session is playing. Positions are in ms; times use elapsedRealtime. */
@Immutable
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val art: ImageBitmap?,
    val durationMs: Long,
    val positionMs: Long,
    val positionUpdatedAt: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    val canSeek: Boolean,
) {
    /** The playback position at [now], extrapolated from the last update while playing. */
    fun positionAt(now: Long): Long {
        val elapsed = if (isPlaying) ((now - positionUpdatedAt) * speed).toLong() else 0L
        val position = (positionMs + elapsed).coerceAtLeast(0)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }

    companion object {
        /** "3:07", or "1:02:45" past an hour. */
        fun formatTime(ms: Long): String {
            val total = (ms / 1000).coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
        }
    }
}
