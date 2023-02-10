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
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn

actual class NetworkStateObserverImpl(
    appContext: Context,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : NetworkStateObserver {
    private val connectivityManager: ConnectivityManager = appContext.getSystemService(Activity.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + kaliumDispatcher.io)
    private val networkStateSharedFlow: Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                trySend(networkCapabilities.toState())
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                super.onBlockedStatusChanged(network, blocked)
                trySend(if (blocked) NetworkState.ConnectedWithoutInternet else NetworkState.ConnectedWithInternet)
            }

            override fun onLost(network: Network) {
                trySend(NetworkState.NotConnected)
                super.onLost(network)
            }

            override fun onUnavailable() {
                trySend(NetworkState.NotConnected)
                super.onUnavailable()
            }
        }

        trySend(connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork).toState())

        connectivityManager.registerDefaultNetworkCallback(callback).also {
            kaliumLogger.d("cyka Registering network callback")
        }

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(),
        replay = 1
    )

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

    override fun observeNetworkState(): Flow<NetworkState> = networkStateSharedFlow.distinctUntilChanged()
}
