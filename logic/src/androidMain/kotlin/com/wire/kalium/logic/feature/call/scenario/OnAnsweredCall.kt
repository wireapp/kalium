package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.call.CallStatus

//TODO(testing): create unit test
class OnAnsweredCall(
    private val callRepository: CallRepository
) : AnsweredCallHandler {
    override fun onAnsweredCall(conversationId: String, arg: Pointer?) {
        callingLogger.i("${CallManagerImpl.TAG} -> answeredCallHandler")
        callRepository.updateCallStatusById(
            conversationId = conversationId,
            status = CallStatus.ANSWERED
        )
    }
}
