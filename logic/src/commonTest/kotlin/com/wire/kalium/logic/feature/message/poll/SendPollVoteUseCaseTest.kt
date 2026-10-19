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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PollMessageRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs

class SendPollVoteUseCaseTest {

    @Test
    fun givenValidVote_whenSending_thenVoteIsRecordedAndSent() = runTest {
        val arrangement = arrangement().withRecordVoteReturning(true).withSendReturning(Either.Right(Unit))

        val result = arrangement.useCase(TestConversation.ID, POLL_ID, listOf(OPTION_A, OPTION_A))

        assertIs<SendPollVoteUseCase.Result.Success>(result)
        verifySuspend {
            arrangement.pollMessageRepository.recordVote(
                TestConversation.ID,
                POLL_ID,
                matching { it.voterId == TestUser.SELF.id && it.selectedOptionIds == listOf(OPTION_A) }
            )
            arrangement.messageSender.sendMessage(
                matching {
                    val content = (it as Message.Signaling).content as MessageContent.PollVote
                    content.referencedMessageId == POLL_ID && content.selectedOptionIds == listOf(OPTION_A)
                },
                any()
            )
        }
    }

    @Test
    fun givenInvalidVote_whenSending_thenNothingIsSent() = runTest {
        val arrangement = arrangement().withRecordVoteReturning(false)

        val result = arrangement.useCase(TestConversation.ID, POLL_ID, listOf("unknown"))

        assertIs<SendPollVoteUseCase.Result.Failure>(result)
        verifySuspend(VerifyMode.not) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenSendingFails_whenSending_thenPreviousVoteIsRestoredAndFailureIsReturned() = runTest {
        val arrangement = arrangement()
            .withPreviousVote(PREVIOUS_VOTE)
            .withRecordVoteReturning(true)
            .withSendReturning(Either.Left(NetworkFailure.NoNetworkConnection(null)))

        val result = arrangement.useCase(TestConversation.ID, POLL_ID, listOf(OPTION_B))

        assertIs<SendPollVoteUseCase.Result.Failure>(result)
        verifySuspend {
            arrangement.pollMessageRepository.revertVote(
                TestConversation.ID,
                POLL_ID,
                matching { it.selectedOptionIds == listOf(OPTION_B) },
                PREVIOUS_VOTE
            )
        }
    }

    private fun TestScope.arrangement() = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher())

    private class Arrangement(dispatcher: KaliumDispatcher) {
        val messageSender = mock<MessageSender>(MockMode.autoUnit)
        val pollMessageRepository = mock<PollMessageRepository>(MockMode.autoUnit)
        val syncManager = mock<SyncManager>(MockMode.autoUnit)

        val useCase = SendPollVoteUseCase(
            messageSender = messageSender,
            pollMessageRepository = pollMessageRepository,
            syncManager = syncManager,
            currentClientIdProvider = CurrentClientIdProvider { Either.Right(TestClient.CLIENT_ID) },
            selfUserId = TestUser.SELF.id,
            dispatcher = dispatcher
        )

        init {
            everySuspend { syncManager.waitUntilLiveOrFailure() } returns Either.Right(Unit)
            everySuspend { pollMessageRepository.getVote(any(), any(), any()) } returns Either.Right(null)
            everySuspend { pollMessageRepository.revertVote(any(), any(), any(), any()) } returns Either.Right(Unit)
        }

        fun withPreviousVote(vote: MessageContent.Poll.Vote) = apply {
            everySuspend { pollMessageRepository.getVote(any(), any(), any()) } returns Either.Right(vote)
        }

        fun withRecordVoteReturning(isValid: Boolean) = apply {
            everySuspend { pollMessageRepository.recordVote(any(), any(), any()) } returns Either.Right(isValid)
        }

        fun withSendReturning(result: Either<NetworkFailure, Unit>) = apply {
            everySuspend { messageSender.sendMessage(any(), any()) } returns result
        }
    }

    private companion object {
        const val POLL_ID = "pollId"
        const val OPTION_A = "optionA"
        const val OPTION_B = "optionB"
        val PREVIOUS_VOTE = MessageContent.Poll.Vote(TestUser.SELF.id, listOf(OPTION_A), Instant.fromEpochMilliseconds(1))
    }
}
