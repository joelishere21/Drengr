package com.drengr.launcher.feature.notes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which note is open in the editor, if any. The editor sits above every page (like Command
 * Center), so actions, search results and cards anywhere can open a note.
 */
@Singleton
class NoteEditorController @Inject constructor() {
    private val _editing = MutableStateFlow<String?>(null)

    /** Id of the note being edited (it may not exist yet, for a new note), or null. */
    val editing: StateFlow<String?> = _editing.asStateFlow()

    fun open(noteId: String) {
        _editing.value = noteId
    }

    /** Opens the editor on a new, empty note. It's saved once something is typed. */
    fun openNew() = open(UUID.randomUUID().toString())

    fun close() {
        _editing.value = null
    }
}
