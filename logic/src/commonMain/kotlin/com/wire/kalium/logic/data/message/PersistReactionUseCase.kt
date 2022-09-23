package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.util.DelicateKaliumApi

interface PersistReactionUseCase {
    suspend operator fun invoke(message: Message, reaction: MessageContent.Reaction): Either<CoreFailure, Unit>
}

internal class PersistReactionUseCaseImpl(
    private val reactionRepository: ReactionRepository,
    private val selfUser: UserId
) : PersistReactionUseCase {
    override suspend operator fun invoke(message: Message, reaction: MessageContent.Reaction): Either<CoreFailure, Unit> {

        return when (val emoji = reaction.emoji) {
            null, "" -> reactionRepository.deleteReaction(
                reaction.messageId,
                message.conversationId,
                message.senderUserId
            )

            else -> reactionRepository.persistReaction(
                originalMessageId = reaction.messageId,
                conversationId = message.conversationId,
                senderUserId = message.senderUserId,
                date = message.date,
                emoji = emoji
            )
        }
    }
}
