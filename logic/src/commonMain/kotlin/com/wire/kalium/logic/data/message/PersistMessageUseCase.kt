package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DelicateKaliumApi
/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
interface PersistMessageUseCase {
    suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit>
}

internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val selfUser: UserId,
    private val conversationRepository: ConversationRepository
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
        val isMyMessage = message.senderUserId == selfUser
        kaliumLogger.d("PMUC -> Starting")
        val modifiedMessage = getExpectsReadConfirmationFromMessage(message)
        kaliumLogger.d("PMUC -> Ended")

        @OptIn(DelicateKaliumApi::class)
        return messageRepository
            .persistMessage(
                message = modifiedMessage,
                updateConversationReadDate = isMyMessage,
                updateConversationModifiedDate = message.content.shouldUpdateConversationOrder(),
                updateConversationNotificationsDate = isMyMessage
            )
    }

    private suspend fun getExpectsReadConfirmationFromMessage(message: Message.Standalone) =
        if (message is Message.Regular) {
            kaliumLogger.d("PMUC -> is Regular")
            val expectsReadConfirmation: Boolean = conversationRepository.detailsById(message.conversationId).fold({
                kaliumLogger.d("PMUC -> failed so returning: ${message.expectsReadConfirmation}")
                message.expectsReadConfirmation
            }, { conversation ->
                kaliumLogger.d("PMUC -> passing")
                if (conversation.type == Conversation.Type.GROUP) {
                    kaliumLogger.d("PMUC -> is Group with value: ${conversation.receiptMode} || ${conversation.receiptMode == Conversation.ReceiptMode.ENABLED}")
                    conversation.receiptMode == Conversation.ReceiptMode.ENABLED
                } else {
                    kaliumLogger.d("PMUC -> not group: ${message.expectsReadConfirmation}")
                    message.expectsReadConfirmation
                }
            })

            message.copy(
                expectsReadConfirmation = expectsReadConfirmation
            )
        } else {
            kaliumLogger.d("PMUC -> not Regular")
            message
        }

    @Suppress("ComplexMethod")
    private fun MessageContent.shouldUpdateConversationOrder(): Boolean =
        when (this) {
            is MessageContent.MemberChange.Added -> true
            is MessageContent.MemberChange.Removed -> false
            is MessageContent.Text -> true
            is MessageContent.Calling -> true
            is MessageContent.Asset -> true
            is MessageContent.Knock -> true
            is MessageContent.DeleteMessage -> false
            is MessageContent.TextEdited -> false
            is MessageContent.RestrictedAsset -> true
            is MessageContent.DeleteForMe -> false
            is MessageContent.Unknown -> false
            is MessageContent.Availability -> false
            is MessageContent.FailedDecryption -> true
            is MessageContent.MissedCall -> true
            is MessageContent.Ignored -> false
            is MessageContent.LastRead -> false
            is MessageContent.Reaction -> false
            is MessageContent.Cleared -> false
            is MessageContent.ConversationRenamed -> true
            is MessageContent.TeamMemberRemoved -> false
            is MessageContent.Receipt -> false
        }
}
