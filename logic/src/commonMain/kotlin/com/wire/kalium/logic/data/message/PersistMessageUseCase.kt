package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DelicateKaliumApi
/**
 * Internal UseCase that should be used instead of MessageRepository.persistMessage(Message)
 * It automatically updates ConversationModifiedDate and ConversationNotificationDate if needed
 */
interface PersistMessageUseCase {
    suspend operator fun invoke(message: Message): Either<CoreFailure, Unit>
}

internal class PersistMessageUseCaseImpl(
    private val messageRepository: MessageRepository,
    private val selfUser: UserId
) : PersistMessageUseCase {
    override suspend operator fun invoke(message: Message): Either<CoreFailure, Unit> {
        val isMyMessage = message.senderUserId == selfUser
        @OptIn(DelicateKaliumApi::class)
        return messageRepository
            .persistMessage(
                message,
                updateConversationReadDate = isMyMessage,
                updateConversationModifiedDate = message.content.shouldUpdateConversationOrder(),
                updateConversationNotificationsDate = isMyMessage
            )
    }

    @Suppress("ComplexMethod")
    private fun MessageContent.shouldUpdateConversationOrder(): Boolean =
        when (this) {
            is MessageContent.MemberChange -> true
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
            is MessageContent.Empty -> false
            is MessageContent.Ignored -> false
            is MessageContent.LastRead -> false
            is MessageContent.Cleared -> false
        }
}
