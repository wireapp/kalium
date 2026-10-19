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

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.adapter.QualifiedIDAdapter
import com.wire.kalium.persistence.content.PollContentQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlinx.serialization.Serializable

interface PollMessageDAO {
    /**
     * Stores [vote] as the voter's current selection when the poll exists and the selection is valid for it:
     * every option belongs to the poll, and a single-answer poll has at most one option. Repeated options are removed.
     * Returns `false` without storing anything otherwise. A vote older than the stored one is ignored but still valid.
     */
    suspend fun recordVote(conversationId: QualifiedIDEntity, pollMessageId: String, vote: PollVoteEntity): Boolean

    suspend fun getVote(conversationId: QualifiedIDEntity, pollMessageId: String, voterId: QualifiedIDEntity): PollVoteEntity?

    /**
     * Replaces [failedVote] with [previousVote], or removes it when there was no previous vote.
     * Does nothing when a newer vote has replaced [failedVote] in the meantime.
     */
    suspend fun revertVote(
        conversationId: QualifiedIDEntity,
        pollMessageId: String,
        failedVote: PollVoteEntity,
        previousVote: PollVoteEntity?
    )
}

internal class PollMessageDAOImpl internal constructor(
    private val pollContentQueries: PollContentQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : PollMessageDAO {

    override suspend fun recordVote(conversationId: QualifiedIDEntity, pollMessageId: String, vote: PollVoteEntity): Boolean =
        withContext(writeDispatcher.value) {
            pollContentQueries.transactionWithResult {
                val poll = pollContentQueries.getPoll(message_id = pollMessageId, conversation_id = conversationId)
                    .awaitAsOneOrNull() ?: return@transactionWithResult false
                val optionIds = PollContentJson.decodeOptions(poll.options_json).map { it.id }.toSet()
                val selectedOptionIds = vote.selectedOptionIds.distinct()
                val isValid = optionIds.containsAll(selectedOptionIds) &&
                        (poll.allow_multiple_answers || selectedOptionIds.size <= 1)
                if (isValid) {
                    pollContentQueries.upsertVote(
                        poll_message_id = pollMessageId,
                        conversation_id = conversationId,
                        voter_user_id = vote.voterId,
                        selected_option_ids_json = PollContentJson.encodeOptionIds(selectedOptionIds),
                        creation_date = vote.date
                    )
                }
                isValid
            }
        }

    override suspend fun getVote(
        conversationId: QualifiedIDEntity,
        pollMessageId: String,
        voterId: QualifiedIDEntity
    ): PollVoteEntity? = withContext(readDispatcher.value) {
        pollContentQueries.getVote(poll_message_id = pollMessageId, conversation_id = conversationId, voter_user_id = voterId)
            .awaitAsOneOrNull()
            ?.let { PollVoteEntity(voterId, PollContentJson.decodeOptionIds(it.selected_option_ids_json), it.creation_date) }
    }

    override suspend fun revertVote(
        conversationId: QualifiedIDEntity,
        pollMessageId: String,
        failedVote: PollVoteEntity,
        previousVote: PollVoteEntity?
    ) {
        withContext(writeDispatcher.value) {
            if (previousVote == null) {
                pollContentQueries.deleteVote(
                    poll_message_id = pollMessageId,
                    conversation_id = conversationId,
                    voter_user_id = failedVote.voterId,
                    failed_vote_date = failedVote.date
                )
            } else {
                pollContentQueries.restoreVote(
                    selected_option_ids_json = PollContentJson.encodeOptionIds(previousVote.selectedOptionIds),
                    creation_date = previousVote.date,
                    poll_message_id = pollMessageId,
                    conversation_id = conversationId,
                    voter_user_id = failedVote.voterId,
                    failed_vote_date = failedVote.date
                )
            }
        }
    }
}

/**
 * JSON stored in poll columns and aggregated by MessageDetailsView.
 * Kotlin encodes every user-provided string, so SQL never has to escape option text.
 */
internal object PollContentJson {
    private val json = JsonSerializer()

    @Serializable
    private data class VoteJson(val voterId: String, val selectedOptionIds: List<String>, val date: Long)

    fun encodeOptions(options: List<PollOptionEntity>): String = json.encodeToString(options)

    fun decodeOptions(optionsJson: String): List<PollOptionEntity> = json.decodeFromString(optionsJson)

    fun encodeOptionIds(optionIds: List<String>): String = json.encodeToString(optionIds)

    fun decodeOptionIds(optionIdsJson: String): List<String> = json.decodeFromString(optionIdsJson)

    fun decodeVotes(votesJson: String): List<PollVoteEntity> = json.decodeFromString<List<VoteJson>>(votesJson).map {
        PollVoteEntity(
            voterId = QualifiedIDAdapter.decode(it.voterId),
            selectedOptionIds = it.selectedOptionIds,
            date = Instant.fromEpochMilliseconds(it.date)
        )
    }
}
