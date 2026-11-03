package com.drengr.launcher.core.common

/** Moves [item] by [delta] positions, clamped to the list. Unknown items leave the list unchanged. */
fun <T> List<T>.moving(item: T, delta: Int): List<T> {
    val from = indexOf(item)
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Sorts by position in [order] (matched by [id]); items missing from [order] keep their
 * relative order after the listed ones.
 */
fun <T> List<T>.inOrder(order: List<String>, id: (T) -> String): List<T> {
    if (order.isEmpty()) return this
    val rank = order.withIndex().associate { (i, key) -> key to i }
    return withIndex()
        .sortedWith(compareBy({ rank[id(it.value)] ?: Int.MAX_VALUE }, { it.index }))
        .map { it.value }
}
