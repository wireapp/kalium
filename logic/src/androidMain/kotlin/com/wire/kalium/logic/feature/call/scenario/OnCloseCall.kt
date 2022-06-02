package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository

//TODO(testing): create unit test
class OnCloseCall(
    private val callRepository: CallRepository
) : CloseCallHandler {
    override fun onClosedCall(reason: Int, conversationId: String, messageTime: Uint32_t, userId: String, clientId: String, arg: Pointer?) {
        callingLogger.i("OnCloseCall -> call for conversation $conversationId from user $userId closed at $messageTime")
        callRepository.removeCallById(conversationId)
        callingLogger.i("OnCloseCall -> incoming call status for conversation $conversationId updated to CLOSED..")
    }
}
