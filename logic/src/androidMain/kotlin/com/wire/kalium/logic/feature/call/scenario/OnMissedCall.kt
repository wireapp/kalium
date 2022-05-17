package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.MissedCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.CallStatus

//TODO(testing): create unit test
class OnMissedCall(
    private val callRepository: CallRepository
) : MissedCallHandler {
    override fun onMissedCall(conversationId: String, messageTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer?) {
        callingLogger.i("OnMissedCall -> Missed call for conversation: $conversationId at $messageTime from user $userId..")
        callRepository.updateCallStatusById(
            conversationId = conversationId,
            status = CallStatus.MISSED
        )
        callingLogger.i("OnMissedCall-> incoming call status updated to MISSED..")
    }
}
