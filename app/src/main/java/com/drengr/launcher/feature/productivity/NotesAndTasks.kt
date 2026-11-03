package com.drengr.launcher.feature.productivity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.domain.Note
import com.drengr.launcher.core.domain.TaskItem
import com.drengr.launcher.core.domain.Tasks
import com.drengr.launcher.feature.notes.NoteEditorController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** Notes and tasks, shared by the Home and Productivity pages. Everything stays on the device. */
@HiltViewModel
class NotesAndTasksViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val editor: NoteEditorController,
) : ViewModel() {
    val notes: StateFlow<List<Note>> = settings.settings.map { it.notes }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Display order: open tasks, then done. */
    val tasks: StateFlow<List<TaskItem>> = settings.settings.map { Tasks.ordered(it.tasks) }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun openNote(note: Note) = editor.open(note.id)
    fun newNote() = editor.openNew()

    private fun now() = System.currentTimeMillis()
    private fun edit(change: (List<TaskItem>) -> List<TaskItem>) = viewModelScope.launch { settings.editTasks(change) }

    fun addTask(title: String) = edit { Tasks.add(it, UUID.randomUUID().toString(), title, now()) }
    fun setDone(task: TaskItem, done: Boolean) = edit { Tasks.setDone(it, task.id, done, now()) }
    fun rename(task: TaskItem, title: String) = edit { Tasks.rename(it, task.id, title) }
    fun delete(task: TaskItem) = edit { Tasks.delete(it, task.id) }
    fun move(task: TaskItem, delta: Int) = edit { Tasks.move(it, task.id, delta) }
    fun clearDone() = edit(Tasks::clearDone)
}

// ---- Notes ------------------------------------------------------------------------------------

/** The newest notes; tap one to edit it, or start a new one. */
@Composable
fun NotesCard(notes: List<Note>, onOpen: (Note) -> Unit, onNew: () -> Unit, modifier: Modifier = Modifier) {
    val colors = DrengrTheme.colors
    var showAll by rememberSaveable { mutableStateOf(false) }
    val now = remember(notes) { System.currentTimeMillis() }
    DrengrCard(modifier.fillMaxWidth()) {
        CardHeader(Icons.Outlined.StickyNote2, "Notes", actionLabel = "New", actionDescription = "New note", onAction = onNew)
        if (notes.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Jot things down. Notes stay on this phone and turn up in Command Center search.",
                style = DrengrTheme.typography.caption,
                color = colors.textSecondary,
            )
        } else {
            Spacer(Modifier.height(6.dp))
            val shown = if (showAll) notes else notes.take(COLLAPSED_NOTES)
            shown.forEach { note ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(DrengrTheme.shapes.small)
                        .clickable(onClickLabel = "Edit note") { onOpen(note) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Text(note.title, style = DrengrTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val time = if (note.updatedAt > 0) DocumentTimes.label(note.updatedAt, now, ZoneId.systemDefault()) else null
                    Text(
                        listOfNotNull(time, note.preview.ifEmpty { null }).joinToString(" · "),
                        style = DrengrTheme.typography.caption,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ShowMore(notes.size, COLLAPSED_NOTES, showAll) { showAll = it }
        }
    }
}

// ---- Tasks ------------------------------------------------------------------------------------

/** The full task list: add, check off, edit, reorder and clear finished tasks. */
@Composable
fun TasksCard(
    tasks: List<TaskItem>,
    viewModel: NotesAndTasksViewModel,
    modifier: Modifier = Modifier,
) {
    val colors = DrengrTheme.colors
    var draft by rememberSaveable { mutableStateOf("") }
    var showDone by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskItem?>(null) }
    val open = tasks.filter { !it.done }
    val done = tasks.filter { it.done }

    fun submit() {
        if (Tasks.clean(draft).isNotEmpty()) viewModel.addTask(draft)
        draft = ""
    }

    DrengrCard(modifier.fillMaxWidth()) {
        CardHeader(
            Icons.Outlined.Checklist,
            "Tasks",
            subtitle = if (open.isEmpty()) null else "${open.size} open",
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Add, null, tint = colors.accent, modifier = Modifier.padding(horizontal = 6.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= Tasks.MAX_TITLE_LENGTH) draft = it },
                singleLine = true,
                textStyle = DrengrTheme.typography.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp)
                    .semantics { contentDescription = "New task" },
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text("Add a task", style = DrengrTheme.typography.body, color = colors.textSecondary)
                    inner()
                },
            )
            if (draft.isNotBlank()) {
                TextButton(onClick = ::submit) { Text("Add", color = colors.accent) }
            }
        }
        open.forEachIndexed { index, task ->
            TaskRow(
                task = task,
                onToggle = { viewModel.setDone(task, !task.done) },
                menu = TaskMenu(
                    canMoveUp = index > 0,
                    canMoveDown = index < open.lastIndex,
                    onMove = { viewModel.move(task, it) },
                    onEdit = { editing = task },
                    onDelete = { viewModel.delete(task) },
                ),
            )
        }
        if (done.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (showDone) "Hide finished" else "Finished (${done.size})",
                    color = colors.accent,
                    style = DrengrTheme.typography.caption,
                    modifier = Modifier
                        .clip(DrengrTheme.shapes.small)
                        .clickable(role = Role.Button) { showDone = !showDone }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                if (showDone) {
                    Text(
                        "Clear finished",
                        color = colors.textSecondary,
                        style = DrengrTheme.typography.caption,
                        modifier = Modifier
                            .clip(DrengrTheme.shapes.small)
                            .clickable(role = Role.Button) { viewModel.clearDone() }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                    )
                }
            }
            if (showDone) {
                done.forEach { task ->
                    TaskRow(
                        task = task,
                        onToggle = { viewModel.setDone(task, false) },
                        menu = TaskMenu(onEdit = { editing = task }, onDelete = { viewModel.delete(task) }),
                    )
                }
            }
        }
    }

    editing?.let { task ->
        EditTaskDialog(task, onSave = { viewModel.rename(task, it) }, onDismiss = { editing = null })
    }
}

/** Home: the first few open tasks, checkable in place. Hidden when nothing is open. */
@Composable
fun HomeTasksCard(tasks: List<TaskItem>, limit: Int, onToggle: (TaskItem) -> Unit, onOpenAll: () -> Unit, modifier: Modifier = Modifier) {
    val open = tasks.filter { !it.done }
    if (open.isEmpty()) return
    DrengrCard(modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(DrengrTheme.shapes.small)
                .clickable(onClickLabel = "Open tasks", onClick = onOpenAll)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(Icons.Outlined.Checklist, size = 32.dp)
            Spacer(Modifier.width(12.dp))
            Text("Tasks", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
            Text("${open.size} open", style = DrengrTheme.typography.caption, color = DrengrTheme.colors.textSecondary)
        }
        open.take(limit).forEach { task -> TaskRow(task, onToggle = { onToggle(task) }, menu = null) }
    }
}

private class TaskMenu(
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val onMove: (Int) -> Unit = {},
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, menu: TaskMenu?) {
    val colors = DrengrTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(DrengrTheme.shapes.small)
                .combinedClickable(
                    onClickLabel = if (task.done) "Mark as not done" else "Mark as done",
                    onLongClickLabel = if (menu != null) "More options" else null,
                    onLongClick = if (menu != null) ({ menuOpen = true }) else null,
                    onClick = onToggle,
                )
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (task.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (task.done) "Done" else "Not done",
                tint = if (task.done) colors.accent else colors.textSecondary,
                modifier = Modifier.padding(horizontal = 6.dp).size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                task.title,
                style = DrengrTheme.typography.body,
                color = if (task.done) colors.textSecondary else colors.textPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (menu != null) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val close = { menuOpen = false }
                MenuItem("Edit", Icons.Outlined.Edit, close, menu.onEdit)
                if (menu.canMoveUp) MenuItem("Move up", Icons.Outlined.KeyboardArrowUp, close) { menu.onMove(-1) }
                if (menu.canMoveDown) MenuItem("Move down", Icons.Outlined.KeyboardArrowDown, close) { menu.onMove(1) }
                MenuItem("Delete", Icons.Outlined.Delete, close, menu.onDelete)
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, close: () -> Unit, action: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        onClick = {
            close()
            action()
        },
    )
}

@Composable
private fun EditTaskDialog(task: TaskItem, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by rememberSaveable(task.id) { mutableStateOf(task.title) }
    val save = {
        if (Tasks.clean(value).isNotEmpty()) onSave(value)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit task") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.length <= Tasks.MAX_TITLE_LENGTH) value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        },
        confirmButton = { TextButton(onClick = save, enabled = Tasks.clean(value).isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- Shared bits ------------------------------------------------------------------------------

@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = DrengrTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = DrengrTheme.typography.title)
            if (subtitle != null) Text(subtitle, style = DrengrTheme.typography.caption, color = colors.textSecondary)
        }
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier
                    .clip(DrengrTheme.shapes.small)
                    .clickable(role = Role.Button, onClickLabel = actionDescription, onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.accent, modifier = Modifier.padding(end = 4.dp))
                Text(actionLabel, color = colors.accent, style = DrengrTheme.typography.body)
            }
        }
    }
}

@Composable
private fun ShowMore(total: Int, collapsed: Int, expanded: Boolean, onChange: (Boolean) -> Unit) {
    if (total <= collapsed) return
    Text(
        if (expanded) "Show fewer" else "Show all $total",
        color = DrengrTheme.colors.accent,
        style = DrengrTheme.typography.caption,
        modifier = Modifier
            .clip(DrengrTheme.shapes.small)
            .clickable(role = Role.Button) { onChange(!expanded) }
            .padding(vertical = 8.dp, horizontal = 4.dp),
    )
}

private const val COLLAPSED_NOTES = 4
