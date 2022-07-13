package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess

/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
interface PersistMessageUseCase {
    suspend operator fun invoke(message: Message): Either<CoreFailure, Unit>
}

internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val conversationRepository: ConversationRepository,
    private val userId: QualifiedID,
) : PersistMessageUseCase {

    override suspend operator fun invoke(message: Message): Either<CoreFailure, Unit> {
        return messageRepository.persistMessage(message)
            .onSuccess {
                if (message.content.shouldUpdateConversationOrder())
                    conversationRepository.updateConversationModifiedDate(message.conversationId, message.date)

                if (userId == message.senderUserId)
                    conversationRepository.updateConversationNotificationDate(message.conversationId, message.date)
            }
    }

    private fun MessageContent.shouldUpdateConversationOrder(): Boolean =
        when (this) {
            is MessageContent.MemberChange -> true
            MessageContent.MissedCall -> true
            is MessageContent.Text -> true
            is MessageContent.Calling -> true
            is MessageContent.Asset -> true
            is MessageContent.DeleteMessage -> false
            is MessageContent.TextEdited -> false
            is MessageContent.RestrictedAsset -> true
            is MessageContent.DeleteForMe -> false
            is MessageContent.Unknown -> false
            MessageContent.Empty -> false
            MessageContent.Ignored -> false
        }
}
