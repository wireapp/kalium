package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DelicateKaliumApi

/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
interface PersistMessageUseCase {
    suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit>
}

@OptIn(DelicateKaliumApi::class)
internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
        val (updateConversationNotificationsDate, isMyMessage) = userRepository.getSelfUser()?.let {
            message.shouldUpdateConversationNotificationDate(it) to message.isSelfTheSender(it.id)
        } ?: (false to false)

        val modifiedMessage = getExpectsReadConfirmationFromMessage(message)

        @OptIn(DelicateKaliumApi::class)
        return messageRepository
            .persistMessage(
                message = modifiedMessage,
                updateConversationReadDate = isMyMessage,
                updateConversationModifiedDate = message.content.shouldUpdateConversationOrder(),
                updateConversationNotificationsDate
            )
    }

    private fun Message.shouldUpdateConversationNotificationDate(selfUser: SelfUser) =
        when (selfUser.availabilityStatus) {
            UserAvailabilityStatus.AWAY -> true
            UserAvailabilityStatus.BUSY -> this.isSelfTheSender(selfUser.id)
            // todo: OR conversationMutedStatus == MutedConversationStatus.OnlyMentionsAndRepliesAllowed
            else -> this.isSelfTheSender(selfUser.id)
        }

    private fun Message.isSelfTheSender(selfUserId: UserId) = senderUserId == selfUserId

    private suspend fun getExpectsReadConfirmationFromMessage(message: Message.Standalone) =
        if (message is Message.Regular) {
            val expectsReadConfirmation: Boolean = messageRepository
                .getReceiptModeFromGroupConversationByQualifiedID(message.conversationId)
                .fold({
                    message.expectsReadConfirmation
                }, { receiptMode ->
                    receiptMode == Conversation.ReceiptMode.ENABLED
                })

            message.copy(
                expectsReadConfirmation = expectsReadConfirmation
            )
        } else {
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
