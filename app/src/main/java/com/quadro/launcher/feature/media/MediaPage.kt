package com.quadro.launcher.feature.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.quadro.launcher.core.designsystem.IconBadge
import com.quadro.launcher.core.designsystem.PageHeader
import com.quadro.launcher.core.designsystem.QuadroCard
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.core.designsystem.SectionHeader
import com.quadro.launcher.core.domain.AppCategory
import com.quadro.launcher.integrations.media.AudioController
import com.quadro.launcher.integrations.media.AudioState
import com.quadro.launcher.integrations.media.OutputKind
import com.quadro.launcher.launcher.LauncherNavigator
import com.quadro.launcher.launcher.LauncherUiState
import com.quadro.launcher.launcher.components.AppItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val audio: AudioController,
) : ViewModel() {
    /** Polled while visible: volume keys and output changes have no permission-free callback. */
    val state: StateFlow<AudioState> = flow {
        while (true) {
            emit(audio.state())
            delay(1_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), audio.state())

    fun playPause() = audio.playPause()
    fun next() = audio.next()
    fun previous() = audio.previous()
    fun volume(raise: Boolean) = audio.adjustVolume(raise)
}

@Composable
fun MediaPage(
    state: LauncherUiState,
    navigator: LauncherNavigator,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val audio by viewModel.state.collectAsStateWithLifecycle()
    val colors = QuadroTheme.colors
    val mediaApps = state.apps.filter { it.category == AppCategory.Media }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QuadroTheme.spacing.pageHorizontal),
    ) {
        PageHeader("Media", subtitle = if (audio.isMusicActive) "Playing" else "Idle", modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))

        QuadroCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(72.dp)
                        .aspectRatio(1f)
                        .clip(QuadroTheme.shapes.medium)
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MusicNote, null, tint = colors.accent, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Now playing", style = QuadroTheme.typography.title)
                    Text(
                        if (audio.isMusicActive) "Audio is playing" else "Nothing playing",
                        style = QuadroTheme.typography.body,
                        color = colors.textSecondary,
                    )
                    Text(
                        "Track details & artwork arrive with media-session integration.",
                        style = QuadroTheme.typography.caption,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton(Icons.Outlined.SkipPrevious, "Previous", 52.dp, viewModel::previous)
                RoundButton(
                    if (audio.isMusicActive) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    "Play or pause",
                    68.dp,
                    viewModel::playPause,
                    primary = true,
                )
                RoundButton(Icons.Outlined.SkipNext, "Next", 52.dp, viewModel::next)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.AutoMirrored.Outlined.VolumeDown, "Volume down", 40.dp, { viewModel.volume(false) })
                LinearProgressIndicator(
                    progress = { audio.volumeFraction },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    color = colors.accent,
                    trackColor = colors.divider,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                RoundButton(Icons.AutoMirrored.Outlined.VolumeUp, "Volume up", 40.dp, { viewModel.volume(true) })
            }
        }

        Spacer(Modifier.height(12.dp))
        QuadroCard(Modifier.fillMaxWidth(), onClick = { navigator.runActionById("volume") }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(audio.outputKind.icon())
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Audio output", style = QuadroTheme.typography.label, color = colors.textSecondary)
                    Text(audio.outputLabel, style = QuadroTheme.typography.title)
                }
            }
        }

        if (mediaApps.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Media apps")
            Spacer(Modifier.height(4.dp))
            mediaApps.take(8).chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { AppItem(it, Modifier.weight(1f)) }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val colors = QuadroTheme.colors
    Box(
        Modifier
            .size(size)
            .clip(QuadroTheme.shapes.pill)
            .background(if (primary) colors.accent else colors.surfaceElevated)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (primary) colors.onAccent else colors.textPrimary,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

private fun OutputKind.icon(): ImageVector = when (this) {
    OutputKind.Bluetooth -> Icons.Outlined.Bluetooth
    OutputKind.Wired -> Icons.Outlined.Headphones
    OutputKind.Usb -> Icons.Outlined.Usb
    OutputKind.Speaker, OutputKind.Other -> Icons.Outlined.Speaker
}
