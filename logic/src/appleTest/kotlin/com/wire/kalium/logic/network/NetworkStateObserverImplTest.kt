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
package com.wire.kalium.logic.network

import com.wire.kalium.network.NetworkState
import platform.Network.nw_path_status_invalid
import platform.Network.nw_path_status_satisfiable
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_status_unsatisfied
import kotlin.test.Test
import kotlin.test.assertEquals

class NetworkStateObserverImplTest {

    @Test
    fun givenSatisfiedStatus_whenMappingToNetworkState_thenConnectedWithInternet() {
        assertEquals(NetworkState.ConnectedWithInternet, nwPathStatusToNetworkState(nw_path_status_satisfied))
    }

    @Test
    fun givenSatisfiableStatus_whenMappingToNetworkState_thenNotConnected() {
        // satisfiable means the path is not currently usable, so it must not be reported as connected
        assertEquals(NetworkState.NotConnected, nwPathStatusToNetworkState(nw_path_status_satisfiable))
    }

    @Test
    fun givenUnsatisfiedStatus_whenMappingToNetworkState_thenNotConnected() {
        assertEquals(NetworkState.NotConnected, nwPathStatusToNetworkState(nw_path_status_unsatisfied))
    }

    @Test
    fun givenInvalidStatus_whenMappingToNetworkState_thenNotConnected() {
        assertEquals(NetworkState.NotConnected, nwPathStatusToNetworkState(nw_path_status_invalid))
    }

    @Test
    fun givenNullStatus_whenMappingToNetworkState_thenNotConnected() {
        assertEquals(NetworkState.NotConnected, nwPathStatusToNetworkState(null))
    }

    @Test
    fun givenNoInterfaces_whenBuildingNetworkId_thenFallbackIsUsed() {
        assertEquals("WIFI", buildCurrentNetworkId(emptyList(), fallback = "WIFI"))
    }

    @Test
    fun givenSingleInterface_whenBuildingNetworkId_thenInterfaceIsUsed() {
        assertEquals("en0-6", buildCurrentNetworkId(listOf("en0-6"), fallback = "WIFI"))
    }

    @Test
    fun givenMultipleInterfaces_whenBuildingNetworkId_thenTheyAreJoined() {
        assertEquals("en0-6+pdp_ip0-2", buildCurrentNetworkId(listOf("en0-6", "pdp_ip0-2"), fallback = "OTHER"))
    }
}
