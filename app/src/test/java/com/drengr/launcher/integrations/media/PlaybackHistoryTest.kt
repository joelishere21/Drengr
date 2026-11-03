package com.drengr.launcher.integrations.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackHistoryTest {
    private fun item(title: String, at: Long, pkg: String = "com.spotify.music") = PlayedItem(title, "Artist", pkg, "Spotify", at)

    @Test fun replayMovesUpWithoutDuplicates() {
        var list = PlaybackHistory.record(emptyList(), item("A", 1))
        list = PlaybackHistory.record(list, item("B", 2))
        list = PlaybackHistory.record(list, item("A", 3))
        assertEquals(listOf("A", "B"), list.map { it.title })
        assertEquals(3, list.first().playedAt)
        // Same title in another app is a different entry.
        assertEquals(3, PlaybackHistory.record(list, item("A", 4, pkg = "other")).size)
    }

    @Test fun capped() {
        val list = (1..15).fold(emptyList<PlayedItem>()) { acc, n -> PlaybackHistory.record(acc, item("T$n", n.toLong())) }
        assertEquals(PlaybackHistory.MAX_ITEMS, list.size)
        assertEquals("T15", list.first().title)
    }

    @Test fun roundTrip() {
        val list = listOf(item("A", 1), item("B", 2).copy(artist = null))
        assertEquals(list, PlaybackHistory.decode(PlaybackHistory.encode(list)))
        assertTrue(PlaybackHistory.decode("[{}]").isEmpty())
    }

    @Test fun podcastApps() {
        assertTrue(PodcastApps.isPodcastApp("au.com.shiftyjelly.pocketcasts", "Pocket Casts"))
        assertTrue(PodcastApps.isPodcastApp("com.example.x", "My Podcasts"))
        assertFalse(PodcastApps.isPodcastApp("com.spotify.music", "Spotify"))
    }
}
