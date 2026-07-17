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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

interface NetworkStateObserver {

    fun observeNetworkState(): StateFlow<NetworkState>
    fun observeCurrentNetwork(): StateFlow<CurrentNetwork?>

    /**
     * Delays for [delay], completing earlier if a newly validated network becomes available.
     *
     * @return `true` when a reconnection ended the wait, or `false` when [delay] elapsed.
     */
    suspend fun delayUntilConnectedWithInternetAgain(delay: Duration): Boolean {
        // Delay for given amount but break it if reconnected again.
        kaliumUtilLogger.i("$TAG delayUntilConnectedWithInternetAgain for $delay")
        return withTimeoutOrNull(delay) {
            // Drop the current value, so it will complete only if the connection changed again to connected during that time.
            observeNetworkState()
                .drop(1)
                .filterIsInstance<NetworkState.ConnectedWithInternet>()
                .first()
        } != null
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

data class CurrentNetwork(val id: String, val type: Type?, val hasInternetAccess: Boolean) {
    enum class Type { WIFI, CELLULAR, OTHER }
}
