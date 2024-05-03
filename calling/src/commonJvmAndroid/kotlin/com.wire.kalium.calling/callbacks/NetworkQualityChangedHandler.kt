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

package com.wire.kalium.calling.callbacks

import com.sun.jna.Callback
import com.sun.jna.Pointer

/**
 * QUALITY_NORMAL          = 1
 * QUALITY_MEDIUM          = 2
 * QUALITY_POOR            = 3
 * QUALITY_NETWORK_PROBLEM = 4
 */
interface NetworkQualityChangedHandler : Callback {

    @Suppress("LongParameterList")
    fun onNetworkQualityChanged(
        conversationId: String,
        userId: String?,
        clientId: String?,
        quality: Int,
        roundTripTimeInMilliseconds: Int,
        upstreamPacketLossPercentage: Int,
        downstreamPacketLossPercentage: Int,
        arg: Pointer?
    )
}
