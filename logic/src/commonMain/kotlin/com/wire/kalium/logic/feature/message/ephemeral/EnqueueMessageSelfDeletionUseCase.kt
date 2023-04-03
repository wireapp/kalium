package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId

/**
 * This use case enqueue the self deletion of a [Message] for a specific conversation id and message id
 */
interface EnqueueMessageSelfDeletionUseCase {
    operator fun invoke(conversationId: ConversationId, messageId: String)
}

internal class EnqueueMessageSelfDeletionUseCaseImpl(
    private val ephemeralMessageDeletionHandler: EphemeralMessageDeletionHandler
) : EnqueueMessageSelfDeletionUseCase {
    override operator fun invoke(conversationId: ConversationId, messageId: String) {
        ephemeralMessageDeletionHandler.startSelfDeletion(
            conversationId = conversationId,
            messageId = messageId
        )
    }
}
