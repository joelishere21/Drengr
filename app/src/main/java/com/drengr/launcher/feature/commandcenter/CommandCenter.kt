package com.drengr.launcher.feature.commandcenter

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.common.FuzzyMatcher
import android.widget.Toast
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StickyNote2
import com.drengr.launcher.core.domain.Note
import com.drengr.launcher.core.domain.Notes
import com.drengr.launcher.core.domain.TaskItem
import com.drengr.launcher.core.domain.Tasks
import com.drengr.launcher.feature.notes.NoteEditorController
import com.drengr.launcher.integrations.contacts.Contact
import com.drengr.launcher.integrations.contacts.ContactsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import java.util.UUID
import com.drengr.launcher.core.domain.searchKeywords
import com.drengr.launcher.core.domain.withCustomLabels
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.designsystem.Pill
import com.drengr.launcher.core.designsystem.DrengrSearchField
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.LocalLauncherLayout
import com.drengr.launcher.core.designsystem.SectionHeader
import com.drengr.launcher.core.domain.AppInfo
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.integrations.applications.AppRepository
import com.drengr.launcher.launcher.components.AppIcon
import com.drengr.launcher.plugins.ActionKind
import com.drengr.launcher.plugins.ActionRegistry
import com.drengr.launcher.plugins.LauncherAction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface CommandItem {
    val id: String

    data class App(val app: AppInfo) : CommandItem {
        override val id get() = "app:${app.key}"
    }

    data class Action(val action: LauncherAction) : CommandItem {
        override val id get() = "action:${action.id}"
    }

    data class WebSearch(val query: String) : CommandItem {
        override val id get() = "web"
    }

    data class NoteResult(val note: Note) : CommandItem {
        override val id get() = "note:${note.id}"
    }

    data class TaskResult(val task: TaskItem) : CommandItem {
        override val id get() = "task:${task.id}"
    }

    data class ContactResult(val contact: Contact) : CommandItem {
        override val id get() = "contact:${contact.id}"
    }

    /** Adds the typed text as a task. */
    data class AddTask(val title: String) : CommandItem {
        override val id get() = "add_task"
    }

    /** Starts a note with the typed text. */
    data class NewNote(val text: String) : CommandItem {
        override val id get() = "new_note"
    }
}

@Immutable
data class CommandSection(val title: String, val items: List<CommandItem>)

@HiltViewModel
class CommandCenterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    apps: AppRepository,
    private val settingsRepository: SettingsRepository,
    val registry: ActionRegistry,
    private val contacts: ContactsRepository,
    private val noteEditor: NoteEditorController,
) : ViewModel() {
    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val sections: StateFlow<List<CommandSection>> =
        combine(query, apps.apps, settingsRepository.settings) { q, appList, s -> Triple(q.trim(), appList, s) }
            .mapLatest { (q, appList, s) -> build(q, appList, s) + extraSections(q, s) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Contacts, notes and tasks matching [q], then ways to act on the typed text. */
    private suspend fun extraSections(q: String, settings: UserSettings): List<CommandSection> {
        if (q.isEmpty()) return emptyList()
        val people = if (settings.searchContacts && q.length >= 2) contacts.search(q, MAX_CONTACTS) else emptyList()
        val notesAndTasks = if (settings.searchNotesAndTasks) {
            Notes.search(settings.notes, q).take(MAX_NOTES).map { CommandItem.NoteResult(it) } +
                Tasks.search(settings.tasks, q).take(MAX_TASKS).map { CommandItem.TaskResult(it) }
        } else {
            emptyList()
        }
        return listOf(
            CommandSection("Contacts", people.map { CommandItem.ContactResult(it) }),
            CommandSection("Notes & tasks", notesAndTasks),
            // Web search first: Enter with no other match still searches the web, as before.
            CommandSection("More", listOf(CommandItem.WebSearch(q), CommandItem.AddTask(q), CommandItem.NewNote(q))),
        ).filter { it.items.isNotEmpty() }
    }

    fun addTask(title: String) = viewModelScope.launch {
        settingsRepository.editTasks { Tasks.add(it, UUID.randomUUID().toString(), title, System.currentTimeMillis()) }
    }

    /** Saves [text] as a new note and opens it, so the user can keep writing. */
    fun newNote(text: String) = viewModelScope.launch {
        val id = UUID.randomUUID().toString()
        settingsRepository.saveNote(id, text)
        noteEditor.open(id)
    }

    fun openNote(note: Note) = noteEditor.open(note.id)

    fun contactIntent(contact: Contact) = contacts.viewIntent(contact)

    private fun build(q: String, installed: List<AppInfo>, settings: UserSettings): List<CommandSection> {
        val actions = registry.available(context)
        val apps = installed.withCustomLabels(settings.appLabels)
        if (q.isEmpty()) {
            // Hidden apps are never suggested, only found by searching.
            val appsByKey = apps.filter { it.key !in settings.hiddenApps }.associateBy { it.key }
            val actionsById = actions.associateBy { it.id }
            val recents = settings.recents.mapNotNull { entry ->
                when {
                    entry.startsWith("app:") -> appsByKey[entry.removePrefix("app:")]?.let { CommandItem.App(it) }
                    entry.startsWith("action:") ->
                        actionsById[entry.removePrefix("action:")]?.let { CommandItem.Action(it) }
                    else -> null
                }
            }.take(6)
            val recentIds = recents.map { it.id }.toSet()
            val frequentApps = settings.launchCounts.entries.sortedByDescending { it.value }
                .mapNotNull { appsByKey[it.key] }
                .map { CommandItem.App(it) }
            val suggestedActions = ActionRegistry.SUGGESTED_IDS.mapNotNull { actionsById[it] }.map { CommandItem.Action(it) }
            val suggested = (frequentApps.take(3) + suggestedActions).filter { it.id !in recentIds }.take(6)
            return listOf(
                CommandSection("Recent", recents),
                CommandSection("Suggested", suggested),
            ).filter { it.items.isNotEmpty() }
        }

        val appMatches = apps.mapNotNull { app ->
            FuzzyMatcher.score(q, app.label, app.searchKeywords)?.let { score ->
                // Small boost for apps the user launches often.
                val boost = (settings.launchCounts[app.key] ?: 0).coerceAtMost(20)
                (score + boost) to CommandItem.App(app)
            }
        }
        val actionMatches = actions.mapNotNull { action ->
            FuzzyMatcher.score(q, action.label, action.keywords)?.let { it to CommandItem.Action(action) }
        }
        val ranked = (appMatches + actionMatches).sortedByDescending { it.first }.map { it.second }.take(MAX_RESULTS)
        return listOf(CommandSection("Results", ranked)).filter { it.items.isNotEmpty() }
    }

    private companion object {
        const val MAX_RESULTS = 20
        const val MAX_CONTACTS = 4
        const val MAX_NOTES = 3
        const val MAX_TASKS = 3
    }
}

/** Global overlay: search apps and actions. Does not occupy a pager page. */
@Composable
fun CommandCenterOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onRunAction: (LauncherAction) -> Unit,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val viewModel: CommandCenterViewModel = hiltViewModel()
        val context = LocalContext.current
        val query by viewModel.query.collectAsStateWithLifecycle()
        val sections by viewModel.sections.collectAsStateWithLifecycle()
        val focusRequester = remember { FocusRequester() }
        val colors = DrengrTheme.colors

        BackHandler(onBack = onDismiss)
        LaunchedEffect(Unit) {
            viewModel.query.value = ""
            delay(80) // let the overlay attach before requesting the keyboard
            runCatching { focusRequester.requestFocus() }
        }

        fun execute(item: CommandItem) {
            onDismiss()
            when (item) {
                is CommandItem.App -> onLaunchApp(item.app)
                is CommandItem.Action -> onRunAction(item.action)
                is CommandItem.WebSearch -> viewModel.registry.webSearch(context, item.query)
                is CommandItem.NoteResult -> viewModel.openNote(item.note)
                is CommandItem.TaskResult -> viewModel.registry["tasks"]?.let(onRunAction)
                is CommandItem.ContactResult -> runCatching { context.startActivity(viewModel.contactIntent(item.contact)) }
                    .onFailure { Toast.makeText(context, "Couldn't open the contact", Toast.LENGTH_SHORT).show() }
                is CommandItem.AddTask -> {
                    viewModel.addTask(item.title)
                    Toast.makeText(context, "Task added", Toast.LENGTH_SHORT).show()
                }
                is CommandItem.NewNote -> viewModel.newNote(item.text)
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val panelState = remember { MutableTransitionState(false).apply { targetState = true } }
            val layout = LocalLauncherLayout.current
            AnimatedVisibility(
                visibleState = panelState,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically { -it / 4 },
                exit = slideOutVertically { -it / 4 },
            ) {
                val shape = DrengrTheme.shapes.large
                Column(
                    Modifier
                        .systemBarsPadding()
                        .imePadding()
                        .padding(16.dp)
                        // On tablets: a centered panel a little below the top, like a desktop launcher.
                        .padding(top = if (layout.isLargeScreen) 40.dp else 0.dp)
                        .widthIn(max = layout.overlayMaxWidth)
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.surface)
                        .border(1.dp, colors.divider, shape)
                        // Swallow taps so they don't dismiss through the scrim.
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Terminal, null, tint = colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Command Center", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
                        Text("esc", style = DrengrTheme.typography.mono, color = colors.textSecondary,
                            modifier = Modifier.clickable(onClick = onDismiss))
                    }
                    Spacer(Modifier.height(12.dp))
                    DrengrSearchField(
                        value = query,
                        onValueChange = { viewModel.query.value = it },
                        placeholder = "Search apps, actions, notes…",
                        modifier = Modifier.focusRequester(focusRequester),
                        onSubmit = { sections.firstOrNull()?.items?.firstOrNull()?.let(::execute) },
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        sections.forEach { section ->
                            item(key = "header_${section.title}") {
                                SectionHeader(section.title, Modifier.padding(top = 12.dp, bottom = 4.dp))
                            }
                            items(section.items, key = { "${section.title}:${it.id}" }) { item ->
                                CommandRow(item, onClick = { execute(item) })
                            }
                        }
                        if (sections.isEmpty()) {
                            item {
                                Text(
                                    "Type to search apps and actions.",
                                    style = DrengrTheme.typography.caption,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandRow(item: CommandItem, onClick: () -> Unit) {
    val colors = DrengrTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DrengrTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (item) {
            is CommandItem.App -> AppIcon(item.app, 34.dp)
            is CommandItem.Action -> IconBadge(item.action.icon, size = 34.dp)
            is CommandItem.WebSearch -> IconBadge(Icons.Outlined.Public, size = 34.dp)
            is CommandItem.NoteResult, is CommandItem.NewNote -> IconBadge(Icons.Outlined.StickyNote2, size = 34.dp)
            is CommandItem.TaskResult -> IconBadge(Icons.Outlined.RadioButtonUnchecked, size = 34.dp)
            is CommandItem.AddTask -> IconBadge(Icons.Outlined.AddTask, size = 34.dp)
            is CommandItem.ContactResult -> IconBadge(Icons.Outlined.Person, size = 34.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val (title, subtitle) = when (item) {
                is CommandItem.App -> item.app.label to item.app.category.label
                is CommandItem.Action -> item.action.label to item.action.description
                is CommandItem.WebSearch -> "Search the web for “${item.query}”" to "Opens your browser"
                is CommandItem.NoteResult -> item.note.title to item.note.preview.ifEmpty { "Note" }
                is CommandItem.TaskResult -> item.task.title to "Open task"
                is CommandItem.ContactResult -> item.contact.name to "Contact"
                is CommandItem.AddTask -> "Add task “${item.title}”" to "Saved on Productivity → Tasks"
                is CommandItem.NewNote -> "New note “${item.text}”" to "Starts a note with this text"
            }
            Text(title, style = DrengrTheme.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = DrengrTheme.typography.caption,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        when (item) {
            is CommandItem.App -> Pill("App")
            is CommandItem.Action -> Pill(item.action.kind.label, highlighted = item.action.kind == ActionKind.Direct)
            is CommandItem.WebSearch -> Pill("Web")
            is CommandItem.NoteResult, is CommandItem.NewNote -> Pill("Note")
            is CommandItem.TaskResult, is CommandItem.AddTask -> Pill("Task")
            is CommandItem.ContactResult -> Pill("Contact")
        }
    }
}
