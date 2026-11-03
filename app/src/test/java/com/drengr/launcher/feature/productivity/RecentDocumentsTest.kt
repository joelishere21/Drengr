package com.drengr.launcher.feature.productivity

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class RecentDocumentsTest {
    private val zone = ZoneId.of("America/Chicago")
    private val now = LocalDateTime.of(2026, 11, 3, 14, 30) // Tuesday
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun label(t: LocalDateTime) = DocumentTimes.label(millis(t), millis(now), zone, Locale.US)

    @Test fun times() {
        assertEquals("Just now", label(now.minusSeconds(20)))
        assertEquals("12 min ago", label(now.minusMinutes(12)))
        assertEquals("3 h ago", label(now.minusHours(3)))
        assertEquals("Yesterday", label(now.minusDays(1)))
        assertEquals("Saturday", label(now.minusDays(3)))
        assertEquals("Sep 4", label(LocalDateTime.of(2026, 9, 4, 9, 0)))
        assertEquals("Sep 4, 2025", label(LocalDateTime.of(2025, 9, 4, 9, 0)))
    }

    @Test fun icons() {
        assertEquals(Icons.Outlined.PictureAsPdf, DocumentIcons.forType("application/pdf"))
        assertEquals(Icons.Outlined.Image, DocumentIcons.forType("image/png"))
        assertEquals(Icons.Outlined.TableChart, DocumentIcons.forType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        assertEquals(Icons.Outlined.Description, DocumentIcons.forType("application/vnd.google-apps.document"))
        assertEquals(Icons.Outlined.InsertDriveFile, DocumentIcons.forType(null))
    }
}
