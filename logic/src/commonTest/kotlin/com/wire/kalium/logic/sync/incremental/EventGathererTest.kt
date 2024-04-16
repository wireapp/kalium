/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.incremental

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventGathererTest {

    @Test
    fun givenWebSocketOpens_whenGathering_thenShouldStartFetchPendingEvents() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            coVerify {
                arrangement.eventRepository.pendingEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.pendingEvents()
            }.wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpensAndDisconnectPolicy_whenGathering_thenShouldStartFetchPendingEvents() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            coVerify {
                arrangement.eventRepository.pendingEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.pendingEvents()
            }.wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenPendingEventAndDisconnectPolicy_whenGathering_thenShouldEmitEvent() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val pendingEvent = TestEvent.newConnection().wrapInEnvelope()
        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Right(pendingEvent)))
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            coVerify {
                arrangement.eventRepository.pendingEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            assertEquals(pendingEvent, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebsocketThousandsEventsAndKeepAlivePolicy_whenGathering_thenShouldEmitAllEvents() = runTest {
        val repeatValue = 10_000
        val liveEventsChannel = flow {
            emit(WebSocketEvent.Open())
            repeat(repeatValue) { value ->
                emit(WebSocketEvent.BinaryPayloadReceived(TestEvent.newConnection(eventId = "event_$value").wrapInEnvelope()))

            }
        }

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.KEEP_ALIVE))
            .withLiveEventsReturning(Either.Right(liveEventsChannel))
            .arrange()

        eventGatherer.gatherEvents().test {
            repeat(repeatValue) { value ->
                assertEquals("event_$value", awaitItem().event.id)
            }
        }

    }

    @Test
    fun givenWebsocketEventAndDisconnectPolicy_whenGathering_thenShouldCompleteFlow() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())

        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(TestEvent.newConnection().wrapInEnvelope()))
        advanceUntilIdle()

        eventGatherer.gatherEvents().test {
            awaitComplete()
        }
    }

    @Test
    fun givenWebSocketOpens_whenGathering_thenSyncSourceIsUpdatedToLive() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            advanceUntilIdle()

            eventGatherer.currentSource.test {
                assertEquals(EventSource.LIVE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpensAndCloses_whenGathering_thenSyncSourceShouldBeResetToPending() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            // Open Websocket should trigger fetching pending events
            eventGatherer.currentSource.test {
                liveEventsChannel.send(WebSocketEvent.Open())
                advanceUntilIdle()
                assertEquals(EventSource.PENDING, awaitItem())
                assertEquals(EventSource.LIVE, awaitItem())
                assertEquals(EventSource.PENDING, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpensAndFetchingPendingEventsFail_whenGathering_thenGatheringShouldFailWithSyncException() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val failureCause = NetworkFailure.ServerMiscommunication(IOException())
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Left(failureCause)))
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            advanceUntilIdle()

            val error = awaitError()
            assertIs<KaliumSyncException>(error)
            assertEquals(failureCause, error.coreFailureCause)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketReceivesEventsAndFetchingPendingEventsFail_whenGathering_thenEventsShouldNotBeEmitted() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val failureCause = NetworkFailure.ServerMiscommunication(IOException())
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Left(failureCause)))
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.receiveAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(TestEvent.newConnection().wrapInEnvelope()))

            advanceUntilIdle()

            awaitError()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoEvents_whenGathering_thenSyncSourceDefaultsToPending() = runTest {
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(emptyFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            eventGatherer.currentSource.test {
                assertEquals(EventSource.PENDING, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnPendingSource_whenGathering_theEventIsEmitted() = runTest {
        val event = TestEvent.memberJoin().wrapInEnvelope()

        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Right(event)))
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        eventGatherer.gatherEvents().test {
            assertEquals(event, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnLiveSource_whenGathering_theEventIsEmitted() = runTest {
        val event = TestEvent.memberJoin().wrapInEnvelope()

        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        // Event from the Websocket
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(event))

        eventGatherer.gatherEvents().test {
            assertEquals(event, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInBothOnPendingAndLiveSources_whenGathering_theEventIsEmittedOnce() = runTest {
        val event = TestEvent.memberJoin().wrapInEnvelope()

        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Right(event)))
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        // Same event on websocket
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(event))

        eventGatherer.gatherEvents().test {
            // From pending events
            assertEquals(event, awaitItem())

            // Should not receive another item
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenPendingEventsFailWith404_whenGathering_thenShouldThrowExceptionWithEventNotFoundCause() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val failureCause = NetworkFailure.ServerMiscommunication(
            KaliumException.InvalidRequestError(
                ErrorResponse(
                    code = 404,
                    label = "Event not found",
                    message = "Event not found"
                )
            )
        )
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Left(failureCause)))
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            advanceUntilIdle()

            val error = awaitError()
            assertIs<KaliumSyncException>(error)
            assertIs<CoreFailure.SyncEventOrClientNotFound>(error.coreFailureCause)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {

        @Mock
        val eventRepository = mock(EventRepository::class)

        @Mock
        val incrementalSyncRepository = mock(IncrementalSyncRepository::class)

        val eventGatherer: EventGatherer = EventGathererImpl(eventRepository, incrementalSyncRepository)

        suspend fun withLiveEventsReturning(either: Either<CoreFailure, Flow<WebSocketEvent<EventEnvelope>>>) = apply {
            coEvery {
                eventRepository.liveEvents()
            }.returns(either)
        }

        suspend fun withPendingEventsReturning(either: Flow<Either<CoreFailure, EventEnvelope>>) = apply {
            coEvery {
                eventRepository.pendingEvents()
            }.returns(either)
        }

        suspend fun withLastEventIdReturning(either: Either<StorageFailure, String>) = apply {
            coEvery {
                eventRepository.lastProcessedEventId()
            }.returns(either)
        }

        fun withConnectionPolicyReturning(policyStateFlow: StateFlow<ConnectionPolicy>) = apply {
            every {
                incrementalSyncRepository.connectionPolicyState
            }.returns(policyStateFlow)
        }

        fun withKeepAliveConnectionPolicy() = apply {
            every {
                incrementalSyncRepository.connectionPolicyState
            }.returns(MutableStateFlow(ConnectionPolicy.KEEP_ALIVE))
        }

        fun arrange() = this to eventGatherer
    }
}
