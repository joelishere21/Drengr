package com.quadro.launcher.feature.productivity

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
import androidx.compose.material.icons.outlined.CalendarToday
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
import com.quadro.launcher.core.datastore.SettingsRepository
import com.quadro.launcher.core.designsystem.IconBadge
import com.quadro.launcher.core.designsystem.PageHeader
import com.quadro.launcher.core.designsystem.Pill
import com.quadro.launcher.core.designsystem.QuadroCard
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.core.designsystem.SectionHeader
import com.quadro.launcher.feature.common.ActionRow
import com.quadro.launcher.launcher.LauncherNavigator
import com.quadro.launcher.plugins.ActionCategory
import com.quadro.launcher.plugins.ActionRegistry
import com.quadro.launcher.plugins.LauncherAction
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

@HiltViewModel
class ProductivityViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val registry: ActionRegistry,
) : ViewModel() {
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
    navigator: LauncherNavigator,
    viewModel: ProductivityViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val note by viewModel.note.collectAsStateWithLifecycle()
    val actions = viewModel.quickActions(context)
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE · MMM d"))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QuadroTheme.spacing.pageHorizontal),
    ) {
        PageHeader("Productivity", subtitle = today, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))

        QuadroCard(Modifier.fillMaxWidth(), onClick = { navigator.runActionById("calendar") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.CalendarToday)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Today's agenda", style = QuadroTheme.typography.title)
                    Text(
                        "Tap to open your calendar. Inline events arrive with the calendar integration.",
                        style = QuadroTheme.typography.caption,
                        color = QuadroTheme.colors.textSecondary,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        QuadroCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick note", style = QuadroTheme.typography.title, modifier = Modifier.weight(1f))
                Pill("Saved on device")
            }
            Spacer(Modifier.height(8.dp))
            BasicTextField(
                value = note,
                onValueChange = viewModel::updateNote,
                textStyle = QuadroTheme.typography.body.copy(color = QuadroTheme.colors.textPrimary),
                cursorBrush = SolidColor(QuadroTheme.colors.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                decorationBox = { inner ->
                    if (note.isEmpty()) {
                        Text(
                            "Write something down…",
                            style = QuadroTheme.typography.body,
                            color = QuadroTheme.colors.textSecondary,
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
        QuadroCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Outlined.Description)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Recent documents", style = QuadroTheme.typography.title)
                    Text(
                        "Coming later via the Storage Access Framework.",
                        style = QuadroTheme.typography.caption,
                        color = QuadroTheme.colors.textSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
