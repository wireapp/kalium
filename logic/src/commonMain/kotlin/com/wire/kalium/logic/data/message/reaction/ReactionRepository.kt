/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.message.reaction

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
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
    private val reactionsMapper: ReactionsMapper = MapperProvider.reactionsMapper()
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
