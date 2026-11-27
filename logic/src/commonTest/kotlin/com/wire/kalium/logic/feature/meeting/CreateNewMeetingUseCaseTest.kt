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

import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.meeting.CreateNewMeetingResult
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.meeting.CreateMeeting
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
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertEquals

class CreateNewMeetingUseCaseTest {

    private val MEETING_ID = MeetingId("meeting-id", "domain")

    @Test
    fun givenRepositoryCreateSucceeds_whenInvoking_thenReturnsSuccessAndCallRefreshUsersWithoutMetadata() = runTest {
        val createMeeting = CREATE_MEETING
        val createResult = Either.Right(CreateNewMeetingResult(ConversationId("conversation", "domain"), MLSAdditionResult.Empty, meetingId = MEETING_ID))
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, createResult)
            .withTransactionReturning(createResult)
            .arrange()

        val result = useCase(createMeeting)

        assertEquals(CreateNewMeetingUseCase.Result.Success.Complete(ConversationId("conversation", "domain"), meetingId = MEETING_ID), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<CreateNewMeetingResult>("CreateNewMeeting", any())
            arrangement.meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = arrangement.transactionContext)
            arrangement.refreshUsersWithoutMetadata()
        }
    }

    @Test
    fun givenPassword_whenCreatingMeeting_thenPassesPasswordToRepository() = runTest {
        val createMeeting = CREATE_MEETING.copy(password = "secret")
        val createResult = Either.Right(CreateNewMeetingResult(ConversationId("conversation", "domain"), MLSAdditionResult.Empty, meetingId = MEETING_ID))
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, createResult)
            .withTransactionReturning(createResult)
            .arrange()

        useCase(createMeeting)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = arrangement.transactionContext)
        }
    }

    @Test
    fun givenRepositoryCreateFails_whenInvoking_thenReturnsFailure() = runTest {
        val createMeeting = CREATE_MEETING
        val createResult = Either.Left(CoreFailure.MissingClientRegistration)
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, createResult)
            .withTransactionReturning(createResult)
            .arrange()

        val result = useCase(createMeeting)

        assertEquals(CreateNewMeetingUseCase.Result.Failure(CoreFailure.MissingClientRegistration), result)
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
        val establishMLSFailure = MeetingDataSource.EstablishMLSFailure(
            conversationId = ConversationId("conversation", "domain"),
            reason = CoreFailure.MissingClientRegistration,
            meetingId = MEETING_ID,
        )
        val createResult = Either.Left(establishMLSFailure)
        val (arrangement, useCase) = Arrangement()
            .withCreateNewMeetingReturning(createMeeting, createResult)
            .withTransactionReturning(createResult)
            .arrange()

        val result = useCase(createMeeting)

        assertEquals(CreateNewMeetingUseCase.Result.Success.Partial(
            establishMLSFailure.conversationId, meetingId = MEETING_ID, warning = MeetingSaveWarning.ConversationSetupFailed
        ), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<CreateNewMeetingResult>("CreateNewMeeting", any())
            arrangement.meetingRepository.createNewMeeting(meeting = createMeeting, transactionContext = arrangement.transactionContext)
            arrangement.refreshUsersWithoutMetadata()
        }
    }

    @Test
    fun givenPartialParticipantAddition_whenCreating_thenReturnsSavedMeetingWithDetails() = runTest {
        val failedUser = CREATE_MEETING.otherParticipants.single()
        val addition = MLSAdditionResult(emptySet(), setOf(failedUser), emptySet())
        val createResult = Either.Right(CreateNewMeetingResult(ConversationId("conversation", "domain"), addition, MEETING_ID))
        val (_, useCase) = Arrangement()
            .withCreateNewMeetingReturning(CREATE_MEETING, createResult)
            .withTransactionReturning(createResult)
            .arrange()

        val result = useCase(CREATE_MEETING)

        assertEquals(
            CreateNewMeetingUseCase.Result.Success.Partial(
                ConversationId("conversation", "domain"), MEETING_ID,
                MeetingSaveWarning.AddParticipantsFailed(mapOf(failedUser to CoreFailure.MissingKeyPackages(setOf(failedUser))))
            ),
            result
        )
    }

    @Test
    fun givenSavedMeetingSetupFails_whenCreating_thenReturnsWarningWithExistingMeetingId() = runTest {
        val failure = MeetingDataSource.SavedMeetingFailure(
            MEETING_ID, ConversationId("conversation", "domain"), CoreFailure.Unknown(null), MeetingDataSource.SavedMeetingFailure.Stage.CONVERSATION_SETUP
        )
        val createResult = Either.Left(failure)
        val (_, useCase) = Arrangement()
            .withCreateNewMeetingReturning(CREATE_MEETING, createResult)
            .withTransactionReturning(createResult)
            .arrange()

        assertEquals(
            CreateNewMeetingUseCase.Result.Success.Partial(failure.conversationId, MEETING_ID, MeetingSaveWarning.ConversationSetupFailed),
            useCase(CREATE_MEETING)
        )
    }

    @Test
    fun givenTransactionFails_whenInvoking_thenReturnsFailureAndDoesNotCreateMeeting() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withTransactionFailure(CoreFailure.MissingClientRegistration)
            .arrange()

        val result = useCase(CREATE_MEETING)

        assertEquals(CreateNewMeetingUseCase.Result.Failure(CoreFailure.MissingClientRegistration), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<CreateNewMeetingResult>("CreateNewMeeting", any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.meetingRepository.createNewMeeting(meeting = any(), transactionContext = any())
        }
    }

    @Test
    fun givenNetworkOrUnexpectedCreationFailure_whenInvoking_thenPreservesOriginalCause() = runTest {
        val failures = listOf(NetworkFailure.NoNetworkConnection(null), CoreFailure.Unknown(IllegalStateException("create failed")))
        failures.forEach { failure ->
            val repositoryResult = Either.Left(failure)
            val (_, useCase) = Arrangement()
                .withCreateNewMeetingReturning(CREATE_MEETING, repositoryResult)
                .withTransactionReturning(repositoryResult)
                .arrange()

            val result = assertIs<CreateNewMeetingUseCase.Result.Failure>(useCase(CREATE_MEETING))

            assertSame(failure, result.coreFailure)
        }
    }

    inner class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        internal val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        internal val refreshUsersWithoutMetadata = mock<RefreshUsersWithoutMetadataUseCase>(mode = MockMode.autoUnit)

        internal fun withCreateNewMeetingReturning(
            meeting: CreateMeeting,
            result: Either<CoreFailure, CreateNewMeetingResult>
        ) = apply {
            everySuspend { meetingRepository.createNewMeeting(meeting = meeting, transactionContext = transactionContext) } returns result
        }

        internal suspend fun withTransactionReturning(result: Either<CoreFailure, CreateNewMeetingResult>) = apply {
            withTransactionReturning(result.map { it.mlsAdditionResult })
        }

        internal fun withTransactionFailure(failure: CoreFailure) = apply {
            everySuspend {
                cryptoTransactionProvider.transaction<CreateNewMeetingResult>(any(), any())
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
    tzid = "Europe/Berlin",
    recurrence = Meeting.Recurrence(
        frequency = Meeting.Recurrence.Frequency.WEEKLY,
        interval = 1L,
        until = Instant.parse("2026-12-01T00:00:00Z")
    ),
    otherParticipants = listOf(UserId("other", "domain"))
)
