package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

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
    override suspend fun invoke(conversationId: ConversationId) {
        callManager.value.answerCall(
            conversationId = conversationId
        )
    }
}
