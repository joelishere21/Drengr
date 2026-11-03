package com.quadro.launcher.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quadro.launcher.integrations.battery.BatteryRepository
import com.quadro.launcher.integrations.battery.BatteryState
import com.quadro.launcher.integrations.connectivity.ConnectivityRepository
import com.quadro.launcher.integrations.connectivity.NetworkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    battery: BatteryRepository,
    connectivity: ConnectivityRepository,
) : ViewModel() {
    val battery: StateFlow<BatteryState> =
        battery.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryState())
    val network: StateFlow<NetworkState> =
        connectivity.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NetworkState())
}
