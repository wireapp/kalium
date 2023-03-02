package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId

class EnqueueMessageSelfDeletionUseCase(
    private val selfDeletingMessageEnqueuer: EphemeralMessageDeletionHandler,
) {
    operator fun invoke(conversationId: ConversationId, messageId: String) {
        selfDeletingMessageEnqueuer.enqueuePendingSelfDeletionMessages()
    }
}

