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
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.PollMessageDAO
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal interface PollMessageRepository {
    suspend fun recordVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        voterId: UserId,
        selectedOptionIds: List<String>,
        date: Instant
    ): Either<StorageFailure, Unit>
}

internal class PollMessageDataSource internal constructor(
    private val pollMessageDAO: PollMessageDAO
) : PollMessageRepository {
    override suspend fun recordVote(
        conversationId: ConversationId,
        pollMessageId: MessageId,
        voterId: UserId,
        selectedOptionIds: List<String>,
        date: Instant
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        val poll = pollMessageDAO.getPoll(
            conversationId = conversationId.toDao(),
            pollMessageId = pollMessageId
        ) ?: return@wrapStorageRequest

        val distinctOptionIds = selectedOptionIds.distinct()
        val knownOptionIds = poll.options.map { it.id }.toSet()
        val isValidVote = distinctOptionIds.all { it in knownOptionIds } &&
                (poll.allowMultipleAnswers || distinctOptionIds.size <= SINGLE_ANSWER_LIMIT)

        if (!isValidVote) {
            return@wrapStorageRequest
        }

        pollMessageDAO.upsertVote(
            conversationId = conversationId.toDao(),
            pollMessageId = pollMessageId,
            voterId = voterId.toDao(),
            selectedOptionIdsJson = Json.encodeToString(distinctOptionIds),
            date = date
        )
    }

    private companion object {
        const val SINGLE_ANSWER_LIMIT = 1
    }
}
