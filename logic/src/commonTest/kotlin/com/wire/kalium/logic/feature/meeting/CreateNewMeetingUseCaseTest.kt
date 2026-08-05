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
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.meeting.CreateMeeting
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.publicuser.RefreshUsersWithoutMetadataUseCase
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class CreateNewMeetingUseCaseTest {

    @Test
    fun givenRepositoryCreateSucceeds_whenInvoking_thenReturnsSuccessAndCallRefreshUsersWithoutMetadata() = runTest {
        val createMeeting = CREATE_MEETING
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, Either.Right(MLSAdditionResult.Empty))
            .withTransactionExecutingBlock()
            .arrange()

        val result = useCase(createMeeting)

        assertEquals(CreateNewMeetingUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<MLSAdditionResult>("CreateNewMeeting", any())
            arrangement.meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = arrangement.transactionContext)
            arrangement.refreshUsersWithoutMetadata()
        }
    }

    @Test
    fun givenRepositoryCreateFails_whenInvoking_thenReturnsFailure() = runTest {
        val createMeeting = CREATE_MEETING
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, Either.Left(CoreFailure.MissingClientRegistration))
            .withTransactionExecutingBlock()
            .arrange()

        val result = useCase(createMeeting)

        assertEquals(CreateNewMeetingUseCase.Result.Failure, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = arrangement.transactionContext)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.refreshUsersWithoutMetadata()
        }
    }

    @Test
    fun givenRepositoryCreateReturnsEstablishMlsFailure_whenInvoking_thenReturnsSuccessAndCallRefreshUsersWithoutMetadata() = runTest {
        val createMeeting = CREATE_MEETING
        val establishMLSFailure = MeetingDataSource.EstablishMLSFailure(ConversationId("conversation", "domain"))
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, Either.Left(establishMLSFailure))
            .withTransactionExecutingBlock()
            .arrange()

        val result = useCase(createMeeting)

        assertEquals(CreateNewMeetingUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<MLSAdditionResult>("CreateNewMeeting", any())
            arrangement.meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = arrangement.transactionContext)
            arrangement.refreshUsersWithoutMetadata()
        }
    }

    @Test
    fun givenTransactionFails_whenInvoking_thenReturnsFailureAndDoesNotCreateMeeting() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withTransactionFailure(CoreFailure.MissingClientRegistration)
            .arrange()

        val result = useCase(CREATE_MEETING)

        assertEquals(CreateNewMeetingUseCase.Result.Failure, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<MLSAdditionResult>("CreateNewMeeting", any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.meetingRepository.createNewMeeting(meeting = any(), transactionContext = any())
        }
    }

    inner class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        internal val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        internal val refreshUsersWithoutMetadata = mock<RefreshUsersWithoutMetadataUseCase>(mode = MockMode.autoUnit)

        internal fun withCreateNewMeetingReturning(
            meeting: CreateMeeting,
            result: Either<CoreFailure, MLSAdditionResult>
        ) = apply {
            everySuspend { meetingRepository.createNewMeeting(meeting = meeting, transactionContext = transactionContext) } returns result
        }

        internal suspend fun withTransactionExecutingBlock() = apply {
            withTransactionReturning<MLSAdditionResult>(Either.Right(MLSAdditionResult.Empty))
        }

        internal fun withTransactionFailure(failure: CoreFailure) = apply {
            everySuspend {
                cryptoTransactionProvider.transaction<MLSAdditionResult>(any(), any())
            } returns Either.Left(failure)
        }

        internal fun arrange() = this to CreateNewMeetingUseCaseImpl(
            meetingRepository = meetingRepository,
            refreshUsersWithoutMetadata = refreshUsersWithoutMetadata,
            transactionProvider = cryptoTransactionProvider
        )
    }
}

private val CREATE_MEETING = CreateMeeting(
    title = "Meeting 1",
    startTime = Instant.parse("2026-06-01T10:00:00Z"),
    endTime = Instant.parse("2026-06-01T11:00:00Z"),
    recurrence = Meeting.Recurrence(
        frequency = Meeting.Recurrence.Frequency.WEEKLY,
        interval = 1L,
        until = Instant.parse("2026-12-01T00:00:00Z")
    ),
    otherParticipants = listOf(UserId("other", "domain"))
)
