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

package com.wire.kalium.logic.framework.stub

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.UserReactions
import com.wire.kalium.logic.data.message.reaction.MessageReaction
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

open class ReactionRepositoryStub : ReactionRepository {

    override suspend fun persistReaction(
        originalMessageId: String,
        conversationId: ConversationId,
        senderUserId: UserId,
        instant: Instant,
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
        instant: Instant,
        userReactions: UserReactions
    ): Either<StorageFailure, Unit> = Either.Right(Unit)
}
