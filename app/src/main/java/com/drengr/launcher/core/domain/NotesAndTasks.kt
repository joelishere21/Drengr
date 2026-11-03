package com.drengr.launcher.core.domain

import androidx.compose.runtime.Immutable

/** A note kept on the device. Listed most recently edited first. */
@Immutable
data class Note(val id: String, val text: String, val updatedAt: Long) {
    /** First non-blank line, for lists and search results. */
    val title: String get() = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()

    /** Everything after the title, flattened to one line. */
    val preview: String
        get() = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).joinToString(" · ")
}

/** A to-do item. Open tasks keep the user's order; done ones follow, most recently finished first. */
@Immutable
data class TaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val createdAt: Long,
    val doneAt: Long? = null,
)

/** Note list rules, kept free of storage and UI so they can be unit tested. */
object Notes {
    const val MAX_NOTES = 100

    /** Saves [text] as note [id] (new or existing) and moves it to the top. Blank text deletes it. */
    fun save(notes: List<Note>, id: String, text: String, now: Long): List<Note> {
        val others = notes.filter { it.id != id }
        if (text.isBlank()) return others
        return (listOf(Note(id, text, now)) + others).take(MAX_NOTES)
    }

    fun delete(notes: List<Note>, id: String): List<Note> = notes.filter { it.id != id }

    /** Notes containing every word of [query], best matches (title hits) first. */
    fun search(notes: List<Note>, query: String): List<Note> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return notes
            .filter { note -> val text = note.text.lowercase(); words.all { it in text } }
            .sortedByDescending { note -> words.count { it in note.title.lowercase() } }
    }
}

/** Task list rules. */
object Tasks {
    const val MAX_TASKS = 200
    const val MAX_TITLE_LENGTH = 200

    /** Open tasks in the user's order, then done tasks, most recently finished first. */
    fun ordered(tasks: List<TaskItem>): List<TaskItem> =
        tasks.filter { !it.done } + tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }

    fun clean(title: String): String = title.replace(Regex("\\s+"), " ").trim().take(MAX_TITLE_LENGTH)

    /** Adds an open task at the end of the open ones. Blank titles are ignored. */
    fun add(tasks: List<TaskItem>, id: String, title: String, now: Long): List<TaskItem> {
        val clean = clean(title)
        if (clean.isEmpty()) return tasks
        val sorted = ordered(tasks)
        val open = sorted.filter { !it.done }
        return (open + TaskItem(id, clean, createdAt = now) + sorted.filter { it.done }).take(MAX_TASKS)
    }

    fun setDone(tasks: List<TaskItem>, id: String, done: Boolean, now: Long): List<TaskItem> =
        ordered(tasks.map { if (it.id == id) it.copy(done = done, doneAt = if (done) now else null) else it })

    fun rename(tasks: List<TaskItem>, id: String, title: String): List<TaskItem> {
        val clean = clean(title)
        if (clean.isEmpty()) return tasks
        return tasks.map { if (it.id == id) it.copy(title = clean) else it }
    }

    fun delete(tasks: List<TaskItem>, id: String): List<TaskItem> = tasks.filter { it.id != id }

    fun clearDone(tasks: List<TaskItem>): List<TaskItem> = tasks.filter { !it.done }

    /** Moves an open task up (negative) or down (positive) among the open tasks. */
    fun move(tasks: List<TaskItem>, id: String, delta: Int): List<TaskItem> {
        val sorted = ordered(tasks)
        val open = sorted.filter { !it.done }.toMutableList()
        val from = open.indexOfFirst { it.id == id }
        if (from < 0) return sorted
        val to = (from + delta).coerceIn(0, open.lastIndex)
        open.add(to, open.removeAt(from))
        return open + sorted.filter { it.done }
    }

    /** Open tasks whose title contains every word of [query]. */
    fun search(tasks: List<TaskItem>, query: String): List<TaskItem> {
        val words = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return tasks.filter { t -> !t.done && words.all { it in t.title.lowercase() } }
    }
}
