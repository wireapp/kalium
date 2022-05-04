package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.UpdateCallStatusById
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.call.CallStatus

//TODO create unit test
class OnAnsweredCall(
    private val updateCallStatusById: UpdateCallStatusById
) : AnsweredCallHandler {
    override fun onAnsweredCall(conversationId: String, arg: Pointer?) {
        callingLogger.i("${CallManagerImpl.TAG} -> answeredCallHandler")
        updateCallStatusById.updateCallStatus(
            conversationId = conversationId,
            status = CallStatus.ANSWERED
        )
    }
}
