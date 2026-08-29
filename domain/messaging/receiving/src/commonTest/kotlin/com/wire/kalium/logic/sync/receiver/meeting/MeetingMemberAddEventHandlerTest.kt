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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.event.Event
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MeetingMemberAddEventHandlerTest {

    @Test
    fun givenFeatureNotSupportedResult_whenHandlingMemberAddEvent_thenReturnSuccess() = runTest {
        val event = meetingMemberAddEvent()
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, Either.Right(MeetingEventFetchResult.FEATURE_NOT_SUPPORTED))
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.fetchAndPersistMeetingForEvent(event.meetingId)
        }
    }

    @Test
    fun givenMeetingNotSupportedResult_whenHandlingMemberAddEvent_thenReturnSuccess() = runTest {
        val event = meetingMemberAddEvent()
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, Either.Right(MeetingEventFetchResult.MEETING_NOT_SUPPORTED))
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.fetchAndPersistMeetingForEvent(event.meetingId)
        }
    }

    @Test
    fun givenMeetingNotFoundResult_whenHandlingMemberAddEvent_thenReturnSuccess() = runTest {
        val event = meetingMemberAddEvent()
        val (arrangement, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, Either.Right(MeetingEventFetchResult.NOT_FOUND))
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.fetchAndPersistMeetingForEvent(event.meetingId)
        }
    }

    @Test
    fun givenOtherFailure_whenHandlingMemberAddEvent_thenReturnFailure() = runTest {
        val event = meetingMemberAddEvent()
        val failure = CoreFailure.MissingClientRegistration
        val (_, handler) = Arrangement()
            .withFetchAndPersistMeetingReturning(event, Either.Left(failure))
            .arrange()

        val result = handler.handle(event)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
    }

    private class Arrangement {
        val meetingRepository = mock<MeetingEventRepository>(mode = MockMode.autoUnit)

        fun withFetchAndPersistMeetingReturning(
            event: Event.Meeting.MemberAdd,
            result: Either<CoreFailure, MeetingEventFetchResult>
        ) = apply {
            everySuspend { meetingRepository.fetchAndPersistMeetingForEvent(event.meetingId) } returns result
        }

        fun arrange() = this to MeetingMemberAddEventHandlerImpl(meetingRepository)
    }
}
