package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository

class EnqueueMessageSelfDeletionUseCase(
    private val selfDeletingMessageManager: SelfDeletingMessageManager,
) {
    operator fun invoke(conversationId: ConversationId, messageId: String) {
        selfDeletingMessageManager.enqueuePendingSelfDeletionMessages()
    }
}

