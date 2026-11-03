package com.drengr.launcher.core.domain

const val MAX_APP_LABEL_LENGTH = 40

/** Collapses whitespace (including newlines and tabs) and trims, so a name fits on one line. */
fun normalizeAppLabel(raw: String): String =
    raw.replace(Regex("\\s+"), " ").trim().take(MAX_APP_LABEL_LENGTH).trim()

/** Shows the user's names in place of the apps' own, sorted by the name shown. */
fun List<AppInfo>.withCustomLabels(labels: Map<String, String>): List<AppInfo> {
    if (labels.isEmpty()) return this
    return map { app -> labels[app.key]?.let { app.copy(label = it) } ?: app }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
}

/** Search terms besides [AppInfo.label]: a renamed app is still found by its own name. */
val AppInfo.searchKeywords: List<String>
    get() = if (isRenamed) listOf(originalLabel) else emptyList()
