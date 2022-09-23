package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
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
        senderUserId: UserId
    ): Either<StorageFailure, Unit>
}

class ReactionRepositoryImpl(
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
        senderUserId: UserId
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        reactionsDAO
            .deleteReaction(
                originalMessageId = originalMessageId,
                conversationId = idMapper.toDaoModel(conversationId),
                senderUserId = idMapper.toDaoModel(senderUserId)
            )
    }
}

