package com.wire.kalium.logic.sync.incremental

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.notification.WebSocketEvent
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class EventGathererTest {

    @Test
    fun givenWebSocketOpens_whenGathering_thenShouldStartFetchPendingEvents() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
            .withPendingEventsReturning(emptyFlow())
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            verify(arrangement.eventRepository)
                .suspendFunction(arrangement.eventRepository::pendingEvents)
                .wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            verify(arrangement.eventRepository)
                .suspendFunction(arrangement.eventRepository::pendingEvents)
                .wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpensAndDisconnectPolicy_whenGathering_thenShouldStartFetchPendingEvents() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
            .withPendingEventsReturning(emptyFlow())
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            verify(arrangement.eventRepository)
                .suspendFunction(arrangement.eventRepository::pendingEvents)
                .wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            verify(arrangement.eventRepository)
                .suspendFunction(arrangement.eventRepository::pendingEvents)
                .wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenPendingEventAndDisconnectPolicy_whenGathering_thenShouldEmitEvent() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val pendingEvent = TestEvent.newConnection()
        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
            .withPendingEventsReturning(flowOf(Either.Right(pendingEvent)))
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            verify(arrangement.eventRepository)
                .suspendFunction(arrangement.eventRepository::pendingEvents)
                .wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            assertEquals(pendingEvent, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebsocketEventAndDisconnectPolicy_whenGathering_thenShouldCompleteFlow() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
            .withPendingEventsReturning(emptyFlow())
            .withConnectionPolicyReturning(MutableStateFlow(ConnectionPolicy.DISCONNECT_AFTER_PENDING_EVENTS))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())

        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(TestEvent.newConnection()))
        advanceUntilIdle()

        eventGatherer.gatherEvents().test {
            verify(arrangement.eventRepository)
                .suspendFunction(arrangement.eventRepository::pendingEvents)
                .wasNotInvoked()

            awaitComplete()
        }
    }

    @Test
    fun givenWebSocketOpens_whenGathering_thenSyncStatusIsUpdatedToLive() = runTest {
        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
            .withPendingEventsReturning(emptyFlow())
            .withKeepAliveConnectionPolicy()
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            verify(arrangement.syncRepository)
                .function(arrangement.syncRepository::updateSyncState)
                .with(any())
                .wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            advanceUntilIdle()

            verify(arrangement.syncRepository)
                .function(arrangement.syncRepository::updateSyncState)
                .with(matching { statusUpdate -> SyncState.Live == statusUpdate(SyncState.Waiting) })
                .wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnPendingSource_whenGathering_theEventIsEmitted() = runTest {
        val event = TestEvent.memberJoin()

        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
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
        val event = TestEvent.memberJoin()

        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
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
        val event = TestEvent.memberJoin()

        val liveEventsChannel = Channel<WebSocketEvent<Event>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withUpdateSyncStateReturningLive()
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

    private class Arrangement {

        @Mock
        val eventRepository = configure(mock(EventRepository::class)) { stubsUnitByDefault = true }

        @Mock
        val syncRepository = mock(SyncRepository::class)

        val eventGatherer: EventGatherer = EventGathererImpl(eventRepository, syncRepository)

        fun withLiveEventsReturning(either: Either<CoreFailure, Flow<WebSocketEvent<Event>>>) = apply {
            given(eventRepository)
                .suspendFunction(eventRepository::liveEvents)
                .whenInvoked()
                .thenReturn(either)
        }

        fun withPendingEventsReturning(either: Flow<Either<CoreFailure, Event>>) = apply {
            given(eventRepository)
                .suspendFunction(eventRepository::pendingEvents)
                .whenInvoked()
                .thenReturn(either)
        }

        fun withLastEventIdReturning(either: Either<CoreFailure, String>) = apply {
            given(eventRepository)
                .suspendFunction(eventRepository::lastEventId)
                .whenInvoked()
                .thenReturn(either)
        }

        fun withConnectionPolicyReturning(policyStateFlow: StateFlow<ConnectionPolicy>) = apply {
            given(syncRepository)
                .getter(syncRepository::connectionPolicyState)
                .whenInvoked()
                .thenReturn(policyStateFlow)
        }

        fun withKeepAliveConnectionPolicy() = apply {
            given(syncRepository)
                .getter(syncRepository::connectionPolicyState)
                .whenInvoked()
                .thenReturn(MutableStateFlow(ConnectionPolicy.KEEP_ALIVE))
        }

        fun withUpdateSyncStateReturningLive() = apply {
            given(syncRepository)
                .function(syncRepository::updateSyncState)
                .whenInvokedWith(any())
                .thenReturn(SyncState.Live)
        }

        fun arrange() = this to eventGatherer
    }
}
