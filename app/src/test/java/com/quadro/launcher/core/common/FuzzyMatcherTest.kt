package com.quadro.launcher.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {

    private fun score(q: String, t: String) = FuzzyMatcher.score(q, t)

    @Test fun emptyQueryMatchesEverything() = assertEquals(0, score("", "Chrome"))

    @Test fun exactBeatsPrefix() = assertTrue(score("maps", "Maps")!! > score("maps", "Maps Go")!!)

    @Test fun prefixBeatsWordPrefix() = assertTrue(score("go", "Gmail Go")!! < score("go", "Google")!!)

    @Test fun wordPrefixMatches() = assertNotNull(score("maps", "Google Maps"))

    @Test fun initialsMatch() = assertEquals(700, score("gm", "Google Maps"))

    @Test fun substringBeatsSubsequence() =
        assertTrue(score("ram", "Telegram")!! > score("tlg", "Telegram")!!)

    @Test fun subsequenceMatchesTypos() = assertNotNull(score("spfy", "Spotify"))

    @Test fun nonMatchIsNull() = assertNull(score("xyz", "Calendar"))

    @Test fun isCaseInsensitive() = assertEquals(score("CAL", "calendar"), score("cal", "Calendar"))

    @Test fun keywordsAreDiscounted() {
        val viaKeyword = FuzzyMatcher.score("torch", "Toggle flashlight", listOf("torch"))!!
        assertEquals(800, viaKeyword) // 1000 * 0.8
        val viaLabel = FuzzyMatcher.score("toggle", "Toggle flashlight", listOf("torch"))!!
        assertTrue(viaLabel > viaKeyword)
    }

    @Test fun rankingOrdersSensibly() {
        val apps = listOf("Camera", "Calendar", "Calculator", "Google Calendar", "Discord")
        val ranked = apps.mapNotNull { a -> score("cal", a)?.let { a to it } }.sortedByDescending { it.second }.map { it.first }
        assertEquals(listOf("Calendar", "Calculator", "Google Calendar"), ranked.take(3))
        assertTrue("Discord" !in ranked)
    }
}
