/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual class NetworkStateObserverImpl(appContext: Context) : NetworkStateObserver {
    private val connectivityManager: ConnectivityManager = appContext.getSystemService(Activity.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val networkStateFlow: MutableStateFlow<NetworkState>

    init {
        val initialState = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork).toState()
        networkStateFlow = MutableStateFlow(initialState)

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                networkStateFlow.tryEmit(networkCapabilities.toState())
            }

            override fun onLost(network: Network) {
                networkStateFlow.tryEmit(NetworkState.NotConnected)
                super.onLost(network)
            }

            override fun onUnavailable() {
                networkStateFlow.tryEmit(NetworkState.NotConnected)
                super.onUnavailable()
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
            !hasInternet -> NetworkState.NotConnected
            isValidated -> NetworkState.ConnectedWithInternet
            else -> NetworkState.ConnectedWithoutInternet
        }
    }

    override fun observeNetworkState(): StateFlow<NetworkState> = networkStateFlow
}
