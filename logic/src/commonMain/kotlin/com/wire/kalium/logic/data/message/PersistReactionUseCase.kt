package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either

interface PersistReactionUseCase {
    suspend operator fun invoke(
        reaction: MessageContent.Reaction,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String
    ): Either<CoreFailure, Unit>
}

internal class PersistReactionUseCaseImpl(
    private val reactionRepository: ReactionRepository,
) : PersistReactionUseCase {
    override suspend operator fun invoke(
        reaction: MessageContent.Reaction,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String
    ): Either<CoreFailure, Unit> {
        return reactionRepository.updateReaction(
            reaction.messageId,
            conversationId,
            senderUserId,
            date,
            reaction.emojiSet
        )
    }
}
