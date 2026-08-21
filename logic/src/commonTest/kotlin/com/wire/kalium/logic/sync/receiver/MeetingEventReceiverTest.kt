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
package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.meetingCreateEvent
import com.wire.kalium.logic.framework.TestEvent.meetingDeleteEvent
import com.wire.kalium.logic.sync.receiver.meeting.MeetingCreateEventHandler
import com.wire.kalium.logic.sync.receiver.meeting.MeetingDeleteEventHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
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

class MeetingEventReceiverTest {

    @Test
    fun givenCreateEvent_whenProcessingEvent_thenCreateHandlerIsInvoked() = runTest {
        val event = meetingCreateEvent()
        val (arrangement, eventReceiver) = Arrangement()
            .withMeetingCreateHandlerReturning(event, Either.Right(Unit))
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingCreateEventHandler.handle(event)
        }
    }

    @Test
    fun givenCreateHandlerFails_whenProcessingEvent_thenFailureIsReturned() = runTest {
        val event = meetingCreateEvent()
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, eventReceiver) = Arrangement()
            .withMeetingCreateHandlerReturning(event, Either.Left(failure))
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingCreateEventHandler.handle(event)
        }
    }

    @Test
    fun givenDeleteEvent_whenProcessingEvent_thenDeleteHandlerIsInvoked() = runTest {
        val event = meetingDeleteEvent()
        val (arrangement, eventReceiver) = Arrangement()
            .withMeetingDeleteHandlerReturning(event, Either.Right(Unit))
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertTrue(result.isRight())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingDeleteEventHandler.handle(event)
        }
    }

    @Test
    fun givenDeleteHandlerFails_whenProcessingEvent_thenFailureIsReturned() = runTest {
        val event = meetingDeleteEvent()
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, eventReceiver) = Arrangement()
            .withMeetingDeleteHandlerReturning(event, Either.Left(failure))
            .arrange()

        val result = eventReceiver.onEvent(arrangement.transactionContext, event, TestEvent.liveDeliveryInfo)

        assertSame(failure, assertIs<Either.Left<CoreFailure>>(result).value)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.meetingDeleteEventHandler.handle(event)
        }
    }


    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val meetingCreateEventHandler = mock<MeetingCreateEventHandler>(mode = MockMode.autoUnit)
        val meetingDeleteEventHandler = mock<MeetingDeleteEventHandler>(mode = MockMode.autoUnit)

        fun withMeetingCreateHandlerReturning(event: Event.Meeting.Create, result: Either<CoreFailure, Unit>) = apply {
            everySuspend { meetingCreateEventHandler.handle(event) } returns result
        }

        fun withMeetingDeleteHandlerReturning(event: Event.Meeting.Delete, result: Either<CoreFailure, Unit>) = apply {
            everySuspend { meetingDeleteEventHandler.handle(event) } returns result
        }

        fun arrange() = this to MeetingEventReceiverImpl(
            meetingCreateEventHandler = meetingCreateEventHandler,
            meetingDeleteEventHandler = meetingDeleteEventHandler,
        )
    }
}
