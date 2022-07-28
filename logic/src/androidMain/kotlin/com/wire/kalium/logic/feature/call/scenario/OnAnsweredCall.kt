package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.AnsweredCallHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

//TODO(testing): create unit test
class OnAnsweredCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper
) : AnsweredCallHandler {
    override fun onAnsweredCall(remoteConversationIdString: String, arg: Pointer?) {
        callingLogger.i("[OnAnsweredCall] -> ConversationId: $remoteConversationIdString")
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(remoteConversationIdString)

        scope.launch {
            callRepository.updateCallStatusById(
                conversationIdString = conversationIdWithDomain.toString(),
                status = CallStatus.ANSWERED
            )
        }
    }
}
