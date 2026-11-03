package com.drengr.launcher.integrations.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDotsTest {
    private fun entry(
        pkg: String,
        user: Long = 0,
        ongoing: Boolean = false,
        summary: Boolean = false,
        badge: Boolean = true,
    ) = NotificationEntry(pkg, user, ongoing, summary, badge)

    @Test fun countsPerAppAndProfile() {
        val counts = NotificationDots.count(
            listOf(entry("mail"), entry("mail"), entry("chat"), entry("mail", user = 10)),
        )
        assertEquals(mapOf("mail#0" to 2, "chat#0" to 1, "mail#10" to 1), counts)
    }

    @Test fun ignoresOngoingSummariesAndBadgeOff() {
        val counts = NotificationDots.count(
            listOf(
                entry("music", ongoing = true),
                entry("chat", summary = true),
                entry("shop", badge = false),
                entry("chat"),
            ),
        )
        assertEquals(mapOf("chat#0" to 1), counts)
    }
}
