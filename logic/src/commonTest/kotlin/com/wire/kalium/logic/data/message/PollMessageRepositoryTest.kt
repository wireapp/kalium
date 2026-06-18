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

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.message.PollEntity
import com.wire.kalium.persistence.dao.message.PollMessageDAO
import com.wire.kalium.persistence.dao.message.PollOptionEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test

class PollMessageRepositoryTest {

    @Test
    fun givenVoteForUnknownPoll_whenRecordVote_thenIgnoreIt() = runTest {
        val (arrangement, repository) = Arrangement()
            .withPoll(null)
            .arrange()

        repository.recordVote(
            conversationId = TestConversation.ID,
            pollMessageId = POLL_MESSAGE_ID,
            voterId = TestUser.OTHER_USER_ID,
            selectedOptionIds = listOf(OPTION_ID),
            date = DATE
        ).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.pollMessageDAO.upsertVote(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenValidVote_whenRecordVote_thenUpsertVote() = runTest {
        val (arrangement, repository) = Arrangement()
            .withPoll(poll(allowMultipleAnswers = false))
            .arrange()

        repository.recordVote(
            conversationId = TestConversation.ID,
            pollMessageId = POLL_MESSAGE_ID,
            voterId = TestUser.OTHER_USER_ID,
            selectedOptionIds = listOf(OPTION_ID),
            date = DATE
        ).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pollMessageDAO.upsertVote(
                eq(TestConversation.ID.toDao()),
                eq(POLL_MESSAGE_ID),
                eq(TestUser.OTHER_USER_ID.toDao()),
                eq("""["$OPTION_ID"]"""),
                eq(DATE)
            )
        }
    }

    @Test
    fun givenUnknownOption_whenRecordVote_thenIgnoreIt() = runTest {
        val (arrangement, repository) = Arrangement()
            .withPoll(poll(allowMultipleAnswers = true))
            .arrange()

        repository.recordVote(
            conversationId = TestConversation.ID,
            pollMessageId = POLL_MESSAGE_ID,
            voterId = TestUser.OTHER_USER_ID,
            selectedOptionIds = listOf("unknownOptionId"),
            date = DATE
        ).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.pollMessageDAO.upsertVote(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenMultipleOptionsForSingleAnswerPoll_whenRecordVote_thenIgnoreIt() = runTest {
        val (arrangement, repository) = Arrangement()
            .withPoll(poll(allowMultipleAnswers = false))
            .arrange()

        repository.recordVote(
            conversationId = TestConversation.ID,
            pollMessageId = POLL_MESSAGE_ID,
            voterId = TestUser.OTHER_USER_ID,
            selectedOptionIds = listOf(OPTION_ID, SECOND_OPTION_ID),
            date = DATE
        ).shouldSucceed()

        verifySuspend(VerifyMode.not) {
            arrangement.pollMessageDAO.upsertVote(any(), any(), any(), any(), any())
        }
    }

    private class Arrangement {
        val pollMessageDAO = mock<PollMessageDAO>(mode = MockMode.autoUnit)
        private val repository = PollMessageDataSource(pollMessageDAO)

        fun arrange(block: suspend Arrangement.() -> Unit = {}): Pair<Arrangement, PollMessageRepository> {
            runBlocking { block() }
            return this to repository
        }

        suspend fun withPoll(poll: PollEntity?) = apply {
            everySuspend {
                pollMessageDAO.getPoll(any(), any())
            } returns poll
        }
    }

    private companion object {
        const val POLL_MESSAGE_ID = "pollMessageId"
        const val OPTION_ID = "optionId"
        const val SECOND_OPTION_ID = "secondOptionId"
        val DATE = Instant.parse("2026-06-18T12:00:00Z")

        fun poll(allowMultipleAnswers: Boolean) = PollEntity(
            question = "Question?",
            options = listOf(
                PollOptionEntity(id = OPTION_ID, text = "Option"),
                PollOptionEntity(id = SECOND_OPTION_ID, text = "Second option")
            ),
            allowMultipleAnswers = allowMultipleAnswers,
            hideVoterNames = false
        )
    }
}
