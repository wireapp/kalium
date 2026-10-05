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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.meeting.Meeting
import com.wire.kalium.logic.data.meeting.MeetingDataSource
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.NotificationEventsManager
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent.meetingUpdateEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
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

class MeetingUpdateEventHandlerTest {

    @Test
    fun givenFeatureNotSupportedFailure_whenHandlingUpdateEvent_thenReturnSuccess() = runTest {
        val event = meetingUpdateEvent()
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, NetworkFailure.FeatureNotSupported.left())
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(0)) { arrangement.notifications.scheduleMeetingNotification(any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.fetchAndPersistMeeting(event.meetingId)
        }
    }

    @Test
    fun givenMeetingNotSupportedFailure_whenHandlingUpdateEvent_thenReturnSuccess() = runTest {
        val event = meetingUpdateEvent()
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, MeetingDataSource.MeetingNotSupportedFailure.left())
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(0)) { arrangement.notifications.scheduleMeetingNotification(any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.fetchAndPersistMeeting(event.meetingId)
        }
    }

    @Test
    fun givenMeetingNotFoundFailure_whenHandlingUpdateEvent_thenReturnSuccess() = runTest {
        val event = meetingUpdateEvent()
        val failure = serverMiscommunicationFailure(code = 404, label = "meeting-not-found")
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, failure.left())
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(0)) { arrangement.notifications.scheduleMeetingNotification(any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.fetchAndPersistMeeting(event.meetingId)
        }
    }

    @Test
    fun givenOtherFailure_whenHandlingUpdateEvent_thenReturnFailure() = runTest {
        val event = meetingUpdateEvent()
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, failure.left())
            .arrange()

        val result = handler.handle(event)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        verifySuspend(VerifyMode.not) { arrangement.notifications.scheduleMeetingNotification(any()) }
    }

    @Test
    fun givenMeeting_whenMeetingFetched_thenEmitUpdateNotification() = runTest {
        val event = meetingUpdateEvent()
        val meeting = meeting(event)
        val updateNotification = with(meeting) {
            LocalNotification.Meeting.Update(event.id, meetingId, conversationId, title, null, event.dateTime, startTime, endTime)
        }
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, meeting.right())
            .withObserveUserReturning(TestUser.OTHER_USER_ID, flowOf(TestUser.OTHER))
            .arrange()

        handler.handle(event)

        verifySuspend { arrangement.notifications.scheduleMeetingNotification(updateNotification) }
    }

    private fun meeting(event: Event.Meeting.Update, creatorId: QualifiedID = TestUser.SELF.id) = Meeting(
        meetingId = event.meetingId,
        conversationId = QualifiedID("conversation", "domain"),
        creatorId = creatorId,
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

        fun withFetchAndPersistMeetingReturning(event: Event.Meeting.Update, result: Either<CoreFailure, Meeting>) = apply {
            everySuspend { meetingRepository.fetchAndPersistMeeting(event.meetingId) } returns result
        }
        fun withObserveUserReturning(userId: QualifiedID, userFlow: Flow<User?>) = apply {
            everySuspend { userRepository.observeUser(userId) } returns userFlow
        }

        fun arrange() = this to MeetingUpdateEventHandlerImpl(
            meetingRepository = meetingRepository,
            userRepository = userRepository,
            notificationEventsManager = notifications,
        )
    }
}
