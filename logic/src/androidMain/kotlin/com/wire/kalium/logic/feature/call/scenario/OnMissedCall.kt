package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.MissedCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.call.CallStatus

//TODO create unit test
class OnMissedCall(
    private val callRepository: CallRepository
) : MissedCallHandler {
    override fun onMissedCall(conversationId: String, messageTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer?) {
        callingLogger.i("${CallManagerImpl.TAG} -> missedCallHandler")
        callRepository.updateCallStatusById(
            conversationId = conversationId,
            status = CallStatus.MISSED
        )
    }
}
