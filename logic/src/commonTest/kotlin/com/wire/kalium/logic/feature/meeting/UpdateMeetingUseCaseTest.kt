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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.CreateMeeting
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
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

class UpdateMeetingUseCaseTest {

    @Test
    fun givenUpdateMeetingSucceeds_whenInvoking_thenReturnsSuccessAndRefreshesUsersWithoutMetadata() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withInsertIncompleteUsersSuccess(CREATE_MEETING)
            .withUpdateMeetingReturning(MEETING_ID, CREATE_MEETING, Either.Right(MLSAdditionResult.Empty))
            .arrange()

        val result = useCase(MEETING_ID, CREATE_MEETING)

        assertEquals(UpdateMeetingUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userRepository.insertOrIgnoreIncompleteUsers(CREATE_MEETING.otherParticipants)
            arrangement.meetingRepository.updateMeeting(
                meetingId = MEETING_ID,
                meeting = CREATE_MEETING,
                generateOccurrencesFrom = any(),
                generateOccurrencesUntil = any(),
                transactionContext = arrangement.transactionContext
            )
            arrangement.refreshUsersWithoutMetadata()
        }
        verifySuspend(VerifyMode.not) {
            arrangement.resetMLSConversation(any(), any())
        }
    }

    @Test
    fun givenUpdateMeetingFailsWithResetConversationResolution_whenInvoking_thenResetsConversationAndReturnsSuccess() = runTest {
        val establishMlsFailure = MeetingDataSource.EstablishMLSFailure(
            conversationId = CONVERSATION_ID,
            reason = MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex)
        )
        val (arrangement, useCase) = Arrangement()
            .withInsertIncompleteUsersSuccess(CREATE_MEETING)
            .withUpdateMeetingReturning(MEETING_ID, CREATE_MEETING, Either.Left(establishMlsFailure))
            .withResetConversationReturning(CONVERSATION_ID, ResetMLSConversationResult.Success)
            .arrange()

        val result = useCase(MEETING_ID, CREATE_MEETING)

        assertEquals(UpdateMeetingUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.resetMLSConversation(conversationId = CONVERSATION_ID, transactionContext = arrangement.transactionContext)
            arrangement.refreshUsersWithoutMetadata()
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        internal val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        internal val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        internal val refreshUsersWithoutMetadata = mock<RefreshUsersWithoutMetadataUseCase>(mode = MockMode.autoUnit)
        internal val resetMLSConversation = mock<ResetMLSConversationUseCase>(mode = MockMode.autoUnit)

        internal fun withInsertIncompleteUsersSuccess(meeting: CreateMeeting) = apply {
            everySuspend { userRepository.insertOrIgnoreIncompleteUsers(meeting.otherParticipants) } returns Either.Right(Unit)
        }

        internal fun withUpdateMeetingReturning(
            meetingId: MeetingId,
            meeting: CreateMeeting,
            result: Either<CoreFailure, MLSAdditionResult>
        ) = apply {
            everySuspend {
                meetingRepository.updateMeeting(
                    meetingId = meetingId,
                    meeting = meeting,
                    generateOccurrencesFrom = any(),
                    generateOccurrencesUntil = any(),
                    transactionContext = transactionContext
                )
            } returns result
        }

        internal fun withResetConversationReturning(conversationId: ConversationId, result: ResetMLSConversationResult) = apply {
            everySuspend { resetMLSConversation(conversationId, transactionContext) } returns result
        }

        internal suspend fun arrange(): Pair<Arrangement, UpdateMeetingUseCase> {
            withTransactionReturning(Either.Right(MLSAdditionResult.Empty))
            return this to UpdateMeetingUseCaseImpl(
                meetingRepository = meetingRepository,
                userRepository = userRepository,
                refreshUsersWithoutMetadata = refreshUsersWithoutMetadata,
                resetMLSConversation = resetMLSConversation,
                transactionProvider = cryptoTransactionProvider,
            )
        }
    }

    private companion object {
        val MEETING_ID = MeetingId("meetingId", "domain")
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val CREATE_MEETING = CreateMeeting(
            title = "Meeting",
            startTime = Instant.parse("2026-08-01T12:00:00.000Z"),
            endTime = Instant.parse("2026-08-01T13:00:00.000Z"),
            recurrence = Meeting.Recurrence(
                frequency = Meeting.Recurrence.Frequency.WEEKLY,
                interval = 1L,
                until = Instant.parse("2026-12-01T00:00:00.000Z")
            ),
            otherParticipants = listOf(UserId("participant", "domain"))
        )
    }
}
