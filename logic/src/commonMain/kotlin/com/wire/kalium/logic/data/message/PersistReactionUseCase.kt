package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.functional.Either

interface PersistReactionUseCase {
    suspend operator fun invoke(message: Message, reaction: MessageContent.Reaction): Either<CoreFailure, Unit>
}

internal class PersistReactionUseCaseImpl(
    private val reactionRepository: ReactionRepository,
) : PersistReactionUseCase {
    override suspend operator fun invoke(message: Message, reaction: MessageContent.Reaction): Either<CoreFailure, Unit> {
        return reactionRepository.updateReaction(
            reaction.messageId,
            message.conversationId,
            message.senderUserId,
            message.date,
            reaction.emojiSet
        )
    }
}

