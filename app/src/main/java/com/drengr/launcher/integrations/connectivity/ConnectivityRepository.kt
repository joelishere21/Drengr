package com.drengr.launcher.integrations.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Immutable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkType(val label: String) {
    None("Offline"),
    Wifi("Wi-Fi"),
    Cellular("Mobile data"),
    Ethernet("Ethernet"),
    Vpn("VPN"),
    Other("Connected"),
}

@Immutable
data class NetworkState(
    val type: NetworkType = NetworkType.None,
    val validated: Boolean = false,
    val metered: Boolean = false,
    val downstreamMbps: Int = 0,
)

/** Default-network status. Requires only ACCESS_NETWORK_STATE (a normal permission). */
@Singleton
class ConnectivityRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)

    val state: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(caps.toState())
            }

            override fun onLost(network: Network) {
                trySend(NetworkState())
            }
        }
        trySend(cm.getNetworkCapabilities(cm.activeNetwork)?.toState() ?: NetworkState())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.conflate().distinctUntilChanged()

    private fun NetworkCapabilities.toState() = NetworkState(
        type = when {
            hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.Vpn
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.Wifi
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.Cellular
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.Ethernet
            else -> NetworkType.Other
        },
        validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        // Bucketed so tiny bandwidth estimate fluctuations don't cause recomposition.
        downstreamMbps = (linkDownstreamBandwidthKbps / 1000 / 10) * 10,
    )
}
