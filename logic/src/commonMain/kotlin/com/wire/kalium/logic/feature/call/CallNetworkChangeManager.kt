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

@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.call

import com.wire.kalium.network.CurrentNetwork
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch

internal abstract class CallNetworkChangeManager(
    scope: CoroutineScope,
    private val networkStateObserver: NetworkStateObserver,
) {
    internal abstract fun networkChanged()

    init {
        scope.launch {
            networkStateObserver.observeCurrentNetwork()
                .scan(null) { lastConnectedNetworkId: String?, currentNetwork: CurrentNetwork? ->
                    // emit id of last network with Internet access, or null if none
                    if (currentNetwork?.hasInternetAccess == true && currentNetwork.id != lastConnectedNetworkId) {
                        currentNetwork.id // new connected network with Internet access, emit its id
                    } else {
                        lastConnectedNetworkId // no network or same as before or new one without Internet, emit last connected network id
                    }
                }
                .filter { it != null } // drop nulls, care only about connected networks
                .distinctUntilChanged() // drop the duplicates, care only about changes
                .drop(1) // drop the initial one, care only about subsequent changes
                .collectLatest {
                    networkChanged()
                }
        }
    }
}
