package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

//TODO(testing): create unit test
class OnAnsweredCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope
) : AnsweredCallHandler {
    override fun onAnsweredCall(conversationId: String, arg: Pointer?) {
        callingLogger.i("OnAnsweredCall -> call for conversation $conversationId answered")
        scope.launch {
            callRepository.updateCallStatusById(
                conversationId = conversationId,
                status = CallStatus.ANSWERED
            )
        }
        callingLogger.i("OnAnsweredCall -> incoming call status for conversation $conversationId updated to ANSWERED..")
    }
}
