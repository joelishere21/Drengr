package com.drengr.launcher.core.common

/** Moves [item] by [delta] positions, clamped to the list. Unknown items leave the list unchanged. */
fun <T> List<T>.moving(item: T, delta: Int): List<T> {
    val from = indexOf(item)
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
