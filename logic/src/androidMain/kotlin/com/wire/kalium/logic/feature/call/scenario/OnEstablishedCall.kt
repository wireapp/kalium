package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.EstablishedCallHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.UpdateCallStatusById
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.call.CallStatus

//TODO create unit test
class OnEstablishedCall(
    private val updateCallStatusById: UpdateCallStatusById
) : EstablishedCallHandler {

    override fun onEstablishedCall(conversationId: String, userId: String, clientId: String, arg: Pointer?) {
        callingLogger.i("${CallManagerImpl.TAG} -> establishedCallHandler")
        updateCallStatusById.updateCallStatus(
            conversationId,
            CallStatus.ESTABLISHED
        )
    }
}
