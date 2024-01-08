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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.NetworkState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("MaxLineLength")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
class NetworkStateObserverImplTest {

    private val dispatcher = TestKaliumDispatcher

    @Test
    fun givenNoNetworkConnected_thenStateIsNotConnected() = runTest(dispatcher.default) {
        // given
        val (_, networkStateObserverImpl) = Arrangement()
            .arrange()
        // then
        assertEquals(NetworkState.NotConnected, networkStateObserverImpl.observeNetworkState().value)
    }

    @Test
    fun givenOneNetworkConnectedWithoutInternetValidated_thenStateIsConnectedWithoutInternet() = runTest(dispatcher.default) {
        // given
        val (_, networkStateObserverImpl) = Arrangement()
            .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = false)
            .arrange()
        // then
        assertEquals(NetworkState.ConnectedWithoutInternet, networkStateObserverImpl.observeNetworkState().value)
    }

    @Test
    fun givenOneNetworkConnectedWithInternetValidated_thenStateIsConnectedWithInternet() = runTest(dispatcher.default) {
        // given
        val (_, networkStateObserverImpl) = Arrangement()
            .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
            .arrange()
        // then
        assertEquals(NetworkState.ConnectedWithInternet, networkStateObserverImpl.observeNetworkState().value)
    }

    @Test
    fun givenNoNetworkConnected_whenOneConnectsWithoutInternetValidated_thenStateChangesToConnectedWithoutInternet() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .arrange()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.NotConnected, awaitItem())
                arrangement.connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = false)
                assertEquals(NetworkState.ConnectedWithoutInternet, awaitItem())
            }
        }

    @Test
    fun givenNoNetworkConnected_whenOneConnectsWithInternetValidated_thenStateChangesToConnectedWithInternet() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .arrange()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.NotConnected, awaitItem())
                arrangement.connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
                assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
            }
        }

    @Test
    fun givenOneNetworkConnectedWithInternetValidated_whenItLosesInternetValidation_thenStateChangesToConnectedWithoutInternet() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
                .arrange()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
                arrangement.changeNetworkCapabilities(networkType = NetworkType.MOBILE, withInternetValidated = false)
                assertEquals(NetworkState.ConnectedWithoutInternet, awaitItem())
            }
        }

    @Test
    fun givenOneNetworkConnectedWithInternetValidated_whenItDisconnects_thenStateChangesToNotConnected() = runTest(dispatcher.default) {
        // given
        val (arrangement, networkStateObserverImpl) = Arrangement()
            .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
            .arrange()
        // when-then
        networkStateObserverImpl.observeNetworkState().test {
            assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
            arrangement.disconnectNetwork(networkType = NetworkType.MOBILE)
            assertEquals(NetworkState.NotConnected, awaitItem())
        }
    }

    @Test
    fun givenOneNetworkConnectedWithInternetValidated_whenOtherConnectsWithInternetValidated_thenStateDoesNotChange() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
                .arrange()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
                arrangement.connectNetwork(networkType = NetworkType.WIFI, setAsDefault = true, withInternetValidated = true)
                expectNoEvents()
            }
        }

    @Test
    fun givenBothNetworksConnectedWithInternetValidated_whenDefaultDisconnectsAndDefaultIsChanged_thenStateDoesNotChange() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
                .connectNetwork(networkType = NetworkType.WIFI, setAsDefault = false, withInternetValidated = true)
                .arrange()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
                arrangement.disconnectNetwork(networkType = NetworkType.MOBILE)
                expectNoEvents()
            }
        }

    @Test
    fun givenBothNetworksConnectedWithInternetValidated_whenOtherDisconnects_thenStateDoesNotChange() = runTest(dispatcher.default) {
        // given
        val (arrangement, networkStateObserverImpl) = Arrangement()
            .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
            .connectNetwork(networkType = NetworkType.WIFI, setAsDefault = false, withInternetValidated = true)
            .arrange()
        // when-then
        networkStateObserverImpl.observeNetworkState().test {
            assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
            arrangement.disconnectNetwork(networkType = NetworkType.WIFI)
            expectNoEvents()
        }
    }

    @Test
    fun givenBothNetworksConnectedWithInternetValidated_whenDefaultNetworkChanges_thenStateDoesNotChange() = runTest(dispatcher.default) {
        // given
        val (arrangement, networkStateObserverImpl) = Arrangement()
            .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
            .connectNetwork(networkType = NetworkType.WIFI, setAsDefault = false, withInternetValidated = true)
            .changeDefaultNetwork(NetworkType.MOBILE)
            .arrange()
        // when-then
        networkStateObserverImpl.observeNetworkState().test {
            assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
            arrangement.changeDefaultNetwork(NetworkType.WIFI)
            expectNoEvents()
        }
    }

    @Test
    fun givenOneNetworkConnectedWithInternetValidated_whenItChangesToBlocked_thenStateChangesToConnectedWithoutInternet() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
                .arrange()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
                arrangement.changeNetworkBlocked(NetworkType.MOBILE, true)
                assertEquals(NetworkState.ConnectedWithoutInternet, awaitItem())
            }
        }

    @Test
    fun givenOneNetworkConnectedWithInternetValidatedButBlocked_whenItChangesToNotBlocked_thenStateChangesToConnectedWithInternet() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = true)
                .arrange()
            // needs to be done after the observer is created to be handled by the callback as it's not a network state but just a network event
            arrangement.changeNetworkBlocked(NetworkType.MOBILE, true)
            advanceUntilIdle()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.ConnectedWithoutInternet, awaitItem())
                arrangement.changeNetworkBlocked(NetworkType.MOBILE, false)
                assertEquals(NetworkState.ConnectedWithInternet, awaitItem())
            }
        }

    @Test
    fun givenOneNetworkConnectedWithoutInternetValidated_whenItChangesToBlocked_thenStateDoesNotChange() = runTest(dispatcher.default) {
        // given
        val (arrangement, networkStateObserverImpl) = Arrangement()
            .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = false)
            .arrange()
        // when-then
        networkStateObserverImpl.observeNetworkState().test {
            assertEquals(NetworkState.ConnectedWithoutInternet, awaitItem())
            arrangement.changeNetworkBlocked(NetworkType.MOBILE, true)
            expectNoEvents()
        }
    }

    @Test
    fun givenOneNetworkConnectedWithoutInternetValidatedButBlocked_whenItChangesToNotBlocked_thenStateDoesNotChange() =
        runTest(dispatcher.default) {
            // given
            val (arrangement, networkStateObserverImpl) = Arrangement()
                .connectNetwork(networkType = NetworkType.MOBILE, setAsDefault = true, withInternetValidated = false)
                .arrange()
            // needs to be done after the observer is created to be handled by the callback as it's not a network state but just a network event
            arrangement.changeNetworkBlocked(NetworkType.MOBILE, true)
            advanceUntilIdle()
            // when-then
            networkStateObserverImpl.observeNetworkState().test {
                assertEquals(NetworkState.ConnectedWithoutInternet, awaitItem())
                arrangement.changeNetworkBlocked(NetworkType.MOBILE, false)
                expectNoEvents()
            }
        }

    /*
     * All the network logic and changes emulated by functions in this class are following Android network state documentation and examples
     * https://developer.android.com/training/basics/network-ops/reading-network-state
     */
    inner class Arrangement {
        private val context: Context = ApplicationProvider.getApplicationContext()
        private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        private val networkStateObserverImpl: NetworkStateObserverImpl by lazy { NetworkStateObserverImpl(context, dispatcher) }

        init {
            shadowOf(connectivityManager).apply {
                clearAllNetworks()
                setActiveNetworkInfo(null)
                setDefaultNetworkActive(false)
            }
        }

        fun changeDefaultNetwork(networkType: NetworkType) = apply {
            connectivityManager.getNetworkByType(networkType)?.let { network ->
                changeDefaultNetwork(network)
            }
        }

        private fun changeDefaultNetwork(network: Network) = apply {
            connectivityManager.getNetworkInfo(network)?.let { networkInfo ->
                shadowOf(connectivityManager).apply {
                    setActiveNetworkInfo(networkInfo)
                    setDefaultNetworkActive(true)
                }
            }
            (connectivityManager.getNetworkCapabilities(network) ?: ShadowNetworkCapabilities.newInstance()).let { capabilities ->
                shadowOf(connectivityManager).apply {
                    networkCallbacks.forEach {
                        // when registerDefaultNetworkCallback is used and default network changes, onAvailable is called for that one
                        it.onAvailable(network)
                        // when network becomes available, onCapabilitiesChanged is also called right after
                        it.onCapabilitiesChanged(network, capabilities)
                    }
                }
            }
        }

        fun changeNetworkCapabilities(networkType: NetworkType, withInternetValidated: Boolean) = apply {
            connectivityManager.getNetworkByType(networkType)
                ?.let { network ->
                    connectivityManager.getNetworkCapabilities(network)?.apply {
                        shadowOf(this)
                            .apply {
                                if (withInternetValidated) addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                else removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                                if (withInternetValidated) addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                else removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                            }
                    }?.let { newCapabilities ->
                        shadowOf(connectivityManager).apply {
                            setNetworkCapabilities(network, newCapabilities)
                            // when registerDefaultNetworkCallback is used, onCapabilitiesChanged is called only for the default network
                            if (connectivityManager.activeNetwork == network) {
                                networkCallbacks.forEach {
                                    it.onCapabilitiesChanged(network, newCapabilities)
                                }
                            }
                        }
                    }
                }
        }

        fun connectNetwork(networkType: NetworkType, setAsDefault: Boolean, withInternetValidated: Boolean) = apply {
            val network = ShadowNetwork.newInstance(networkType.type)
            val networkInfo = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, networkType.type, 0, true, true)
            shadowOf(connectivityManager).apply {
                addNetwork(network, networkInfo)
            }
            changeNetworkCapabilities(networkType, withInternetValidated)
            if (setAsDefault) {
                changeDefaultNetwork(networkType)
            }
        }

        fun disconnectNetwork(networkType: NetworkType) = apply {
            connectivityManager.getNetworkByType(networkType)?.let { network ->
                (connectivityManager.activeNetwork == network).let { isDefaultNetwork ->
                    shadowOf(connectivityManager).apply {
                        removeNetwork(network)
                        // when registerDefaultNetworkCallback is used, when default network is lost, changes to another available network
                        // and if there is no other network available, onLost is called for the default network
                        if (isDefaultNetwork) {
                            connectivityManager.allNetworks.firstOrNull { it != network }.let { otherNetwork ->
                                if (otherNetwork != null) { // switched automatically to another available network
                                    changeDefaultNetwork(otherNetwork)
                                } else { // no other network available, clear default network
                                    setActiveNetworkInfo(null)
                                    setDefaultNetworkActive(false)
                                    networkCallbacks.forEach {
                                        it.onLost(network)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun changeNetworkBlocked(networkType: NetworkType, isBlocked: Boolean) = apply {
            connectivityManager.getNetworkByType(networkType)?.let { network ->
                (connectivityManager.getNetworkCapabilities(network) ?: ShadowNetworkCapabilities.newInstance()).let { capabilities ->
                    shadowOf(connectivityManager).apply {
                        networkCallbacks.forEach {
                            it.onBlockedStatusChanged(network, isBlocked)
                        }
                    }
                }
            }
        }

        private fun ConnectivityManager.getNetworkByType(networkType: NetworkType): Network? = allNetworks.firstOrNull {
            getNetworkInfo(it)?.type == networkType.type
        }

        internal fun arrange() = this to networkStateObserverImpl
    }

    enum class NetworkType(val type: Int) {
        WIFI(ConnectivityManager.TYPE_WIFI),
        MOBILE(ConnectivityManager.TYPE_MOBILE)
    }
}
