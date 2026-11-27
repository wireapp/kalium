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

package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class JoinMeetingViaCodeUseCaseTest {
    @Test
    fun givenServerErrors_whenJoining_thenReturnSpecificFailures() = runTest {
        val cases = listOf(
            Triple(403, "invalid-meeting-password", JoinMeetingViaCodeUseCase.Result.InvalidPassword),
            Triple(404, "meeting-not-found", JoinMeetingViaCodeUseCase.Result.MeetingNotFound),
            Triple(403, "id-feature-error", JoinMeetingViaCodeUseCase.Result.LinkUnavailable),
            Triple(403, "access-denied", JoinMeetingViaCodeUseCase.Result.Failure),
            Triple(400, "invalid-meeting-password", JoinMeetingViaCodeUseCase.Result.Failure),
        )
        for ((code, label, expected) in cases) {
            val failure = NetworkFailure.ServerMiscommunication(
                kaliumException = KaliumException.InvalidRequestError(GenericAPIErrorResponse(code, "error", label))
            )
            val transactions = CryptoTransactionProviderArrangementImpl()
            val repository: MeetingRepository = mock()
            val result: Either<CoreFailure, Meeting> = Either.Left(failure)
            transactions.withTransactionReturning(result)
            everySuspend { repository.joinViaCode("domain", "key", "code", "password", transactions.transactionContext) } returns result
            val useCase = JoinMeetingViaCodeUseCaseImpl(repository, transactions.cryptoTransactionProvider)

            assertEquals(expected, useCase("domain", "key", "code", "password"))
        }
    }

    @Test
    fun givenSuccessfulJoin_whenInvoked_thenForwardLinkAndOptionalPasswordAndReturnSuccess() = runTest {
        for (password in listOf(null, "p a&ss?")) {
            val transactions = CryptoTransactionProviderArrangementImpl()
            val repository: MeetingRepository = mock()
            val meeting = Meeting(
                meetingId = MeetingId("meeting", "domain"),
                conversationId = ConversationId("conversation", "domain"),
                creatorId = UserId("creator", "domain"),
                title = "Meeting",
                startTime = Instant.parse("2026-11-20T13:30:00Z"),
                endTime = Instant.parse("2026-11-20T14:30:00Z"),
                recurrence = null,
                tzid = "Europe/Warsaw",
            )
            val result: Either<CoreFailure, Meeting> = Either.Right(meeting)
            transactions.withTransactionReturning(result)
            everySuspend { repository.joinViaCode("domain", "key", "code", password, transactions.transactionContext) } returns result
            val useCase = JoinMeetingViaCodeUseCaseImpl(repository, transactions.cryptoTransactionProvider)

            assertEquals(JoinMeetingViaCodeUseCase.Result.Success, useCase("domain", "key", "code", password))
            verifySuspend { repository.joinViaCode("domain", "key", "code", password, transactions.transactionContext) }
        }
    }

    @Test
    fun givenPasswordQueryParsingError_whenJoining_thenReturnInvalidPassword() = runTest {
        val transactions = CryptoTransactionProviderArrangementImpl()
        val repository: MeetingRepository = mock()
        val failure = NetworkFailure.ServerMiscommunication(
            kaliumException = KaliumException.InvalidRequestError(
                errorResponse = GenericAPIErrorResponse(
                    code = 400,
                    message = "Error parsing query parameter password failed: Invalid password",
                    label = "bad-request",
                )
            )
        )
        val result: Either<CoreFailure, Meeting> = Either.Left(failure)
        transactions.withTransactionReturning(result)
        everySuspend {
            repository.joinViaCode("domain", "key", "code", "password", transactions.transactionContext)
        } returns result
        val useCase = JoinMeetingViaCodeUseCaseImpl(repository, transactions.cryptoTransactionProvider)

        assertEquals(JoinMeetingViaCodeUseCase.Result.InvalidPassword, useCase("domain", "key", "code", "password"))
    }
}
