package com.wire.kalium.logic.feature.call.scenario

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.EstablishedCallHandler
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.feature.call.CallStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// TODO(testing): create unit test
class OnEstablishedCall(
    private val callRepository: CallRepository,
    private val scope: CoroutineScope,
    private val qualifiedIdMapper: QualifiedIdMapper
) : EstablishedCallHandler {

    override fun onEstablishedCall(remoteConversationIdString: String, userId: String, clientId: String, arg: Pointer?) {
        callingLogger.i(
            "[OnEstablishedCall] -> ConversationId: ${remoteConversationIdString.obfuscateId()}" +
                    " | UserId: $userId | ClientId: $clientId"
        )
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(remoteConversationIdString)

        scope.launch {
            callRepository.updateCallStatusById(
                conversationIdWithDomain.toString(),
                CallStatus.ESTABLISHED
            )
        }
    }
}
