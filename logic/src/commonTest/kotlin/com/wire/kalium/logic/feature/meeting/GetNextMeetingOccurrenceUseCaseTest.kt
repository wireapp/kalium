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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MeetingId
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingOccurrence
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.feature.backup.UserId
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetNextMeetingOccurrenceUseCaseTest {

    @Test
    fun givenRepositoryReturnsOccurrence_whenInvoking_thenReturnsOccurrence() = runTest {
        val (arrangement, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            .withNextMeetingOccurrenceReturning(MEETING.meetingId, FROM, MEETING_OCCURRENCE.right())
            .arrange()

        val result = useCase(MEETING.meetingId, FROM)

        assertEquals(MEETING_OCCURRENCE, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getNextMeetingOccurrence(MEETING.meetingId, FROM)
        }
    }

    @Test
    fun givenRepositoryReturnsNull_whenInvoking_thenReturnsNull() = runTest {
        val (arrangement, useCase) = Arrangement(StandardTestDispatcher(testScheduler).testKaliumDispatcher())
            .withNextMeetingOccurrenceReturning(MEETING.meetingId, FROM, StorageFailure.DataNotFound.left())
            .arrange()

        val result = useCase(MEETING.meetingId, FROM)

        assertEquals(null, result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.getNextMeetingOccurrence(MEETING.meetingId, FROM)
        }
    }

    private class Arrangement(private val dispatcher: KaliumDispatcher) {
        val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)
        fun withNextMeetingOccurrenceReturning(id: MeetingId, from: Instant, result: Either<StorageFailure, MeetingOccurrence>) = apply {
            everySuspend { meetingRepository.getNextMeetingOccurrence(id, from) } returns result
        }
        fun arrange() = this to GetNextMeetingOccurrenceUseCaseImpl(
            dispatcher = dispatcher,
            meetingRepository = meetingRepository,
        )
    }

    private companion object {
        val FROM: Instant = Instant.parse("2026-06-01T10:00:00Z")
        val MEETING: Meeting = Meeting(
            meetingId = MeetingId("meeting1", "domain"),
            conversationId = ConversationId("conversation1", "domain"),
            creatorId = UserId("user1", "domain"),
            title = "Meeting 1",
            startTime = Instant.parse("2026-06-01T10:30:00Z"),
            endTime = Instant.parse("2026-06-01T11:30:00Z"),
            recurrence = null,
        )
        val MEETING_OCCURRENCE: MeetingOccurrence = MeetingOccurrence(
            meeting = MEETING,
            selfRole = MeetingOccurrence.SelfRole.Creator,
            conversationName = "Conversation 1",
            conversationType = MeetingOccurrence.ConversationType.Group,
            occurrenceId = "occurrence1",
            occurrenceStartTime = Instant.parse("2026-06-01T10:30:00Z"),
            occurrenceEndTime = Instant.parse("2026-06-01T11:30:00Z"),
        )
    }
}
