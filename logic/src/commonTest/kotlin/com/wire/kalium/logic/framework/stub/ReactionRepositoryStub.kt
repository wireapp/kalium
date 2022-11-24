package com.wire.kalium.logic.framework.stub

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.message.reaction.MessageReaction
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

open class ReactionRepositoryStub : ReactionRepository {

    override suspend fun persistReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String,
        emoji: String
    ): Either<StorageFailure, Unit> = Either.Right(Unit)

    override suspend fun deleteReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        emoji: String
    ): Either<StorageFailure, Unit> = Either.Right(Unit)

    override suspend fun getSelfUserReactionsForMessage(
        originalMessageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, UserReactions> {
        TODO("Not yet implemented")
    }

    override suspend fun observeMessageReactions(
        conversationId: ConversationId,
        messageId: String
    ): Flow<List<MessageReaction>> {
        return flowOf(listOf())
    }

    override suspend fun updateReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        date: String,
        userReactions: UserReactions
    ): Either<StorageFailure, Unit> = Either.Right(Unit)
}
