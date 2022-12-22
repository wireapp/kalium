package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
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
    suspend operator fun invoke(conversationId: ConversationId) = withContext(dispatchers.io) {
        callManager.value.endCall(conversationId)
        callRepository.updateIsCameraOnById(conversationId.toString(), false)
    }
}
