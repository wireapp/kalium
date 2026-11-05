/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NetworkStateObserverTest {

    @Test
    fun givenDisconnectedState_whenNetworkReconnects_thenShouldCompleteWithReconnectResult() = runTest {
        val networkState = MutableStateFlow<NetworkState>(NetworkState.NotConnected)
        val observer = TestNetworkStateObserver(networkState)

        val result = backgroundScope.async {
            observer.delayUntilConnectedWithInternetAgain(10.seconds)
        }
        runCurrent()

        networkState.value = NetworkState.ConnectedWithInternet
        runCurrent()

        assertTrue(result.await())
    }

    @Test
    fun givenDisconnectedState_whenDelayElapses_thenShouldCompleteWithTimeoutResult() = runTest {
        val observer = TestNetworkStateObserver(MutableStateFlow(NetworkState.NotConnected))

        val result = backgroundScope.async {
            observer.delayUntilConnectedWithInternetAgain(1.seconds)
        }
        runCurrent()
        advanceTimeBy(1.seconds)
        runCurrent()

        assertFalse(result.await())
    }

    @Test
    fun givenAlreadyConnectedState_whenWaitingForReconnect_thenShouldIgnoreCurrentState() = runTest {
        val observer = TestNetworkStateObserver(MutableStateFlow(NetworkState.ConnectedWithInternet))

        val result = backgroundScope.async {
            observer.delayUntilConnectedWithInternetAgain(1.seconds)
        }
        runCurrent()

        assertFalse(result.isCompleted)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertFalse(result.await())
    }

    @Test
    fun givenValidatedNetwork_whenAnotherValidatedNetworkBecomesCurrent_thenShouldCompleteWithReconnectResult() = runTest {
        // Switching between validated networks does not change the network state, only the current network.
        val currentNetwork = MutableStateFlow<CurrentNetwork?>(WIFI)
        val observer = TestNetworkStateObserver(MutableStateFlow(NetworkState.ConnectedWithInternet), currentNetwork)

        val result = backgroundScope.async {
            observer.delayUntilConnectedWithInternetAgain(10.minutes)
        }
        runCurrent()
        assertFalse(result.isCompleted)

        currentNetwork.value = CELLULAR
        runCurrent()

        assertTrue(result.await())
    }

    @Test
    fun givenValidatedNetwork_whenAnotherNetworkWithoutInternetBecomesCurrent_thenShouldKeepWaiting() = runTest {
        val currentNetwork = MutableStateFlow<CurrentNetwork?>(WIFI)
        val observer = TestNetworkStateObserver(MutableStateFlow(NetworkState.ConnectedWithInternet), currentNetwork)

        val result = backgroundScope.async {
            observer.delayUntilConnectedWithInternetAgain(1.seconds)
        }
        runCurrent()

        currentNetwork.value = CELLULAR.copy(hasInternetAccess = false)
        runCurrent()
        assertFalse(result.isCompleted)

        advanceTimeBy(1.seconds)
        runCurrent()
        assertFalse(result.await())
    }

    @Test
    fun givenPreviousNetwork_whenAnotherValidatedNetworkIsAlreadyCurrent_thenShouldCompleteImmediately() = runTest {
        // The network changed while the failed attempt was running, before the wait started.
        val observer = TestNetworkStateObserver(MutableStateFlow(NetworkState.ConnectedWithInternet), MutableStateFlow(CELLULAR))

        val result = backgroundScope.async {
            observer.delayUntilConnectedWithInternetAgain(10.minutes, previousNetworkId = WIFI.id)
        }
        runCurrent()

        assertTrue(result.isCompleted)
        assertTrue(result.await())
    }

    private class TestNetworkStateObserver(
        private val networkState: StateFlow<NetworkState>,
        private val currentNetwork: StateFlow<CurrentNetwork?> = MutableStateFlow(null),
    ) : NetworkStateObserver {

        override fun observeNetworkState(): StateFlow<NetworkState> = networkState

        override fun observeCurrentNetwork(): StateFlow<CurrentNetwork?> = currentNetwork
    }

    private companion object {
        val WIFI = CurrentNetwork("wifi", CurrentNetwork.Type.WIFI, hasInternetAccess = true)
        val CELLULAR = CurrentNetwork("cellular", CurrentNetwork.Type.CELLULAR, hasInternetAccess = true)
    }
}
