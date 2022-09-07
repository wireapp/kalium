package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.VideoReceiveStateHandler
import com.wire.kalium.logic.callingLogger

class OnParticipantsVideoStateChanged : VideoReceiveStateHandler {
    override fun onVideoReceiveStateChanged(conversationId: String, userId: String, clientId: String, state: Int, arg: Pointer?) {
        callingLogger.i(
            "[onVideoReceiveStateChanged] - conversationId: $conversationId | userId: $userId clientId: $clientId" +
                    " | state: $state"
        )
    }
}
