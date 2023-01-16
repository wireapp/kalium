package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will reject a call for the given conversation.
 */
class RejectCallUseCase(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
) {

    suspend operator fun invoke(conversationId: ConversationId) = withContext(KaliumDispatcherImpl.default) {
        callingLogger.d("[RejectCallUseCase] -> Updating call status to REJECTED")
        callRepository.updateCallStatusById(conversationId.toString(), CallStatus.REJECTED)

        callManager.value.rejectCall(conversationId)
    }
}
