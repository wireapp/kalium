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

import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.cinterop.toKString
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import platform.Network.nw_interface_get_index
import platform.Network.nw_interface_get_name
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_enumerate_interfaces
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_status_t
import platform.Network.nw_path_t
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_queue_create

internal actual class NetworkStateObserverImpl : NetworkStateObserver {

    // Conservative initial value: until the path monitor delivers its first update, we don't know the current status.
    private val networkStateFlow = MutableStateFlow<NetworkState>(NetworkState.NotConnected)
    private val currentNetworkFlow = MutableStateFlow<CurrentNetwork?>(null)
    private val monitor = nw_path_monitor_create()

    // Dedicated serial queue: the path monitor's update handler must run on a serial queue so updates are
    // delivered and applied to the flows in order. A global concurrent queue could overlap invocations and
    // leave the flows holding a stale value.
    private val queue = dispatch_queue_create("com.wire.kalium.network-monitor", null)

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            val networkState = path.toState()
            networkStateFlow.value = networkState
            currentNetworkFlow.value = path.toCurrentNetwork(networkState)
        }
        nw_path_monitor_set_queue(monitor = monitor, queue = queue)
        nw_path_monitor_start(monitor)
    }

    actual override fun observeNetworkState(): StateFlow<NetworkState> = networkStateFlow

    actual override fun observeCurrentNetwork(): StateFlow<CurrentNetwork?> = currentNetworkFlow

    private fun nw_path_t.toState(): NetworkState = nwPathStatusToNetworkState(this?.let { nw_path_get_status(it) })

    private fun nw_path_t.toCurrentNetwork(networkState: NetworkState): CurrentNetwork? = when (networkState) {
        NetworkState.NotConnected -> null
        NetworkState.ConnectedWithInternet,
        NetworkState.ConnectedWithoutInternet -> this?.let { path ->
            val type = path.toCurrentNetworkType()
            CurrentNetwork(
                id = path.toCurrentNetworkId(type),
                type = type,
                hasInternetAccess = networkState == NetworkState.ConnectedWithInternet
            )
        }
    }

    private fun nw_path_t.toCurrentNetworkType(): CurrentNetwork.Type = when {
        nw_path_uses_interface_type(this, nw_interface_type_wifi) -> CurrentNetwork.Type.WIFI
        nw_path_uses_interface_type(this, nw_interface_type_cellular) -> CurrentNetwork.Type.CELLULAR
        else -> CurrentNetwork.Type.OTHER
    }

    /**
     * Builds an identifier for the current network from the interfaces the path uses.
     *
     * Known limitation: the identifier is derived from interface name + index, which stay constant for a given
     * physical interface. Switching between two networks on the same interface (e.g. changing Wi-Fi SSID) keeps the
     * same id and therefore is not detected as a network change by consumers such as [CurrentNetwork] observers.
     * Distinguishing those would require the SSID, which needs a location permission we intentionally do not request here.
     */
    private fun nw_path_t.toCurrentNetworkId(type: CurrentNetwork.Type): String {
        val interfaces = mutableListOf<String>()
        nw_path_enumerate_interfaces(this) { networkInterface ->
            val interfaceName = nw_interface_get_name(networkInterface)?.toKString()
            val interfaceIndex = nw_interface_get_index(networkInterface)
            interfaces += interfaceName?.let { "$it-$interfaceIndex" } ?: interfaceIndex.toString()
            true
        }
        return buildCurrentNetworkId(interfaces, fallback = type.name)
    }
}

/**
 * Maps a `nw_path` status to a [NetworkState].
 *
 * Only [nw_path_status_satisfied] is treated as connected: a satisfied path is usable for sending data. Every other
 * status (unsatisfied, satisfiable, invalid, or `null`) means there is no usable connection right now — `satisfiable`
 * in particular means the path could be activated by a connection attempt but is not currently available, so it is
 * mapped to [NetworkState.NotConnected] rather than to a "connected without internet" state.
 */
internal fun nwPathStatusToNetworkState(status: nw_path_status_t?): NetworkState = when (status) {
    nw_path_status_satisfied -> NetworkState.ConnectedWithInternet
    else -> NetworkState.NotConnected
}

/**
 * Joins the collected interface identifiers into a single stable id, falling back to [fallback] when the path exposes
 * no interfaces.
 */
internal fun buildCurrentNetworkId(interfaceIds: List<String>, fallback: String): String =
    interfaceIds.takeUnless { it.isEmpty() }?.sorted()?.joinToString(separator = "+") ?: fallback
