package com.drengr.launcher.core.common

/**
 * Lightweight ranking for launcher search. Higher scores are better; null means no match.
 *
 * Tiers: exact > prefix > word prefix > initials ("gm" → "Google Maps") > substring > subsequence.
 */
object FuzzyMatcher {

    private val separators = charArrayOf(' ', '-', '_', '.', '&', '/')

    fun score(query: String, target: String): Int? {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return 0
        val t = target.lowercase()
        if (t.isEmpty()) return null

        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - (t.length - q.length).coerceAtMost(99)

        val words = t.split(*separators).filter { it.isNotEmpty() }
        val wordIndex = words.indexOfFirst { it.startsWith(q) }
        if (wordIndex >= 0) return 800 - (wordIndex * 10).coerceAtMost(90)

        if (q.length >= 2) {
            val initials = words.joinToString("") { it.first().toString() }
            if (initials.startsWith(q)) return 700
        }

        val index = t.indexOf(q)
        if (index >= 0) return 600 - index.coerceAtMost(99)

        // Subsequence: every query char appears in order; tighter matches score higher.
        var from = 0
        var last = -1
        var gaps = 0
        for (c in q) {
            if (c == ' ') continue
            val found = t.indexOf(c, from)
            if (found < 0) return null
            if (last >= 0) gaps += found - last - 1
            last = found
            from = found + 1
        }
        return (400 - gaps * 8).coerceAtLeast(1)
    }

    /** Best score across a primary label and secondary keywords (keywords weigh 80%). */
    fun score(query: String, label: String, keywords: List<String>): Int? {
        val primary = score(query, label)
        val secondary = keywords.mapNotNull { score(query, it) }.maxOrNull()?.let { it * 8 / 10 }
        return listOfNotNull(primary, secondary).maxOrNull()
    }
}
