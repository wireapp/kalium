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
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
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
import kotlin.test.assertIs

class DeleteMeetingForEveryoneUseCaseTest {

    @Test
    fun givenRepositoryDeleteSucceeds_whenInvoking_thenDeletesConversationAndReturnsSuccess() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        val result = useCase(MEETING_ID)

        assertEquals(DeleteMeetingForEveryoneUseCase.Result.Success, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getMeeting(MEETING_ID)
            arrangement.meetingRepository.deleteMeeting(MEETING_ID)
            arrangement.deleteConversation(arrangement.transactionContext, CONVERSATION_ID)
        }
    }

    @Test
    fun givenRepositoryDeleteFails_whenInvoking_thenReturnsFailureAndDoesNotDeleteConversation() = runTest {
        val failure = CoreFailure.Unknown(RuntimeException("delete failed"))
        val (arrangement, useCase) = Arrangement()
            .withDeleteMeetingReturning(Either.Left(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForEveryoneUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.not) {
            arrangement.deleteConversation(any(), any())
        }
    }

    @Test
    fun givenMeetingLookupFails_whenInvoking_thenReturnsFailureAndDoesNotDeleteMeetingOrConversation() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement()
            .withGetMeetingReturning(Either.Left(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForEveryoneUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.not) {
            arrangement.meetingRepository.deleteMeeting(any())
            arrangement.deleteConversation(any(), any())
        }
    }

    @Test
    fun givenConversationDeleteFails_whenInvoking_thenReturnsFailure() = runTest {
        val failure = CoreFailure.Unknown(RuntimeException("conversation delete failed"))
        val (arrangement, useCase) = Arrangement()
            .withDeleteConversationReturning(Either.Left(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForEveryoneUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeeting(MEETING_ID)
            arrangement.deleteConversation(arrangement.transactionContext, CONVERSATION_ID)
        }
    }

    @Test
    fun givenNonMeetingConversation_whenInvoking_thenDeletesMeetingAndPreservesConversation() = runTest {
        listOf(
            Conversation.Type.Group.Regular,
            Conversation.Type.Group.Channel,
            Conversation.Type.OneOnOne,
            Conversation.Type.Self,
            Conversation.Type.ConnectionPending,
        ).forEach { type ->
            val (arrangement, useCase) = Arrangement()
                .withGetConversationReturning(Either.Right(MockConversation.group(CONVERSATION_ID).copy(type = type)))
                .arrange()

            val result = useCase(MEETING_ID)

            assertEquals(DeleteMeetingForEveryoneUseCase.Result.Success, result)
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.meetingRepository.deleteMeeting(MEETING_ID)
                arrangement.conversationRepository.getConversationById(CONVERSATION_ID)
            }
            verifySuspend(VerifyMode.not) {
                arrangement.deleteConversation(any(), any())
            }
        }
    }

    @Test
    fun givenConversationLookupFails_whenInvoking_thenReturnsFailureAndDoesNotDeleteConversation() = runTest {
        val failure = StorageFailure.DataNotFound
        val (arrangement, useCase) = Arrangement()
            .withGetConversationReturning(Either.Left(failure))
            .arrange()

        val result = useCase(MEETING_ID)

        assertEquals(failure, assertIs<DeleteMeetingForEveryoneUseCase.Result.Failure>(result).coreFailure)
        verifySuspend(VerifyMode.not) {
            arrangement.deleteConversation(any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>()
        val deleteConversation = mock<DeleteConversationUseCase>()

        init {
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns Either.Right(
                MockConversation.group(CONVERSATION_ID).copy(type = Conversation.Type.Group.Meeting)
            )
            everySuspend { meetingRepository.getMeeting(MEETING_ID) } returns Either.Right(MEETING)
            everySuspend { meetingRepository.deleteMeeting(MEETING_ID) } returns Either.Right(Unit)
            everySuspend { deleteConversation(transactionContext, CONVERSATION_ID) } returns Either.Right(Unit)
        }

        fun withGetConversationReturning(result: Either<StorageFailure, Conversation>) = apply {
            everySuspend { conversationRepository.getConversationById(CONVERSATION_ID) } returns result
        }

        fun withGetMeetingReturning(result: Either<StorageFailure, Meeting>) = apply {
            everySuspend { meetingRepository.getMeeting(MEETING_ID) } returns result
        }

        fun withDeleteMeetingReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { meetingRepository.deleteMeeting(MEETING_ID) } returns result
        }

        fun withDeleteConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { deleteConversation(transactionContext, CONVERSATION_ID) } returns result
        }

        suspend fun arrange(): Pair<Arrangement, DeleteMeetingForEveryoneUseCase> {
            withTransactionReturning(Either.Right(Unit))
            return this to DeleteMeetingForEveryoneUseCaseImpl(
                meetingRepository = meetingRepository,
                conversationRepository = conversationRepository,
                deleteConversation = deleteConversation,
                transactionProvider = cryptoTransactionProvider,
            )
        }
    }

    private companion object {
        val MEETING_ID = MeetingId("meetingId", "domain")
        val CONVERSATION_ID = ConversationId("conversationId", "domain")
        val MEETING = Meeting(
            meetingId = MEETING_ID,
            conversationId = CONVERSATION_ID,
            creatorId = UserId("creatorId", "domain"),
            title = "Meeting",
            startTime = Instant.parse("2026-06-01T10:00:00Z"),
            endTime = Instant.parse("2026-06-01T11:00:00Z"),
            tzid = "Europe/Berlin",
            recurrence = null,
        )
    }
}
