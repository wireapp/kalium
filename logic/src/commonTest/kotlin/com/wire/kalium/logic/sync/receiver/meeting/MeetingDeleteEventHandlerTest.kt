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
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.framework.TestEvent.meetingDeleteEvent
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

class MeetingDeleteEventHandlerTest {

    @Test
    fun givenDeleteEvent_whenHandlingEvent_thenMeetingIsDeletedLocally() = runTest {
        val event = meetingDeleteEvent()
        val (arrangement, handler) = Arrangement()
            .withDeleteMeetingLocallyReturning(event, Either.Right(Unit))
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeetingLocally(event.meetingId)
        }
    }

    @Test
    fun givenFeatureNotSupportedFailure_whenHandlingEvent_thenReturnSuccess() = runTest {
        val event = meetingDeleteEvent()
        val (arrangement, handler) = Arrangement()
            .withDeleteMeetingLocallyReturning(event, Either.Left(NetworkFailure.FeatureNotSupported))
            .arrange()

        val result = handler.handle(event)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeetingLocally(event.meetingId)
        }
    }

    @Test
    fun givenRepositoryFailure_whenHandlingEvent_thenFailureIsReturned() = runTest {
        val event = meetingDeleteEvent()
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, handler) = Arrangement()
            .withDeleteMeetingLocallyReturning(event, Either.Left(failure))
            .arrange()

        val result = handler.handle(event)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingRepository.deleteMeetingLocally(event.meetingId)
        }
    }

    private class Arrangement {
        val meetingRepository = mock<MeetingRepository>(mode = MockMode.autoUnit)

        fun withDeleteMeetingLocallyReturning(event: Event.Meeting.Delete, result: Either<CoreFailure, Unit>) = apply {
            everySuspend { meetingRepository.deleteMeetingLocally(event.meetingId) } returns result
        }

        fun arrange() = this to MeetingDeleteEventHandlerImpl(meetingRepository)
    }
}
