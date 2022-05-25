package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.NetworkQualityChangedHandler
import com.wire.kalium.logic.callingLogger

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
        callingLogger.i("OnNetworkQualityChanged() - ConversationID: $conversationId - Quality: $quality")
    }
}
