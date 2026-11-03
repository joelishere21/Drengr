package com.quadro.launcher.feature.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.SignalWifiOff
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.quadro.launcher.core.designsystem.IconBadge
import com.quadro.launcher.core.designsystem.PageHeader
import com.quadro.launcher.core.designsystem.QuadroCard
import com.quadro.launcher.core.designsystem.QuadroTheme
import com.quadro.launcher.core.designsystem.SectionHeader
import com.quadro.launcher.feature.common.ActionRow
import com.quadro.launcher.integrations.battery.BatteryRepository
import com.quadro.launcher.integrations.battery.BatteryState
import com.quadro.launcher.integrations.connectivity.ConnectivityRepository
import com.quadro.launcher.integrations.connectivity.NetworkState
import com.quadro.launcher.integrations.connectivity.NetworkType
import com.quadro.launcher.integrations.media.AudioController
import com.quadro.launcher.integrations.media.AudioState
import com.quadro.launcher.integrations.system.StorageRepository
import com.quadro.launcher.integrations.system.StorageState
import com.quadro.launcher.integrations.system.formatBytes
import com.quadro.launcher.launcher.LauncherNavigator
import com.quadro.launcher.plugins.ActionCategory
import com.quadro.launcher.plugins.ActionKind
import com.quadro.launcher.plugins.ActionRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SystemViewModel @Inject constructor(
    battery: BatteryRepository,
    connectivity: ConnectivityRepository,
    private val storage: StorageRepository,
    private val audio: AudioController,
    val registry: ActionRegistry,
) : ViewModel() {
    val battery: StateFlow<BatteryState> =
        battery.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryState())
    val network: StateFlow<NetworkState> =
        connectivity.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkState())

    private val _storage = MutableStateFlow(StorageState())
    val storageState: StateFlow<StorageState> = _storage.asStateFlow()
    private val _audio = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audio.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _storage.value = storage.read() }
        _audio.value = audio.state()
    }
}

@Composable
fun SystemPage(
    navigator: LauncherNavigator,
    viewModel: SystemViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val network by viewModel.network.collectAsStateWithLifecycle()
    val storage by viewModel.storageState.collectAsStateWithLifecycle()
    val audio by viewModel.audioState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val controls = viewModel.registry.available(context).filter { it.category == ActionCategory.System }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = QuadroTheme.spacing.pageHorizontal),
    ) {
        PageHeader("System", subtitle = android.os.Build.MODEL, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusCard(
                icon = if (battery.isCharging) Icons.Outlined.BatteryChargingFull else Icons.Outlined.BatteryFull,
                title = "Battery",
                value = "${battery.level}%",
                detail = buildString {
                    append(if (battery.isCharging) "Charging" else "On battery")
                    battery.source?.let { append(" · $it") }
                    append(" · %.1f°C".format(battery.temperatureC))
                },
                progress = battery.level / 100f,
                modifier = Modifier.weight(1f),
                onClick = { navigator.runActionById("battery") },
            )
            StatusCard(
                icon = network.icon(),
                title = "Network",
                value = network.type.label,
                detail = when {
                    network.type == NetworkType.None -> "No connection"
                    !network.validated -> "No internet access"
                    network.metered -> "Metered"
                    else -> "Connected"
                },
                modifier = Modifier.weight(1f),
                onClick = { navigator.runActionById("internet") },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusCard(
                icon = Icons.Outlined.Storage,
                title = "Storage",
                value = formatBytes(storage.freeBytes),
                detail = "free of ${formatBytes(storage.totalBytes)}",
                progress = storage.usedFraction,
                modifier = Modifier.weight(1f),
                onClick = { navigator.runActionById("storage") },
            )
            StatusCard(
                icon = Icons.AutoMirrored.Outlined.VolumeUp,
                title = "Audio",
                value = "${(audio.volumeFraction * 100).toInt()}%",
                detail = "${audio.ringerMode} · ${audio.outputLabel}",
                progress = audio.volumeFraction,
                modifier = Modifier.weight(1f),
                onClick = { navigator.runActionById("volume") },
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Direct controls")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            controls.filter { it.kind == ActionKind.Direct }.forEach { action ->
                ActionRow(action, onClick = { navigator.runAction(action) })
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionHeader("Opens Android Settings")
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            controls.filter { it.kind != ActionKind.Direct }.forEach { action ->
                ActionRow(action, onClick = { navigator.runAction(action) })
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusCard(
    icon: ImageVector,
    title: String,
    value: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
) {
    QuadroCard(modifier = modifier.heightIn(min = 140.dp), onClick = onClick) {
        IconBadge(icon, size = 32.dp)
        Spacer(Modifier.height(10.dp))
        Text(title.uppercase(), style = QuadroTheme.typography.label, color = QuadroTheme.colors.textSecondary)
        Text(value, style = QuadroTheme.typography.monoLarge, maxLines = 1)
        Text(detail, style = QuadroTheme.typography.caption, color = QuadroTheme.colors.textSecondary, maxLines = 2)
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = QuadroTheme.colors.accent,
                trackColor = QuadroTheme.colors.divider,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private fun NetworkState.icon(): ImageVector = when (type) {
    NetworkType.Wifi -> Icons.Outlined.Wifi
    NetworkType.Cellular -> Icons.Outlined.SignalCellularAlt
    NetworkType.Ethernet -> Icons.Outlined.SettingsEthernet
    NetworkType.Vpn -> Icons.Outlined.VpnKey
    NetworkType.Other -> Icons.Outlined.Wifi
    NetworkType.None -> Icons.Outlined.SignalWifiOff
}
