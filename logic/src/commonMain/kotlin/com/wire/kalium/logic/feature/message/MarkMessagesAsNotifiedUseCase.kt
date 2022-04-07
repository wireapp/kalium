package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either

class MarkMessagesAsNotifiedUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: ConversationId? = null): Either<CoreFailure, Unit> {
        return if (conversationId == null) messageRepository.markAllMessagesAsNotified()
        else messageRepository.markMessagesAsNotifiedByConversation(conversationId)
    }
}
