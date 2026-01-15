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
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
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
    private val currentNetworkFlow: StateFlow<CurrentNetwork?>
    private val scope = CoroutineScope(SupervisorJob() + kaliumDispatcher.default)

    init {
        val initialDefaultNetworkData = connectivityManager.activeNetwork?.let {
            val defaultNetworkCapabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            DefaultNetworkData.Connected(it, defaultNetworkCapabilities)
        } ?: DefaultNetworkData.NotConnected
        defaultNetworkDataStateFlow = MutableStateFlow(initialDefaultNetworkData)
        networkStateFlow = defaultNetworkDataStateFlow
            .map { networkData -> networkData.toState() }
            .conflate()
            .stateIn(scope, SharingStarted.Eagerly, initialDefaultNetworkData.toState())
        currentNetworkFlow = defaultNetworkDataStateFlow
            .map { networkData -> networkData.toCurrentNetwork() }
            .conflate()
            .stateIn(scope, SharingStarted.Eagerly, initialDefaultNetworkData.toCurrentNetwork())

        val callback = object : ConnectivityManager.NetworkCallback() {

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
                defaultNetworkDataStateFlow.update {
                    DefaultNetworkData.Connected(
                        network,
                        networkCapabilities
                    )
                }
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
                    val updatedValue = when (it) {
                        is DefaultNetworkData.Connected -> {
                            it.copy(isBlocked = blocked)
                        }

                        is DefaultNetworkData.NotConnected -> {
                            if (blocked) it
                            else DefaultNetworkData.Connected(network)
                        }
                    }
                    kaliumLogger.d("${NetworkStateObserver.TAG} default network state $it changed to $updatedValue")
                    updatedValue
                }
                super.onBlockedStatusChanged(network, blocked)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
    }

    private fun NetworkCapabilities?.hasInternetValidated(): Boolean {
        val hasInternet = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        // There may be some edge cases where on-premise environments could be considered "not validated"
        // and should still be able to make requests.
        val isValidated = this?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return hasInternet && isValidated
    }

    private fun NetworkCapabilities?.toCurrentNetworkType(): CurrentNetwork.Type = when {
        this == null -> CurrentNetwork.Type.UNKNOWN
        this.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> CurrentNetwork.Type.WIFI
        this.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> CurrentNetwork.Type.CELLULAR
        else -> CurrentNetwork.Type.OTHER
    }

    private fun NetworkCapabilities?.toState(): NetworkState = when {
        this.hasInternetValidated() -> NetworkState.ConnectedWithInternet
        else -> NetworkState.ConnectedWithoutInternet
    }

    private fun DefaultNetworkData.toState(): NetworkState = when (this) {
        is DefaultNetworkData.Connected -> when (this.isBlocked) {
            true -> NetworkState.ConnectedWithoutInternet
            false -> this.networkCapabilities.toState()
        }
        else -> NetworkState.NotConnected
    }

    private fun DefaultNetworkData.toCurrentNetwork(): CurrentNetwork? = when (this) {
        is DefaultNetworkData.NotConnected -> null
        is DefaultNetworkData.Connected -> CurrentNetwork(
            this.network.networkHandle.toString(),
            this.networkCapabilities.toCurrentNetworkType(),
            !this.isBlocked && this.networkCapabilities.hasInternetValidated()
        )
    }

    actual override fun observeNetworkState(): StateFlow<NetworkState> = networkStateFlow
    actual override fun observeCurrentNetwork(): StateFlow<CurrentNetwork?> = currentNetworkFlow

    private sealed class DefaultNetworkData {
        data object NotConnected : DefaultNetworkData()
        data class Connected(
            val network: Network,
            val networkCapabilities: NetworkCapabilities? = null,
            val isBlocked: Boolean = false
        ) : DefaultNetworkData()
    }
}
