package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.CallClosedReason.STILL_ONGOING
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.feature.call.CallStatus

//TODO(testing): create unit test
class OnCloseCall(
    private val callRepository: CallRepository
) : CloseCallHandler {
    override fun onClosedCall(reason: Int, conversationId: String, messageTime: Uint32_t, userId: String, clientId: String?, arg: Pointer?) {
        callingLogger.i("OnCloseCall -> call for conversation $conversationId from user $userId closed at $messageTime for reason: $reason")

        val avsReason = CallClosedReason.fromInt(value = reason)
        val callStatus = if (avsReason === STILL_ONGOING) CallStatus.ONGOING else CallStatus.CLOSED

        callRepository.updateCallStatusById(
            conversationId = conversationId.toConversationId().toString(),
            status = callStatus
        )

        callingLogger.i("OnCloseCall -> incoming call status for conversation $conversationId updated to $callStatus..")
    }
}
