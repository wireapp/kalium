package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for ending a call.
 */
class EndCallUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * @param conversationId the id of the conversation for the call should be ended.
     */
    suspend operator fun invoke(conversationId: ConversationId) = withContext(dispatchers.default) {
        persistMissedCallIfNeeded(conversationId)

        callingLogger.d("[EndCallUseCase] -> Updating call status to CLOSED_INTERNALLY")
        callRepository.updateCallStatusById(conversationId.toString(), CallStatus.CLOSED_INTERNALLY)

        callManager.value.endCall(conversationId)
        callRepository.updateIsCameraOnById(conversationId.toString(), false)
    }

    private suspend fun persistMissedCallIfNeeded(conversationId: ConversationId) {
        val call = callRepository.establishedCallsFlow().first().find {
            it.conversationId == conversationId
        }
        if (call == null)
            callRepository.persistMissedCall(conversationId)
    }
}
