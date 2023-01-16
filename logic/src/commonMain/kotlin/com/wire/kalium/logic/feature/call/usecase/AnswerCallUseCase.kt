package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for answering a call.
 */
interface AnswerCallUseCase {
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class AnswerCallUseCaseImpl(
    private val callManager: Lazy<CallManager>
) : AnswerCallUseCase {

    /**
     * @param conversationId the id of the conversation.
     */
    override suspend fun invoke(conversationId: ConversationId) = withContext(KaliumDispatcherImpl.default) {
        callManager.value.answerCall(
            conversationId = conversationId
        )
    }
}
