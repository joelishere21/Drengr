package com.drengr.launcher.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesAndTasksTest {
    @Test fun noteTitleAndPreview() {
        val note = Note("1", "\n  Groceries \nmilk\n\neggs", 0)
        assertEquals("Groceries", note.title)
        assertEquals("milk · eggs", note.preview)
    }

    @Test fun savingMovesToTopAndBlankDeletes() {
        var notes = Notes.save(emptyList(), "a", "First", 1)
        notes = Notes.save(notes, "b", "Second", 2)
        notes = Notes.save(notes, "a", "First, edited", 3)
        assertEquals(listOf("a", "b"), notes.map { it.id })
        assertEquals("First, edited", notes.first().text)
        assertEquals(listOf("b"), Notes.save(notes, "a", "   ", 4).map { it.id })
    }

    @Test fun noteSearchNeedsEveryWordAndPrefersTitles() {
        val notes = listOf(Note("1", "Trip\npack the passport", 2), Note("2", "Passport renewal\nform", 1))
        assertEquals(listOf("2", "1"), Notes.search(notes, "passport").map { it.id })
        assertEquals(listOf("1"), Notes.search(notes, "pack passport").map { it.id })
        assertTrue(Notes.search(notes, "  ").isEmpty())
    }

    private fun task(id: String, done: Boolean = false, doneAt: Long? = null) = TaskItem(id, "Task $id", done, 0, doneAt)

    @Test fun addGoesAfterOpenTasksAndCleansTitle() {
        val tasks = Tasks.add(listOf(task("a"), task("d", true, 5)), "b", "  Buy   milk ", 1)
        assertEquals(listOf("a", "b", "d"), tasks.map { it.id })
        assertEquals("Buy milk", tasks[1].title)
        assertEquals(tasks, Tasks.add(tasks, "c", "   ", 2))
    }

    @Test fun doneTasksSinkNewestFirstAndReopen() {
        var tasks = listOf(task("a"), task("b"), task("c"))
        tasks = Tasks.setDone(tasks, "a", true, 10)
        tasks = Tasks.setDone(tasks, "b", true, 20)
        assertEquals(listOf("c", "b", "a"), tasks.map { it.id })
        tasks = Tasks.setDone(tasks, "a", false, 30)
        assertEquals(listOf("c", "a", "b"), tasks.map { it.id })
        assertEquals(null, tasks[1].doneAt)
        assertEquals(listOf("c", "a"), Tasks.clearDone(tasks).map { it.id })
    }

    @Test fun moveStaysAmongOpenTasks() {
        val tasks = listOf(task("a"), task("b"), task("d", true, 1))
        assertEquals(listOf("b", "a", "d"), Tasks.move(tasks, "a", 1).map { it.id })
        assertEquals(listOf("b", "a", "d"), Tasks.move(tasks, "a", 5).map { it.id })
        assertEquals(listOf("a", "b", "d"), Tasks.move(tasks, "a", -1).map { it.id })
    }

    @Test fun renameAndSearch() {
        val tasks = listOf(task("a"), task("b", true, 1))
        assertEquals("Call mom", Tasks.rename(tasks, "a", " Call  mom ")[0].title)
        assertEquals(tasks, Tasks.rename(tasks, "a", " "))
        assertEquals(listOf("a"), Tasks.search(tasks, "task").map { it.id }) // done tasks aren't searched
    }
}
