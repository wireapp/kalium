package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.CallManager

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
