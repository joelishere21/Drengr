package com.drengr.launcher.feature.smarthome

import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drengr.launcher.core.common.inOrder
import com.drengr.launcher.core.common.moving
import com.drengr.launcher.core.datastore.SettingsRepository
import com.drengr.launcher.integrations.govee.GoveeCommand
import com.drengr.launcher.integrations.govee.GoveeDevice
import com.drengr.launcher.integrations.govee.GoveeException
import com.drengr.launcher.integrations.govee.GoveeRepository
import com.drengr.launcher.integrations.govee.GoveeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SmartHomeUi {
    data object Loading : SmartHomeUi
    data object NeedsKey : SmartHomeUi
    data class Ready(val devices: List<DeviceUi>, val refreshing: Boolean = false) : SmartHomeUi
    data class Error(val message: String) : SmartHomeUi
}

@Immutable
data class DeviceUi(val device: GoveeDevice, val state: GoveeState)

/** Refresh device states on return to the launcher once they are this old. */
private const val STALE_MS = 60_000L

@HiltViewModel
class SmartHomeViewModel @Inject constructor(
    private val govee: GoveeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Device ids the user hid from the page. */
    val hidden: StateFlow<Set<String>> = settings.settings
        .map { it.hiddenGoveeDevices }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Device ids in the user's order. */
    val order: StateFlow<List<String>> = settings.settings
        .map { it.goveeDeviceOrder }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Moves a shown device up (negative) or down (positive) among the shown devices. */
    fun move(device: GoveeDevice, delta: Int) {
        val all = (_ui.value as? SmartHomeUi.Ready)?.devices?.map { it.device.id }?.inOrder(order.value) { it } ?: return
        val shown = all.filter { it !in hidden.value }
        val moved = shown.moving(device.id, delta)
        if (moved == shown) return
        // Hidden devices keep their place after the shown ones.
        viewModelScope.launch { settings.setGoveeDeviceOrder(moved + (all - moved.toSet())) }
    }

    fun setHidden(device: GoveeDevice, hide: Boolean) = viewModelScope.launch {
        settings.setGoveeDeviceHidden(device.id, hide)
    }

    private val _ui = MutableStateFlow<SmartHomeUi>(SmartHomeUi.Loading)
    val ui: StateFlow<SmartHomeUi> = _ui.asStateFlow()

    private val _savingKey = MutableStateFlow(false)
    val savingKey: StateFlow<Boolean> = _savingKey.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var loadedAt = 0L

    init {
        viewModelScope.launch {
            govee.hasKey.collect { has -> if (has) load(refresh = false) else _ui.value = SmartHomeUi.NeedsKey }
        }
    }

    fun onResume() {
        if (_ui.value is SmartHomeUi.Ready && SystemClock.elapsedRealtime() - loadedAt > STALE_MS) {
            viewModelScope.launch { load(refresh = false) }
        }
    }

    fun refresh() = viewModelScope.launch { load(refresh = true) }

    fun saveKey(key: String) = viewModelScope.launch {
        _savingKey.value = true
        try {
            govee.saveKey(key) // hasKey then triggers load()
        } catch (e: GoveeException) {
            _messages.tryEmit(e.message ?: "That key didn't work")
        } finally {
            _savingKey.value = false
        }
    }

    fun allOff() {
        val ready = _ui.value as? SmartHomeUi.Ready ?: return
        val targets = ready.devices.filter { it.device.canPower && it.device.id !in hidden.value }
        updateStates(targets.map { it.device.id }) { it.copy(on = false) }
        govee.allOff { message ->
            _messages.tryEmit(message)
            viewModelScope.launch { load(refresh = false) }
        }
    }

    fun setPower(device: GoveeDevice, on: Boolean) =
        send(device, GoveeCommand.Power(on)) { it.copy(on = on) }

    fun setBrightness(device: GoveeDevice, percent: Int) =
        send(device, GoveeCommand.Brightness(percent)) { it.copy(brightness = percent, on = true) }

    fun setColor(device: GoveeDevice, rgb: Int) =
        send(device, GoveeCommand.Color(rgb)) { it.copy(color = rgb, colorTemperatureK = null, on = true) }

    fun setColorTemperature(device: GoveeDevice, kelvin: Int) =
        send(device, GoveeCommand.ColorTemperature(kelvin)) { it.copy(colorTemperatureK = kelvin, on = true) }

    /** Shows the change at once, then reverts it if Govee rejects the command. */
    private fun send(device: GoveeDevice, command: GoveeCommand, optimistic: (GoveeState) -> GoveeState) {
        val before = (_ui.value as? SmartHomeUi.Ready)?.devices?.firstOrNull { it.device.id == device.id }?.state
        updateStates(listOf(device.id), optimistic)
        viewModelScope.launch {
            try {
                govee.control(device, command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoveeException) {
                if (before != null) updateStates(listOf(device.id)) { before }
                _messages.tryEmit("${device.name}: ${e.message}")
            }
        }
    }

    private fun updateStates(ids: List<String>, change: (GoveeState) -> GoveeState) {
        _ui.update { ui ->
            if (ui !is SmartHomeUi.Ready) return@update ui
            ui.copy(devices = ui.devices.map { if (it.device.id in ids) it.copy(state = change(it.state)) else it })
        }
    }

    private suspend fun load(refresh: Boolean) {
        val current = _ui.value
        _ui.value = if (current is SmartHomeUi.Ready) current.copy(refreshing = true) else SmartHomeUi.Loading
        try {
            val devices = govee.devices(refresh)
            // One state request per device, in parallel. A device that fails just shows as unknown.
            val states = coroutineScope {
                devices.map { d -> async { runCatching { govee.state(d) }.getOrDefault(GoveeState()) } }.awaitAll()
            }
            _ui.value = SmartHomeUi.Ready(devices.zip(states) { d, s -> DeviceUi(d, s) })
            loadedAt = SystemClock.elapsedRealtime()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoveeException) {
            if (current is SmartHomeUi.Ready) {
                _ui.value = current.copy(refreshing = false)
                _messages.tryEmit(e.message ?: "Govee is unavailable")
            } else {
                _ui.value = SmartHomeUi.Error(e.message ?: "Govee is unavailable")
            }
        }
    }
}
