package com.drengr.launcher.feature.productivity

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import com.drengr.launcher.integrations.documents.DocumentsRepository
import com.drengr.launcher.integrations.documents.RecentDocument
import kotlinx.coroutines.flow.receiveAsFlow
import android.text.format.DateFormat
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.domain.UserSettings
import com.drengr.launcher.feature.common.TwoColumns
import com.drengr.launcher.feature.common.rememberPermissionRequest
import com.drengr.launcher.feature.home.Agenda
import com.drengr.launcher.feature.home.AgendaUi
import com.drengr.launcher.feature.home.rememberCurrentTime
import com.drengr.launcher.integrations.calendar.CalendarEvent
import com.drengr.launcher.integrations.calendar.CalendarRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.drengr.launcher.core.designsystem.PageHeader
import com.drengr.launcher.core.designsystem.Pill
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.launcher.LauncherNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProductivityViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val calendar: CalendarRepository,
    private val documentsRepository: DocumentsRepository,
) : ViewModel() {
    private val calendarGranted = MutableStateFlow(calendar.hasPermission())

    val agenda: StateFlow<AgendaUi> = calendarGranted
        .flatMapLatest { granted ->
            if (granted) calendar.upcoming().map { AgendaUi.Ready(it) } else flowOf(AgendaUi.NeedsPermission)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgendaUi.Loading)

    /** Calendar access can change in system settings; check again whenever the launcher returns. */
    fun refreshCalendarAccess() {
        calendarGranted.value = calendar.hasPermission()
    }

    fun calendarIntent(event: CalendarEvent) = calendar.viewIntent(event)

    val documents: StateFlow<List<RecentDocument>> = documentsRepository.documents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = kotlinx.coroutines.channels.Channel<String>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun addDocuments(uris: List<Uri>) = viewModelScope.launch {
        val added = documentsRepository.add(uris)
        if (added < uris.size) _messages.send("Android didn't let Drengr keep access to some of those files.")
    }

    fun removeDocument(document: RecentDocument) = viewModelScope.launch { documentsRepository.remove(document) }

    /** Opens [document] in its app, or explains (and drops it) if it was moved or deleted. */
    fun openDocument(context: Context, document: RecentDocument) = viewModelScope.launch {
        val ready = documentsRepository.prepareOpen(document)
        if (ready == null) {
            documentsRepository.remove(document)
            _messages.send("“${document.name}” was moved or deleted, so it's been removed from the list.")
            return@launch
        }
        try {
            context.startActivity(documentsRepository.viewIntent(ready))
        } catch (e: ActivityNotFoundException) {
            _messages.send("No app on this phone can open “${ready.name}”.")
        } catch (e: SecurityException) {
            _messages.send("Drengr can't share “${ready.name}” with another app.")
        }
    }
}

@Composable
fun ProductivityPage(
    settings: UserSettings,
    navigator: LauncherNavigator,
    viewModel: ProductivityViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notesAndTasks: NotesAndTasksViewModel = hiltViewModel()
    val notes by notesAndTasks.notes.collectAsStateWithLifecycle()
    val tasks by notesAndTasks.tasks.collectAsStateWithLifecycle()
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMM d"))
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.messages.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    val now by rememberCurrentTime()
    val use24h = settings.use24Hour ?: DateFormat.is24HourFormat(context)
    val agendaItems = remember(agenda, now, use24h) {
        (agenda as? AgendaUi.Ready)?.let { Agenda.build(it.events, now, use24h, limit = AGENDA_LIMIT) }.orEmpty()
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshCalendarAccess()
        onPauseOrDispose { }
    }
    val requestCalendar = rememberPermissionRequest(
        permission = Manifest.permission.READ_CALENDAR,
        blockedMessage = "Calendar access is off. Tap Agenda again to open App info → Permissions.",
        onResult = { viewModel.refreshCalendarAccess() },
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DrengrTheme.spacing.pageHorizontal),
    ) {
        PageHeader("Productivity", subtitle = today, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))

        // Wide windows: the week and tasks on the left, notes and documents beside it.
        TwoColumns(
            stackedGap = 12.dp,
            first = {
                WeekAgenda(
                    state = agenda,
                    items = agendaItems,
                    onRequestAccess = requestCalendar,
                    onOpenCalendar = { navigator.runActionById("calendar") },
                    onOpenEvent = { event ->
                        runCatching { context.startActivity(viewModel.calendarIntent(event)) }
                            .onFailure { navigator.runActionById("calendar") }
                    },
                )
                Spacer(Modifier.height(12.dp))
                TasksCard(tasks, notesAndTasks)
            },
            second = {
                NotesCard(notes, onOpen = notesAndTasks::openNote, onNew = notesAndTasks::newNote)
                Spacer(Modifier.height(12.dp))
                RecentDocumentsCard(
                    documents = documents,
                    onAdd = viewModel::addDocuments,
                    onOpen = { viewModel.openDocument(context, it) },
                    onRemove = viewModel::removeDocument,
                )
            },
        )
        Spacer(Modifier.height(16.dp))
    }
}
