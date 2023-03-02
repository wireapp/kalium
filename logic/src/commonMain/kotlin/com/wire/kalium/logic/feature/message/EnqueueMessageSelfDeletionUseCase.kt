package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId

class EnqueueMessageSelfDeletionUseCase(
    private val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler,
) {
    operator fun invoke(conversationId: ConversationId, messageId: String) {
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = conversationId,
            messageId = messageId
        )
    }
}

