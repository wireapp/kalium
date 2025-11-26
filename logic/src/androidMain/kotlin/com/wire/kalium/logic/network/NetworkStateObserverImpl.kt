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
import android.os.Build
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

internal actual class NetworkStateObserverImpl(
    private val connectivityManager: ConnectivityManager,
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
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        val initialDefaultNetworkData = connectivityManager.activeNetwork?.let {
            val defaultNetworkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
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
            .buffer(capacity = 0)
            .stateIn(scope, SharingStarted.Eagerly, initialState)

        networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                val loggerMessage = mutableMapOf<String, String>().apply {
                    put("internet", networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).toString())
                    put("validated", networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).toString())
                    put("not restricted", networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED).toString())

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        put("foreground", networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_FOREGROUND).toString())
                        put("not congested", networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED).toString())
                        put("not suspended", networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED).toString())
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put("signalStrength", networkCapabilities.signalStrength.toString())
                    }
                }
                kaliumLogger.logStructuredJson(KaliumLogLevel.INFO, "${NetworkStateObserver.TAG} capabilities changed", loggerMessage)
                defaultNetworkDataStateFlow.update { current ->
                    // Only update if this is for the current tracked network or if not connected
                    if (current is DefaultNetworkData.Connected && current.network == network) {
                        current.copy(networkCapabilities = networkCapabilities)
                    } else if (current is DefaultNetworkData.NotConnected) {
                        // New network becoming available
                        DefaultNetworkData.Connected(network, networkCapabilities)
                    } else {
                        current
                    }
                }
            }

            override fun onLost(network: Network) {
                kaliumLogger.i("${NetworkStateObserver.TAG} lost connection")
                defaultNetworkDataStateFlow.update { current ->
                    // Only mark as not connected if this is for the current tracked network
                    if (current is DefaultNetworkData.Connected && current.network == network) {
                        DefaultNetworkData.NotConnected
                    } else {
                        current
                    }
                }
                super.onLost(network)
            }

            override fun onUnavailable() {
                kaliumLogger.i("${NetworkStateObserver.TAG} connection unavailable")
                defaultNetworkDataStateFlow.update { DefaultNetworkData.NotConnected }
                super.onUnavailable()
            }

            override fun onAvailable(network: Network) {
                kaliumLogger.i("${NetworkStateObserver.TAG} connection available")
                // Note: onCapabilitiesChanged will be called shortly after with actual capabilities
                // We preserve existing capabilities if switching networks to avoid brief "no internet" state
                defaultNetworkDataStateFlow.update { current ->
                    when (current) {
                        is DefaultNetworkData.Connected -> current.copy(network = network)
                        is DefaultNetworkData.NotConnected -> DefaultNetworkData.Connected(network)
                    }
                }
                super.onAvailable(network)
            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                kaliumLogger.i("${NetworkStateObserver.TAG} losing connection maxMsToLive: $maxMsToLive")
                super.onLosing(network, maxMsToLive)
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                kaliumLogger.i("${NetworkStateObserver.TAG} block connection changed to $blocked")
                defaultNetworkDataStateFlow.update { current ->
                    val updatedValue = when (current) {
                        is DefaultNetworkData.Connected -> {
                            // Only update if this is for the current tracked network
                            if (current.network == network) {
                                current.copy(isBlocked = blocked)
                            } else {
                                current
                            }
                        }

                        is DefaultNetworkData.NotConnected -> current
                    }
                    kaliumLogger.d("${NetworkStateObserver.TAG} default network state $current changed to $updatedValue")
                    updatedValue
                }
                super.onBlockedStatusChanged(network, blocked)
            }
        }
        networkCallback?.let { connectivityManager.registerDefaultNetworkCallback(it) }
    }

    override fun unregister() {
        networkCallback?.let {
            kaliumLogger.i("${NetworkStateObserver.TAG} unregistering network callback")
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
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
