package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
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
        return messageRepository.persistMessage(
            message = message,
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
            is MessageContent.ClientAction -> false
            is MessageContent.CryptoSessionReset -> false
        }
}
