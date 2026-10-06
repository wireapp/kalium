/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.PollMessageDAO

internal interface PollMessageRepository {
    suspend fun getVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        voterId: UserId
    ): Either<StorageFailure, MessageContent.Poll.Vote?>

    /**
     * Stores [vote] as the voter's current selection when it is valid for the poll.
     * Returns `false` without storing anything when the poll is unknown or the vote is invalid.
     * A vote older than the voter's stored vote is ignored but still counts as valid.
     */
    suspend fun recordVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        vote: MessageContent.Poll.Vote
    ): Either<StorageFailure, Boolean>

    /** Undoes [failedVote] unless a newer vote has already replaced it. */
    suspend fun revertVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        failedVote: MessageContent.Poll.Vote,
        previousVote: MessageContent.Poll.Vote?
    ): Either<StorageFailure, Unit>
}

internal class PollMessageDataSource internal constructor(
    private val pollMessageDAO: PollMessageDAO
) : PollMessageRepository {

    override suspend fun getVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        voterId: UserId
    ): Either<StorageFailure, MessageContent.Poll.Vote?> = wrapStorageNullableRequest {
        pollMessageDAO.getVote(conversationId.toDao(), pollMessageId, voterId.toDao())?.toModel()
    }

    override suspend fun recordVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        vote: MessageContent.Poll.Vote
    ): Either<StorageFailure, Boolean> = wrapStorageRequest {
        pollMessageDAO.recordVote(conversationId.toDao(), pollMessageId, vote.toEntity())
    }

    override suspend fun revertVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        failedVote: MessageContent.Poll.Vote,
        previousVote: MessageContent.Poll.Vote?
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        pollMessageDAO.revertVote(conversationId.toDao(), pollMessageId, failedVote.toEntity(), previousVote?.toEntity())
    }
}
