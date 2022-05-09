package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.IncomingCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.call.CallStatus

//TODO create unit test
class OnIncomingCall(
    private val callRepository: CallRepository
) : IncomingCallHandler {
    override fun onIncomingCall(
        conversationId: String,
        messageTime: Uint32_t,
        userId: String,
        clientId: String,
        isVideoCall: Boolean,
        shouldRing: Boolean,
        conversationType: Int,
        arg: Pointer?
    ) {
        callingLogger.i("${CallManagerImpl.TAG} -> incomingCallHandler")
        callRepository.createCall(
            call = Call(
                conversationId = conversationId.toConversationId(),
                status = CallStatus.INCOMING,
                callerId = userId
            )
        )
    }
}
