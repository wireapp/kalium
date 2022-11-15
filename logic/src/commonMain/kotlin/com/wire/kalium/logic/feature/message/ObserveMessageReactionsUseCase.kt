package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.reaction.MessageReaction
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe the reactions on a message
 *
 * @param reactionRepository ReactionRepository for observing the selected message reactions.
 *
 * @return Flow<List<MessageReaction>> - Flow of MessageReactions List that should be shown to the user.
 * That Flow emits everytime a reaction on the message is added/removed.
 */
interface ObserveMessageReactionsUseCase {
    suspend operator fun invoke(conversationId: ConversationId, messageId: String): Flow<List<MessageReaction>>
}

internal class ObserveMessageReactionsUseCaseImpl(
    private val reactionRepository: ReactionRepository
) : ObserveMessageReactionsUseCase {

    override suspend fun invoke(conversationId: ConversationId, messageId: String): Flow<List<MessageReaction>> =
        reactionRepository.observeMessageReactions(
            conversationId = conversationId,
            messageId = messageId
        )
}
