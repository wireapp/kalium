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
package com.wire.kalium.network

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

interface NetworkStateObserver {

    fun observeNetworkState(): StateFlow<NetworkState>

    // Delay which will be completed earlier if there is a reconnection in the meantime.
    suspend fun delayUntilConnectedWithInternetAgain(delay: Duration) {
        // Delay for given amount but break it if reconnected again.
        kaliumUtilLogger.i("$TAG delayUntilConnectedWithInternetAgain $delay")
        withTimeoutOrNull(delay) {
            // Drop the current value, so it will complete only if the connection changed again to connected during that time.
            observeNetworkState()
                .drop(1)
                .filter { it is NetworkState.ConnectedWithInternet }
                .firstOrNull()
        }
    }

    companion object {
        const val TAG = "NetworkStateObserver"
    }
}

sealed class NetworkState {
    data object ConnectedWithInternet : NetworkState()
    data object ConnectedWithoutInternet : NetworkState()
    data object NotConnected : NetworkState()
}
