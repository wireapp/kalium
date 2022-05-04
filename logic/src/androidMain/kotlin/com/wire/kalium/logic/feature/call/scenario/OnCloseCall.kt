package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.CloseCallHandler
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.UpdateCallStatusById
import com.wire.kalium.logic.feature.call.CallManagerImpl
import com.wire.kalium.logic.feature.call.CallStatus

//TODO create unit test
class OnCloseCall(
    private val updateCallStatusById: UpdateCallStatusById
) : CloseCallHandler {
    override fun onClosedCall(reason: Int, conversationId: String, messageTime: Uint32_t, userId: String, clientId: String, arg: Pointer?) {
        callingLogger.i("${CallManagerImpl.TAG} -> closeCallHandler")
        updateCallStatusById.updateCallStatus(
            conversationId,
            CallStatus.CLOSED
        )
    }
}
