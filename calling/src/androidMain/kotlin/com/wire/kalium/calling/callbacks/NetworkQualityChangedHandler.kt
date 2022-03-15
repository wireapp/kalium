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
    fun onNetworkQualityChanged(
        conversationId: String,
        userId: String,
        clientId: String,
        quality: Int,
        roundTripTimeInMilliseconds: Int,
        upstreamPacketLossPercentage: Int,
        downstreamPacketLossPercentage: Int,
        arg: Pointer?
    )
}
