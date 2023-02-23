package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
class EnqueueMessageSelfDeletionUseCase(
    private val selfDeletingMessageManager: SelfDeletingMessageManager
) {
    operator fun invoke(conversationId: ConversationId, messageId: String) {
        selfDeletingMessageManager.enqueue(conversationId, messageId)
    }
}


