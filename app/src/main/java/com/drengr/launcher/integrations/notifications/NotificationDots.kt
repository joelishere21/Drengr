package com.drengr.launcher.integrations.notifications

/** One active notification, reduced to what decides whether it earns its app a dot. */
data class NotificationEntry(
    val packageName: String,
    val userSerial: Long,
    val ongoing: Boolean,
    val groupSummary: Boolean,
    val canShowBadge: Boolean,
)

object NotificationDots {
    /** Key matching [com.drengr.launcher.core.domain.AppInfo.notificationKey]. */
    fun key(packageName: String, userSerial: Long) = "$packageName#$userSerial"

    /**
     * Counts notifications per app. Ongoing ones (music, downloads, foreground services) and
     * group summaries don't count, nor do channels where the app or user turned badges off.
     */
    fun count(entries: List<NotificationEntry>): Map<String, Int> =
        entries.asSequence()
            .filter { !it.ongoing && !it.groupSummary && it.canShowBadge }
            .groupingBy { key(it.packageName, it.userSerial) }
            .eachCount()
}
