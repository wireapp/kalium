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
package com.wire.kalium.logic.feature.message.poll

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.messaging.sending.MessageSender
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

/**
 * Sends a vote for an existing native poll and records the local user's vote state.
 */
public class SendPollVoteUseCase internal constructor(
    private val messageSender: MessageSender,
    private val messageRepository: MessageRepository,
    private val pollMessageRepository: PollMessageRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId
) {
    public suspend operator fun invoke(
        conversationId: ConversationId,
        pollMessageId: String,
        selectedOptionIds: List<String>
    ): Result = validateVote(conversationId, pollMessageId, selectedOptionIds)?.let(Result::Failure)
        ?: syncManager.waitUntilLiveOrFailure().flatMap {
            currentClientIdProvider().flatMap { currentClientId ->
                val date = Clock.System.now()
                val message = Message.Signaling(
                    id = Uuid.random().toString(),
                    content = MessageContent.PollVote(
                        referencedMessageId = pollMessageId,
                        selectedOptionIds = selectedOptionIds.distinct()
                    ),
                    conversationId = conversationId,
                    date = date,
                    senderUserId = selfUserId,
                    senderClientId = currentClientId,
                    status = Message.Status.Pending,
                    isSelfMessage = true,
                    expirationData = null
                )
                messageSender.sendMessage(message).flatMap {
                    pollMessageRepository.recordVote(
                        conversationId = conversationId,
                        pollMessageId = pollMessageId,
                        voterId = selfUserId,
                        selectedOptionIds = selectedOptionIds,
                        date = date
                    )
                }
            }
        }.fold(Result::Failure) {
            Result.Success
        }

    private suspend fun validateVote(
        conversationId: ConversationId,
        pollMessageId: String,
        selectedOptionIds: List<String>
    ): CoreFailure? {
        val pollMessage = messageRepository.getMessageById(conversationId, pollMessageId).fold(
            { return it },
            { it }
        )
        val poll = pollMessage.content as? MessageContent.Poll ?: return invalid("Poll does not exist")

        val distinctOptionIds = selectedOptionIds.distinct()
        val knownOptionIds = poll.options.map { it.id }.toSet()

        return when {
            distinctOptionIds.any { it !in knownOptionIds } -> invalid("Poll vote references an unknown option")
            !poll.allowMultipleAnswers && distinctOptionIds.size > SINGLE_ANSWER_LIMIT ->
                invalid("Poll accepts only one answer")

            else -> null
        }
    }

    private fun invalid(message: String) = CoreFailure.Unknown(IllegalArgumentException(message))

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(
            val error: CoreFailure
        ) : Result
    }

    private companion object {
        const val SINGLE_ANSWER_LIMIT = 1
    }
}
