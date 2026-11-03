package com.drengr.launcher.feature.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.LocalLauncherLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val controller: NoteEditorController,
) : ViewModel() {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var noteId: String? = null
    private var saveJob: Job? = null

    fun load(id: String) {
        if (id == noteId) return
        noteId = id
        _loaded.value = false
        viewModelScope.launch {
            _text.value = settings.current().notes.firstOrNull { it.id == id }?.text.orEmpty()
            _loaded.value = true
        }
    }

    /** Debounced so typing doesn't write to disk on every keystroke. */
    fun update(text: String) {
        val id = noteId ?: return
        if (!_loaded.value) return
        _text.value = text
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            settings.saveNote(id, text)
        }
    }

    /** Saves right away (a blank note is dropped) and closes the editor. */
    fun close() {
        val id = noteId
        val pending = saveJob?.isActive == true
        saveJob?.cancel()
        if (id != null && pending) {
            val text = _text.value
            viewModelScope.launch { withContext(NonCancellable) { settings.saveNote(id, text) } }
        }
        reset()
    }

    fun delete() {
        val id = noteId
        saveJob?.cancel()
        if (id != null) viewModelScope.launch { withContext(NonCancellable) { settings.deleteNote(id) } }
        reset()
    }

    private fun reset() {
        noteId = null
        _text.value = ""
        _loaded.value = false
        controller.close()
    }
}

/** Full-screen editor for the note [NoteEditorController] has open. */
@Composable
fun NoteEditorHost(controller: NoteEditorController) {
    val editing by controller.editing.collectAsStateWithLifecycle()
    val id = editing ?: return
    val viewModel: NoteEditorViewModel = hiltViewModel()
    LaunchedEffect(id) { viewModel.load(id) }
    val text by viewModel.text.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val colors = DrengrTheme.colors
    val focus = remember { FocusRequester() }

    Dialog(
        onDismissRequest = viewModel::close,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        BackHandler(onBack = viewModel::close)
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
                .systemBarsPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.widthIn(max = LocalLauncherLayout.current.readableMaxWidth).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::close) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Save and close")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Note", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
                    Text("Saved on device", style = DrengrTheme.typography.caption, color = colors.textSecondary)
                    IconButton(onClick = viewModel::delete, enabled = text.isNotBlank()) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete note")
                    }
                }
                if (loaded) {
                    BasicTextField(
                        value = text,
                        onValueChange = viewModel::update,
                        textStyle = DrengrTheme.typography.body.copy(color = colors.textPrimary),
                        cursorBrush = SolidColor(colors.accent),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .focusRequester(focus),
                        decorationBox = { inner ->
                            if (text.isEmpty()) {
                                Text("Write something down…", style = DrengrTheme.typography.body, color = colors.textSecondary)
                            }
                            inner()
                        },
                    )
                    LaunchedEffect(Unit) {
                        // New notes start with the keyboard up; existing ones open for reading.
                        if (text.isEmpty()) runCatching { focus.requestFocus() }
                    }
                }
            }
        }
    }
}
