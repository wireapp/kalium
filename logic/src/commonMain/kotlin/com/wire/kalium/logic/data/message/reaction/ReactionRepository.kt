package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReactionRepository {
    suspend fun persistReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String,
        emoji: String
    ): Either<StorageFailure, Unit>

    suspend fun deleteReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        emoji: String
    ): Either<StorageFailure, Unit>

    suspend fun getSelfUserReactionsForMessage(
        originalMessageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, UserReactions>

    suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String,
        userReactions: UserReactions
    ): Either<StorageFailure, Unit>

    suspend fun observeMessageReactions(
        conversationId: ConversationId,
        messageId: String
    ): Flow<List<MessageReaction>>
}

class ReactionRepositoryImpl(
    private val selfUserId: UserId,
    private val reactionsDAO: ReactionDAO,
    private val reactionsMapper: ReactionsMapper = MapperProvider.reactionsMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ReactionRepository {

    override suspend fun persistReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String,
        emoji: String
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        reactionsDAO.insertReaction(
            originalMessageId = originalMessageId,
            conversationId = conversationId.toDao(),
            senderUserId = senderUserId.toDao(),
            date = date,
            emoji = emoji
        )
    }

    override suspend fun deleteReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        emoji: String
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        reactionsDAO
            .deleteReaction(
                originalMessageId = originalMessageId,
                conversationId = conversationId.toDao(),
                senderUserId = senderUserId.toDao(),
                emoji = emoji
            )
    }

    override suspend fun getSelfUserReactionsForMessage(
        originalMessageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, UserReactions> = wrapStorageRequest {
        reactionsDAO.getReaction(originalMessageId, conversationId.toDao(), selfUserId.toDao())
    }

    override suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String,
        userReactions: UserReactions
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        reactionsDAO.updateReactions(
            originalMessageId,
            conversationId.toDao(),
            senderUserId.toDao(),
            date,
            userReactions
        )
    }

    override suspend fun observeMessageReactions(
        conversationId: ConversationId,
        messageId: String
    ): Flow<List<MessageReaction>> =
        reactionsDAO.observeMessageReactions(
            conversationId = conversationId.toDao(),
            messageId = messageId
        ).map {
            it.map { messageReaction ->
                reactionsMapper.fromEntityToModel(
                    selfUserId = selfUserId,
                    messageReactionEntity = messageReaction
                )
            }
        }
}
