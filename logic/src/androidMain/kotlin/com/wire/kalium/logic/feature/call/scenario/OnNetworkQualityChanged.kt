package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.NetworkQualityChangedHandler

class OnNetworkQualityChanged : NetworkQualityChangedHandler {

    override fun onNetworkQualityChanged(
        conversationId: String,
        userId: String,
        clientId: String,
        quality: Int,
        roundTripTimeInMilliseconds: Int,
        upstreamPacketLossPercentage: Int,
        downstreamPacketLossPercentage: Int,
        arg: Pointer?
    ) {
        // Not yet implemented
    }
}
