package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

class ToggleReactionUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userId: UserId,
    private val slowSyncRepository: SlowSyncRepository,
    private val reactionRepository: ReactionRepository,
    private val messageSender: MessageSender
) {
    suspend operator fun invoke(
        conversationId: ConversationId,
        messageId: String,
        reaction: String
    ): Either<CoreFailure, Unit> {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }
        val date = Clock.System.now().toString()

        return reactionRepository.getSelfUserReactionsForMessage(messageId, conversationId)
            .flatMap { reactions ->
                currentClientIdProvider().map { it to reactions }
            }
            .flatMap {(currentClientId, currentReactions) ->
                if (currentReactions.contains(reaction)) {
                    // Remove reaction
                    removeReaction(
                        currentClientId,
                        conversationId,
                        date,
                        messageId,
                        reaction,
                        currentReactions
                    )
                } else {
                    // Add reaction
                    addReaction(
                        currentClientId,
                        conversationId,
                        date,
                        messageId,
                        currentReactions,
                        reaction
                    )
                }
            }
    }

    private suspend fun addReaction(
        clientId: ClientId,
        conversationId: ConversationId,
        date: String,
        messageId: String,
        currentReactions: UserReactions,
        newReaction: String
    ): Either<CoreFailure, Unit> {

        return reactionRepository
            .persistReaction(messageId, conversationId, userId, date, newReaction).flatMap {
                val regularMessage = Message.Regular(
                    id = uuid4().toString(),
                    content = MessageContent.Reaction(messageId = messageId, emojiSet = currentReactions + newReaction),
                    conversationId = conversationId,
                    date = date,
                    senderUserId = userId,
                    senderClientId = clientId,
                    status = Message.Status.PENDING,
                    editStatus = Message.EditStatus.NotEdited,
                )
                messageSender.sendMessage(regularMessage)
            }
            .flatMapLeft {
                reactionRepository.deleteReaction(messageId, conversationId, userId, newReaction)
            }
    }

    private suspend fun removeReaction(
        clientId: ClientId,
        conversationId: ConversationId,
        date: String,
        messageId: String,
        removedReaction: String,
        currentReactions: UserReactions
    ): Either<CoreFailure, Unit> {
        return reactionRepository.deleteReaction(messageId, conversationId, userId, removedReaction)
            .flatMap {
                val regularMessage = Message.Regular(
                    id = uuid4().toString(),
                    content = MessageContent.Reaction(messageId = messageId, emojiSet = currentReactions - removedReaction),
                    conversationId = conversationId,
                    date = date,
                    senderUserId = userId,
                    senderClientId = clientId,
                    status = Message.Status.PENDING,
                    editStatus = Message.EditStatus.NotEdited,
                )
                messageSender.sendMessage(regularMessage)
            }.flatMapLeft {
                reactionRepository.persistReaction(messageId, conversationId, userId, date, removedReaction)
            }
    }
}
