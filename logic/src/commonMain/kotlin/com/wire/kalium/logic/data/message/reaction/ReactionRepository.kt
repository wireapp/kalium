package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.reaction.ReactionDAO

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
}

class ReactionRepositoryImpl(
    private val selfUserId: UserId,
    private val reactionsDAO: ReactionDAO,
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
            conversationId = idMapper.toDaoModel(conversationId),
            senderUserId = idMapper.toDaoModel(senderUserId),
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
                conversationId = idMapper.toDaoModel(conversationId),
                senderUserId = idMapper.toDaoModel(senderUserId),
                emoji = emoji
            )
    }

    override suspend fun getSelfUserReactionsForMessage(
        originalMessageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, UserReactions> = wrapStorageRequest {
        reactionsDAO.getReaction(originalMessageId, idMapper.toDaoModel(conversationId), idMapper.toDaoModel(selfUserId))
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
            idMapper.toDaoModel(conversationId),
            idMapper.toDaoModel(senderUserId),
            date,
            userReactions
        )
    }
}

