package com.drengr.launcher.core.datastore

import com.drengr.launcher.core.domain.Note
import com.drengr.launcher.core.domain.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesStorageTest {
    @Test fun notesRoundTripNewestFirst() {
        val notes = listOf(Note("a", "Old", 1), Note("b", "New\nline two", 9))
        assertEquals(notes.reversed(), decodeNotes(encodeNotes(notes)))
    }

    @Test fun tasksRoundTripInOrder() {
        val tasks = listOf(TaskItem("a", "One", createdAt = 1), TaskItem("b", "Two", true, 2, 3))
        assertEquals(tasks, decodeTasks(encodeTasks(tasks)))
    }

    @Test fun badEntriesAreSkipped() {
        assertTrue(decodeNotes("nope").isEmpty())
        assertEquals(1, decodeNotes("""[{"id":"a","text":"hi"},{"id":"b","text":"  "},{"text":"no id"}]""").size)
        assertEquals(1, decodeTasks("""[{"id":"a","title":"x"},{"id":"a","title":"dup"},{"id":"c"}]""").size)
    }
}
