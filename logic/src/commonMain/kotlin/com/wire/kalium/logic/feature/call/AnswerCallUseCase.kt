package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId

interface AnswerCallUseCase {
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class AnswerCallUseCaseImpl(
    private val callManager: Lazy<CallManager>
) : AnswerCallUseCase {

    override suspend fun invoke(conversationId: ConversationId) {
        callManager.value.answerCall(
            conversationId = conversationId
        )
    }
}
