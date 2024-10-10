/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.logic.network

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal actual class NetworkStateObserverImpl(
    connectivityManager: ConnectivityManager,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : NetworkStateObserver {

    constructor(
        appContext: Context,
        kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    ) : this(
        connectivityManager = appContext.getSystemService(Activity.CONNECTIVITY_SERVICE) as ConnectivityManager,
        kaliumDispatcher = kaliumDispatcher
    )

    private val defaultNetworkDataStateFlow: MutableStateFlow<DefaultNetworkData>
    private val networkStateFlow: StateFlow<NetworkState>
    private val scope = CoroutineScope(SupervisorJob() + kaliumDispatcher.default)

    init {
        val initialDefaultNetworkData = connectivityManager.activeNetwork?.let {
            val defaultNetworkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            DefaultNetworkData.Connected(it, defaultNetworkCapabilities)
        } ?: DefaultNetworkData.NotConnected
        defaultNetworkDataStateFlow = MutableStateFlow(initialDefaultNetworkData)
        val initialState = when (initialDefaultNetworkData) {
            is DefaultNetworkData.Connected -> initialDefaultNetworkData.networkCapabilities.toState()
            is DefaultNetworkData.NotConnected -> NetworkState.NotConnected
        }
        networkStateFlow = defaultNetworkDataStateFlow
            .map { networkData ->
                if (networkData is DefaultNetworkData.Connected) {
                    if (networkData.isBlocked) NetworkState.ConnectedWithoutInternet
                    else networkData.networkCapabilities.toState()
                } else NetworkState.NotConnected
            }
            .stateIn(scope, SharingStarted.Eagerly, initialState)

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                kaliumLogger.i(
                    "${NetworkStateObserver.TAG} capabilities changed " +
                            "internet:${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)} " +
                            "validated:${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
                )
                defaultNetworkDataStateFlow.update { DefaultNetworkData.Connected(network, networkCapabilities) }
            }

            override fun onLost(network: Network) {
                kaliumLogger.i("${NetworkStateObserver.TAG} lost connection")
                defaultNetworkDataStateFlow.update { DefaultNetworkData.NotConnected }
                super.onLost(network)
            }

            override fun onUnavailable() {
                kaliumLogger.i("${NetworkStateObserver.TAG} connection unavailable")
                defaultNetworkDataStateFlow.update { DefaultNetworkData.NotConnected }
                super.onUnavailable()
            }

            override fun onAvailable(network: Network) {
                kaliumLogger.i("${NetworkStateObserver.TAG} connection available")
                defaultNetworkDataStateFlow.update { DefaultNetworkData.Connected(network) }
                super.onAvailable(network)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                kaliumLogger.i("${NetworkStateObserver.TAG} losing connection maxMsToLive: $maxMsToLive")
                super.onLosing(network, maxMsToLive)
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                kaliumLogger.i("${NetworkStateObserver.TAG} block connection changed to $blocked")
                defaultNetworkDataStateFlow.update {
                    when (it) {
                        is DefaultNetworkData.Connected -> {
                            it.copy(isBlocked = blocked)
                        }

                        is DefaultNetworkData.NotConnected -> {
                            if (blocked) it
                            else DefaultNetworkData.Connected(network)
                        }
                    }
                }
                super.onBlockedStatusChanged(network, blocked)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun NetworkCapabilities?.toState(): NetworkState {
        val hasInternet = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        // There may be some edge cases where on-premise environments could be considered "not validated"
        // and should still be able to make requests.
        val isValidated = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return when {
            hasInternet && isValidated -> NetworkState.ConnectedWithInternet
            else -> NetworkState.ConnectedWithoutInternet
        }
    }

    override fun observeNetworkState(): StateFlow<NetworkState> = networkStateFlow

    private sealed class DefaultNetworkData {
        data object NotConnected : DefaultNetworkData()
        data class Connected(
            val network: Network,
            val networkCapabilities: NetworkCapabilities? = null,
            val isBlocked: Boolean = false
        ) : DefaultNetworkData()
    }
}
