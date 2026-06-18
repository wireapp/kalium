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
package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.content.PollContentQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

interface PollMessageDAO {
    suspend fun getPoll(
        conversationId: QualifiedIDEntity,
        pollMessageId: String
    ): PollEntity?

    suspend fun upsertVote(
        conversationId: QualifiedIDEntity,
        pollMessageId: String,
        voterId: QualifiedIDEntity,
        selectedOptionIdsJson: String,
        date: Instant
    )
}

data class PollEntity(
    val question: String,
    val options: List<PollOptionEntity>,
    val allowMultipleAnswers: Boolean,
    val hideVoterNames: Boolean
)

internal class PollMessageDAOImpl internal constructor(
    private val pollContentQueries: PollContentQueries,
    private val writeDispatcher: WriteDispatcher,
) : PollMessageDAO {
    override suspend fun getPoll(
        conversationId: QualifiedIDEntity,
        pollMessageId: String
    ): PollEntity? {
        val poll = pollContentQueries.getPoll(
            message_id = pollMessageId,
            conversation_id = conversationId
        ).awaitAsOneOrNull() ?: return null

        val options = pollContentQueries.getPollOptions(
            message_id = pollMessageId,
            conversation_id = conversationId
        ).awaitAsList().map {
            PollOptionEntity(id = it.id, text = it.text)
        }

        return PollEntity(
            question = poll.question,
            options = options,
            allowMultipleAnswers = poll.allow_multiple_answers,
            hideVoterNames = poll.hide_voter_names
        )
    }

    override suspend fun upsertVote(
        conversationId: QualifiedIDEntity,
        pollMessageId: String,
        voterId: QualifiedIDEntity,
        selectedOptionIdsJson: String,
        date: Instant
    ) {
        withContext(writeDispatcher.value) {
            pollContentQueries.upsertVote(
                poll_message_id = pollMessageId,
                conversation_id = conversationId,
                voter_user_id = voterId,
                selected_option_ids = selectedOptionIdsJson,
                creation_date = date
            )
        }
    }
}
