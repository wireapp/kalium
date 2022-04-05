package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId

interface AnswerCallUseCase {
    suspend operator fun invoke(conversationId: ConversationId)
}

internal class AnswerCallUseCaseImpl(
    private val callManagerImpl: CallManagerImpl
) : AnswerCallUseCase {

    override suspend fun invoke(conversationId: ConversationId) {
        callManagerImpl.answerCall(
            conversationId = conversationId
        )
    }
}
