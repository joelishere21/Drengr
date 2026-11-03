package com.drengr.launcher.feature.media

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.drengr.launcher.feature.common.TwoColumns
import com.drengr.launcher.integrations.media.MediaSessionRepository
import com.drengr.launcher.integrations.media.NowPlaying
import com.drengr.launcher.integrations.notifications.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
import com.drengr.launcher.core.designsystem.IconBadge
import com.drengr.launcher.core.designsystem.PageHeader
import com.drengr.launcher.core.designsystem.DrengrCard
import com.drengr.launcher.core.designsystem.DrengrTheme
import com.drengr.launcher.core.designsystem.SectionHeader
import com.drengr.launcher.core.domain.AppCategory
import com.drengr.launcher.integrations.media.AudioController
import com.drengr.launcher.integrations.media.PlaybackHistoryRepository
import com.drengr.launcher.integrations.media.PlayedItem
import com.drengr.launcher.integrations.media.PodcastApps
import kotlinx.coroutines.launch
import com.drengr.launcher.integrations.media.AudioState
import com.drengr.launcher.integrations.media.OutputKind
import com.drengr.launcher.launcher.LauncherNavigator
import com.drengr.launcher.launcher.LauncherUiState
import com.drengr.launcher.launcher.components.AppItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaViewModel @Inject constructor(
    private val audio: AudioController,
    private val media: MediaSessionRepository,
    private val notifications: NotificationRepository,
    private val history: PlaybackHistoryRepository,
) : ViewModel() {
    val recentlyPlayed: StateFlow<List<PlayedItem>> = history.items
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearHistory() = viewModelScope.launch { history.clear() }

    fun openApp(item: PlayedItem) = media.openPlayer(item.packageName)

    /** Polled while visible: volume keys and output changes have no permission-free callback. */
    val state: StateFlow<AudioState> = flow {
        while (true) {
            emit(audio.state())
            delay(1_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(1_000), audio.state())

    private val access = MutableStateFlow(notifications.hasAccess())
    val hasAccess: StateFlow<Boolean> = access.asStateFlow()

    val nowPlaying: StateFlow<NowPlaying?> = access
        .flatMapLatest { granted -> if (granted) media.nowPlaying() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Access is granted in system settings, so check again whenever the launcher returns. */
    fun onResume() {
        access.value = notifications.hasAccess()
    }

    fun requestAccess() = notifications.openAccessSettings()
    fun playPause() = media.playPause()
    fun next() = media.next()
    fun previous() = media.previous()
    fun seekTo(positionMs: Long) = media.seekTo(positionMs)
    fun openPlayer(playing: NowPlaying) = media.openPlayer(playing.packageName)
    fun volume(raise: Boolean) = audio.adjustVolume(raise)
}

@Composable
fun MediaPage(
    state: LauncherUiState,
    navigator: LauncherNavigator,
    viewModel: MediaViewModel = hiltViewModel(),
) {
    val audio by viewModel.state.collectAsStateWithLifecycle()
    val hasAccess by viewModel.hasAccess.collectAsStateWithLifecycle()
    val playing by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val colors = DrengrTheme.colors
    val podcastApps = state.apps.filter { PodcastApps.isPodcastApp(it.packageName, it.label) }
    val mediaApps = state.apps.filter { it.category == AppCategory.Media && it !in podcastApps }
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    val current = playing
    val subtitle = when {
        current != null -> (if (current.isPlaying) "Playing" else "Paused") + " · " + current.appLabel
        audio.isMusicActive -> "Playing"
        else -> "Idle"
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = DrengrTheme.spacing.pageHorizontal),
    ) {
        PageHeader("Media", subtitle = subtitle, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))

        // Wide windows: player on the left, output and media apps beside it.
        TwoColumns(
            stackedGap = 12.dp,
            first = {
                DrengrCard(Modifier.fillMaxWidth()) {
                    if (current != null) {
                        TrackInfo(current, onOpen = { viewModel.openPlayer(current) })
                        Spacer(Modifier.height(14.dp))
                        PlaybackProgress(current, onSeek = viewModel::seekTo)
                    } else {
                        IdleInfo(
                            audioActive = audio.isMusicActive,
                            hasAccess = hasAccess,
                            onRequestAccess = viewModel::requestAccess,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(
                            Icons.Outlined.SkipPrevious, "Previous", 52.dp, viewModel::previous,
                            enabled = current?.canSkipPrevious ?: true,
                        )
                        val isPlaying = current?.isPlaying ?: audio.isMusicActive
                        RoundButton(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            68.dp,
                            viewModel::playPause,
                            primary = true,
                        )
                        RoundButton(
                            Icons.Outlined.SkipNext, "Next", 52.dp, viewModel::next,
                            enabled = current?.canSkipNext ?: true,
                        )
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
            },
            second = {
                DrengrCard(Modifier.fillMaxWidth(), onClick = { navigator.runActionById("volume") }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(audio.outputKind.icon())
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Audio output", style = DrengrTheme.typography.label, color = colors.textSecondary)
                            Text(audio.outputLabel, style = DrengrTheme.typography.title)
                        }
                    }
                }

                // The track playing now is already in the player card.
                val history = recentlyPlayed.filterNot { current != null && it.packageName == current.packageName && it.title == current.title }
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    RecentlyPlayedSection(history, onOpen = viewModel::openApp, onClear = viewModel::clearHistory)
                }
                if (podcastApps.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    PodcastsSection(podcastApps)
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
            },
        )
        Spacer(Modifier.height(16.dp))
    }
}

/** Artwork, title and artist. Tapping opens the app that is playing. */
@Composable
private fun TrackInfo(playing: NowPlaying, onOpen: () -> Unit) {
    val colors = DrengrTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(DrengrTheme.shapes.medium)
            .clickable(onClickLabel = "Open ${playing.appLabel}", onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(playing.art, 88.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playing.title ?: "Playing in ${playing.appLabel}",
                style = DrengrTheme.typography.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val byline = listOfNotNull(playing.artist, playing.album).joinToString(" · ")
            if (byline.isNotEmpty()) {
                Text(
                    byline,
                    style = DrengrTheme.typography.body,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(playing.appLabel, style = DrengrTheme.typography.caption, color = colors.accent, maxLines = 1)
        }
    }
}

@Composable
private fun Artwork(art: ImageBitmap?, size: Dp) {
    val colors = DrengrTheme.colors
    Box(
        Modifier
            .size(size)
            .clip(DrengrTheme.shapes.medium)
            .background(colors.accentSoft),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(art, contentDescription = "Artwork", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.MusicNote, null, tint = colors.accent, modifier = Modifier.size(size * 0.45f))
        }
    }
}

/** Elapsed / total time, with a seek bar when the app allows seeking. */
@Composable
private fun PlaybackProgress(playing: NowPlaying, onSeek: (Long) -> Unit) {
    if (playing.durationMs <= 0) return
    val colors = DrengrTheme.colors
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(playing.isPlaying, playing.positionUpdatedAt) {
        now = SystemClock.elapsedRealtime()
        while (playing.isPlaying) {
            delay(500)
            now = SystemClock.elapsedRealtime()
        }
    }
    // While dragging, show the drag position instead of the live one.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: (playing.positionAt(now).toFloat() / playing.durationMs)
    val position = (fraction * playing.durationMs).toLong()

    if (playing.canSeek) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * playing.durationMs).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = colors.accent,
                activeTrackColor = colors.accent,
                inactiveTrackColor = colors.divider,
            ),
            modifier = Modifier.height(28.dp),
        )
    } else {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            color = colors.accent,
            trackColor = colors.divider,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
    Row(Modifier.fillMaxWidth()) {
        Text(NowPlaying.formatTime(position), style = DrengrTheme.typography.mono, color = colors.textSecondary)
        Spacer(Modifier.weight(1f))
        Text(NowPlaying.formatTime(playing.durationMs), style = DrengrTheme.typography.mono, color = colors.textSecondary)
    }
}

@Composable
private fun IdleInfo(audioActive: Boolean, hasAccess: Boolean, onRequestAccess: () -> Unit) {
    val colors = DrengrTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Artwork(null, 72.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (audioActive) "Audio is playing" else "Nothing playing", style = DrengrTheme.typography.title)
            Text(
                if (hasAccess) {
                    "Start music or a podcast in any app to control it here."
                } else {
                    "Allow notification access to see the track, artwork and progress."
                },
                style = DrengrTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
    if (!hasAccess) {
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRequestAccess) {
            Text("Allow notification access", color = colors.accent)
        }
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = DrengrTheme.colors
    Box(
        Modifier
            .size(size)
            .clip(DrengrTheme.shapes.pill)
            .background(if (primary) colors.accent else colors.surfaceElevated)
            .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = when {
                primary -> colors.onAccent
                enabled -> colors.textPrimary
                else -> colors.textSecondary.copy(alpha = 0.4f)
            },
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
