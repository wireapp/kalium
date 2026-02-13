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
package com.wire.kalium.logic.feature.call

import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CallNetworkChangeManagerTest {
    @Test
    fun givenNoInitialCurrentNetwork_whenItStaysAndNotChanges_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(null)
            .arrange()
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenInitialCurrentNetworkHasInternet_whenItStaysAndNotChanges_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
            .arrange()
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenNoInitialCurrentNetwork_whenSomeNetworkConnectsWithInternet_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(null)
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenInitialCurrentNetworkHasInternet_whenItDisconnects_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(null)
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenInitialCurrentNetworkHasInternet_whenItLosesInternet_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = false))
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenInitialCurrentNetworkDoesNotHaveInternet_whenItGainsInternet_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = false))
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenInitialCurrentNetworkHasInternet_whenItLosesAndGainsBackInternet_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = false))
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenCurrentNetworkHasInternet_whenItChangesToOtherWithNoInternet_thenDoNotInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(CurrentNetwork(id = "2", type = CurrentNetwork.Type.CELLULAR, hasInternetAccess = false))
        runCurrent()
        // then
        assertEquals(0, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenCurrentNetworkHasInternet_whenItChangesToOtherWithInternet_thenInvokeNetworkChanged() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
            .arrange()
        runCurrent()
        // when
        arrangement.setCurrentNetwork(CurrentNetwork(id = "2", type = CurrentNetwork.Type.CELLULAR, hasInternetAccess = true))
        runCurrent()
        // then
        assertEquals(1, arrangement.networkChangedInvokeCount)
    }

    @Test
    fun givenCurrentNetworkHasInternet_whenChangesToOtherWithNoInternetAndGainsIt_thenInvokeNetworkChangedAfterGainedInternet() = runTest {
        // given
        val (arrangement, _) = Arrangement(this.backgroundScope)
            .setCurrentNetwork(CurrentNetwork(id = "1", type = CurrentNetwork.Type.WIFI, hasInternetAccess = true))
            .arrange()
        runCurrent()
        // when - changes to other without internet
        arrangement.setCurrentNetwork(CurrentNetwork(id = "2", type = CurrentNetwork.Type.CELLULAR, hasInternetAccess = false))
        runCurrent()
        // then - do not invoke yet, as changed network still has no Internet
        assertEquals(0, arrangement.networkChangedInvokeCount)
        // when - changed network gains Internet
        arrangement.setCurrentNetwork(CurrentNetwork(id = "2", type = CurrentNetwork.Type.CELLULAR, hasInternetAccess = true))
        runCurrent()
        // then - invoke now, as changed network gained Internet
        assertEquals(1, arrangement.networkChangedInvokeCount)
    }

    inner class Arrangement(private val scope: CoroutineScope) {
        var networkChangedInvokeCount: Int = 0
            private set
        private val networkStateObserver = object : NetworkStateObserver {
            private val currentNetwork = MutableStateFlow<CurrentNetwork?>(null)
            override fun observeNetworkState(): StateFlow<NetworkState> { TODO("not needed for this test") }
            override fun observeCurrentNetwork(): StateFlow<CurrentNetwork?> = currentNetwork
            fun setCurrentNetwork(network: CurrentNetwork?) { currentNetwork.value = network }
        }

        fun setCurrentNetwork(network: CurrentNetwork?) = apply {
            networkStateObserver.setCurrentNetwork(network)
        }

        internal fun arrange() = this to object : CallNetworkChangeManager(scope = scope, networkStateObserver = networkStateObserver) {
            override fun networkChanged() { networkChangedInvokeCount++ }
        }
    }
}
