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
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.message.PollMessageDAO
import com.wire.kalium.persistence.dao.message.PollVoteEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class PollMessageRepositoryTest {

    private val pollMessageDAO = mock<PollMessageDAO>(mode = MockMode.autoUnit)
    private val repository = PollMessageDataSource(pollMessageDAO)

    @Test
    fun givenVote_whenRecording_thenTheDaoDecidesWhetherItIsValid() = runTest {
        everySuspend {
            pollMessageDAO.recordVote(
                TestConversation.ID.toDao(),
                POLL_ID,
                PollVoteEntity(TestUser.OTHER_USER_ID.toDao(), listOf(OPTION_A), DATE)
            )
        } returns false

        repository.recordVote(TestConversation.ID, POLL_ID, VOTE).shouldSucceed {
            assertEquals(false, it)
        }
    }

    @Test
    fun givenStorageError_whenRecordingVote_thenFailureIsReturned() = runTest {
        everySuspend { pollMessageDAO.recordVote(any(), any(), any()) } throws IllegalStateException("disk")

        repository.recordVote(TestConversation.ID, POLL_ID, VOTE).shouldFail {
            assertIs<StorageFailure.Generic>(it)
        }
    }

    private companion object {
        const val POLL_ID = "pollMessageId"
        const val OPTION_A = "optionA"
        val DATE = Instant.parse("2026-06-18T12:00:00Z")
        val VOTE = MessageContent.Poll.Vote(TestUser.OTHER_USER_ID, listOf(OPTION_A), DATE)
    }
}
