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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Replaces the self user's vote in a native poll.
 *
 * The vote is stored before it is sent, so results update immediately.
 * If sending fails, the previous vote is restored and [Result.Failure] is returned.
 */
@Suppress("LongParameterList")
public class SendPollVoteUseCase internal constructor(
    private val messageSender: MessageSender,
    private val pollMessageRepository: PollMessageRepository,
    private val syncManager: SyncManager,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) {
    /**
     * @param selectedOptionIds the complete new selection; an empty list retracts the vote
     */
    public suspend operator fun invoke(
        conversationId: ConversationId,
        pollMessageId: String,
        selectedOptionIds: List<String>
    ): Result = withContext(dispatcher.io) {
        syncManager.waitUntilLiveOrFailure()
            .flatMap { currentClientIdProvider() }
            .flatMap { clientId ->
                val vote = MessageContent.Poll.Vote(selfUserId, selectedOptionIds.distinct(), Clock.System.now())
                pollMessageRepository.getVote(conversationId, pollMessageId, selfUserId).flatMap { previousVote ->
                    pollMessageRepository.recordVote(conversationId, pollMessageId, vote).flatMap { isValid ->
                        if (!isValid) {
                            Either.Left(CoreFailure.Unknown(IllegalArgumentException("Invalid vote for poll $pollMessageId")))
                        } else {
                            messageSender.sendMessage(voteMessage(conversationId, pollMessageId, vote, clientId)).onFailure {
                                pollMessageRepository.revertVote(conversationId, pollMessageId, vote, previousVote)
                            }
                        }
                    }
                }
            }
            .fold({ Result.Failure(it) }, { Result.Success })
    }

    private fun voteMessage(
        conversationId: ConversationId,
        pollMessageId: String,
        vote: MessageContent.Poll.Vote,
        clientId: ClientId
    ) = Message.Signaling(
        id = Uuid.random().toString(),
        content = MessageContent.PollVote(referencedMessageId = pollMessageId, selectedOptionIds = vote.selectedOptionIds),
        conversationId = conversationId,
        date = vote.date,
        senderUserId = selfUserId,
        senderClientId = clientId,
        status = Message.Status.Pending,
        isSelfMessage = true,
        expirationData = null
    )

    public sealed interface Result {
        public data object Success : Result
        public data class Failure(val error: CoreFailure) : Result
    }
}
