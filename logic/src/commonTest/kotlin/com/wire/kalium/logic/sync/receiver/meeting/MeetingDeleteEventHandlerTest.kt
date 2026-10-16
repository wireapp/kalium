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
package com.wire.kalium.logic.sync.receiver.meeting

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent.meetingDeleteEvent
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class MeetingDeleteEventHandlerTest {

    @Test
    fun givenDeleteEvent_whenHandlingEvent_thenMeetingIsDeletedLocallyAndNotificationScheduled() = runTest {
        val event = meetingDeleteEvent()
        val meeting = meeting(event)
        val cancelNotification = with(meeting) {
            LocalNotification.Meeting.Cancel(event.id, meetingId, conversationId, title, null, event.dateTime)
        }
        val (arrangement, handler) = Arrangement()
            .withDeleteMeetingLocallyReturning(event, Either.Right(Unit))
            .withGetMeetingReturning(event, Either.Right(meeting(event)))
            .withObserveUserReturning(TestUser.OTHER_USER_ID, flowOf(TestUser.OTHER))
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeetingLocally(event.meetingId)
            arrangement.notifications.scheduleMeetingNotification(cancelNotification)
        }
    }

    @Test
    fun givenRepositoryFailure_whenHandlingEvent_thenFailureIsReturned() = runTest {
        val event = meetingDeleteEvent()
        val failure = StorageFailure.Generic(Exception(""))
        val (arrangement, handler) = Arrangement()
            .withGetMeetingReturning(event, Either.Right(meeting(event)))
            .withDeleteMeetingLocallyReturning(event, Either.Left(failure))
            .arrange()

        val result = handler.handle(event)

        assertSame(failure, assertIs<Either.Left<StorageFailure>>(result).value)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeetingLocally(event.meetingId)
        }
    }

    @Test
    fun givenMeetingLookupFailure_whenHandlingDeleteEvent_thenDeleteLocallyWithoutNotification() = runTest {
        val event = meetingDeleteEvent()
        val (arrangement, handler) = Arrangement()
            .withGetMeetingReturning(event, Either.Left(StorageFailure.DataNotFound))
            .withDeleteMeetingLocallyReturning(event, Either.Right(Unit))
            .arrange()

        assertTrue(handler.handle(event).isRight())

        verifySuspend(VerifyMode.not) { arrangement.notifications.scheduleMeetingNotification(any()) }
        verifySuspend { arrangement.meetingRepository.deleteMeetingLocally(event.meetingId) }
    }

    private fun meeting(event: Event.Meeting.Delete) = Meeting(
        meetingId = event.meetingId,
        conversationId = QualifiedID("conversation", "domain"),
        creatorId = TestUser.SELF.id,
        title = "Planning",
        startTime = event.dateTime + 1.hours,
        endTime = event.dateTime + 2.hours,
        tzid = "UTC",
        recurrence = null,
    )

    private class Arrangement {
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val notifications = mock<NotificationEventsManager>(mode = MockMode.autoUnit)
        val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)

        fun withDeleteMeetingLocallyReturning(event: Event.Meeting.Delete, result: Either<StorageFailure, Unit>) = apply {
            everySuspend { meetingRepository.deleteMeetingLocally(event.meetingId) } returns result
        }

        fun withGetMeetingReturning(event: Event.Meeting.Delete, result: Either<StorageFailure, Meeting>) = apply {
            everySuspend { meetingRepository.getMeeting(event.meetingId) } returns result
        }

        fun withObserveUserReturning(userId: QualifiedID, userFlow: Flow<User?>) = apply {
            everySuspend { userRepository.observeUser(userId) } returns userFlow
        }

        fun arrange() = this to MeetingDeleteEventHandlerImpl(meetingRepository, userRepository, notifications)
    }
}
