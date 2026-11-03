package com.drengr.launcher.integrations.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTest {
    private fun playing(position: Long, at: Long, isPlaying: Boolean = true, duration: Long = 200_000, speed: Float = 1f) =
        NowPlaying(
            packageName = "p", appLabel = "App", title = "T", artist = null, album = null, art = null,
            durationMs = duration, positionMs = position, positionUpdatedAt = at, speed = speed,
            isPlaying = isPlaying, canSkipNext = true, canSkipPrevious = true, canSeek = true,
        )

    @Test fun extrapolatesWhilePlaying() {
        assertEquals(15_000, playing(10_000, at = 1_000).positionAt(6_000))
        assertEquals(20_000, playing(10_000, at = 1_000, speed = 2f).positionAt(6_000))
    }

    @Test fun holdsWhilePaused() {
        assertEquals(10_000, playing(10_000, at = 1_000, isPlaying = false).positionAt(60_000))
    }

    @Test fun clampsToDuration() {
        assertEquals(200_000, playing(195_000, at = 0).positionAt(60_000))
    }

    @Test fun formatsTimes() {
        assertEquals("0:00", NowPlaying.formatTime(0))
        assertEquals("3:07", NowPlaying.formatTime(187_400))
        assertEquals("1:02:45", NowPlaying.formatTime(3_765_000))
    }
}
