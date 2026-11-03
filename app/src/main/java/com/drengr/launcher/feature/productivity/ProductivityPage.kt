package com.drengr.launcher.feature.productivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
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
import android.text.format.DateFormat
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.core.domain.UserSettings
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
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.designsystem.PageHeader
import com.drengr.launcher.core.designsystem.Pill
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.SectionHeader
import com.drengr.launcher.feature.common.ActionRow
import com.drengr.launcher.launcher.LauncherNavigator
import com.drengr.launcher.plugins.ActionCategory
import com.drengr.launcher.plugins.ActionRegistry
import com.drengr.launcher.plugins.LauncherAction
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
    private val registry: ActionRegistry,
    private val calendar: CalendarRepository,
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

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note.asStateFlow()
    private var saveJob: Job? = null
    private var loaded = false

    fun quickActions(context: android.content.Context): List<LauncherAction> =
        registry.available(context).filter { it.category == ActionCategory.Productivity }

    init {
        viewModelScope.launch {
            _note.value = settings.current().quickNote
            loaded = true
        }
    }

    /** Debounced so typing doesn't write to disk on every keystroke. */
    fun updateNote(text: String) {
        if (!loaded) return
        _note.value = text
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            settings.setQuickNote(text)
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
    val note by viewModel.note.collectAsStateWithLifecycle()
    val actions = viewModel.quickActions(context)
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMM d"))
    val agenda by viewModel.agenda.collectAsStateWithLifecycle()
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
        DrengrCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick note", style = DrengrTheme.typography.title, modifier = Modifier.weight(1f))
                Pill("Saved on device")
            }
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = note,
                onValueChange = viewModel::updateNote,
                textStyle = DrengrTheme.typography.body.copy(color = DrengrTheme.colors.textPrimary),
                cursorBrush = SolidColor(DrengrTheme.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                decorationBox = { inner ->
                    if (note.isEmpty()) {
                        Text(
                            "Write something down…",
                            style = DrengrTheme.typography.body,
                            color = DrengrTheme.colors.textSecondary,
                        )
                    }
                    inner()
                },
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Quick actions")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.filter { it.id != "quick_note" }.forEach { action ->
                ActionRow(action, onClick = { navigator.runAction(action) })
            }
        }

        Spacer(Modifier.height(20.dp))
        DrengrCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Description)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Recent documents", style = DrengrTheme.typography.title)
                    Text(
                        "Coming later via the Storage Access Framework.",
                        style = DrengrTheme.typography.caption,
                        color = DrengrTheme.colors.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
