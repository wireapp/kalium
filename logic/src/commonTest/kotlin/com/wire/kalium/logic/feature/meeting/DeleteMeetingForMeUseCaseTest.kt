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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.LeaveConversationUseCase
import com.wire.kalium.logic.feature.conversation.RemoveMemberFromConversationUseCase
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
import kotlin.test.assertIs

class DeleteMeetingForMeUseCaseTest {

    @Test
    fun givenLeaveAndLocalDeleteSucceed_whenInvoking_thenReturnsSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withLeaveConversationReturning(RemoveMemberFromConversationUseCase.Result.Success)
            .withDeleteMeetingLocallyReturning(Either.Right(Unit))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(DeleteMeetingForMeUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getMeeting(MEETING_ID)
            arrangement.leaveConversation(CONVERSATION_ID)
            arrangement.meetingRepository.deleteMeetingLocally(MEETING_ID)
        }
    }

    @Test
    fun givenLeaveFails_whenInvoking_thenReturnsFailureAndDoesNotDeleteMeetingLocally() = runTest {
        val failure = CoreFailure.Unknown(RuntimeException("leave failed"))
        val (arrangement, useCase) = Arrangement()
            .withLeaveConversationReturning(RemoveMemberFromConversationUseCase.Result.Failure(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForMeUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getMeeting(MEETING_ID)
            arrangement.leaveConversation(CONVERSATION_ID)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.meetingRepository.deleteMeetingLocally(any())
        }
    }

    @Test
    fun givenLocalDeleteFails_whenInvoking_thenReturnsFailure() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement()
            .withLeaveConversationReturning(RemoveMemberFromConversationUseCase.Result.Success)
            .withDeleteMeetingLocallyReturning(Either.Left(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForMeUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getMeeting(MEETING_ID)
            arrangement.leaveConversation(CONVERSATION_ID)
            arrangement.meetingRepository.deleteMeetingLocally(MEETING_ID)
        }
    }

    @Test
    fun givenMeetingLookupFails_whenInvoking_thenReturnsFailureAndDoesNotLeaveConversation() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement()
            .withGetMeetingReturning(Either.Left(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForMeUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getMeeting(MEETING_ID)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.leaveConversation(any())
            arrangement.meetingRepository.deleteMeetingLocally(any())
        }
    }

    private class Arrangement {
        val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        val leaveConversation = mock<LeaveConversationUseCase>()

        init {
            everySuspend { meetingRepository.getMeeting(any()) } returns Either.Right(MEETING)
            everySuspend { leaveConversation(any()) } returns RemoveMemberFromConversationUseCase.Result.Success
            everySuspend { meetingRepository.deleteMeetingLocally(any()) } returns Either.Right(Unit)
        }

        fun withGetMeetingReturning(result: Either<StorageFailure, Meeting>) = apply {
            everySuspend { meetingRepository.getMeeting(MEETING_ID) } returns result
        }

        fun withLeaveConversationReturning(result: RemoveMemberFromConversationUseCase.Result) = apply {
            everySuspend { leaveConversation(CONVERSATION_ID) } returns result
        }

        fun withDeleteMeetingLocallyReturning(result: Either<StorageFailure, Unit>) = apply {
            everySuspend { meetingRepository.deleteMeetingLocally(MEETING_ID) } returns result
        }

        fun arrange() = this to DeleteMeetingForMeUseCaseImpl(meetingRepository, leaveConversation)
    }

    private companion object {
        val MEETING_ID = MeetingId("meetingId", "domain")
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val CREATOR_ID = UserId("creatorId", "domain")
        val MEETING = Meeting(
            meetingId = MEETING_ID,
            conversationId = CONVERSATION_ID,
            creatorId = CREATOR_ID,
            title = "Meeting",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            tzid = "Europe/Berlin",
            recurrence = null,
        )
    }
}
