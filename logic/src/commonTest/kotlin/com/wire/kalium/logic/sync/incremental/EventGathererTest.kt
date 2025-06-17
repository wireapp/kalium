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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventGathererTest.Arrangement.Companion.testScope
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class EventGathererTest {

    @Test
    fun givenWebSocketOpens_whenGathering_thenShouldStartFetchPendingEvents() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning("2022-03-30T15:36:00.000Z")
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpens_whenGatheringFromNewAsyncNotifications_thenShouldSkipFetchPendingEvents() = runTest(testScope) {
        // given
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)
        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning("lastEventId".right())
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(liveEventsChannel.consumeAsFlow().right())
            .withLocalEventsReturning(emptyFlow())
            .withFetchServerTimeReturning("2022-03-30T15:36:00.000Z")
            .arrange(this.backgroundScope)

        eventGatherer.gatherEvents().test {
            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()

            // when
            liveEventsChannel.send(WebSocketEvent.Open(shouldProcessPendingEvents = false))

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()
            coVerify {
                arrangement.serverTimeHandler.computeTimeOffset(any())
            }.wasNotInvoked()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpensAndDisconnectPolicy_whenGathering_thenShouldStartFetchPendingEvents() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withLocalEventsReturning(emptyFlow())
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasInvoked(exactly = once)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenPendingEventAndDisconnectPolicy_whenGathering_thenShouldEmitEvent() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val pendingEvent = TestEvent.newConnection().wrapInEnvelope()
        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Right(pendingEvent)))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withLocalEventsReturning(emptyFlow())
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())

            awaitItem()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLocalThousandsEventsAndKeepAlivePolicy_whenGathering_thenShouldEmitAllEvents() = runTest(testScope) {
        val repeatValue = 10_000
        val webSocketEventFlow = channelFlow<WebSocketEvent<Unit>> {
            send(WebSocketEvent.Open())
            repeat(repeatValue) { value ->
                send(WebSocketEvent.BinaryPayloadReceived(Unit))
            }
            awaitCancellation()
        }

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(webSocketEventFlow))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            repeat(repeatValue) { value ->
                awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpens_whenGathering_thenSyncSourceIsUpdatedToLive() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withLocalEventsReturning(emptyFlow())
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
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
    fun givenEventsWithPendingSource_whenGathering_thenCurrentSourceIsPending() = runTest(testScope) {
        val event = TestEvent.memberJoin()
            .wrapInEnvelope(source = EventSource.PENDING)

        val webSocketEventFlow = channelFlow<List<EventEnvelope>> {
            send(listOf(event))
            awaitCancellation()
        }

        val (_, eventGatherer) = Arrangement()
            .withLocalEventsReturning(webSocketEventFlow)
            .withLiveEventsReturning(Either.Right(emptyFlow()))
            .arrange(this.backgroundScope)

        eventGatherer.gatherEvents().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(EventSource.PENDING, eventGatherer.currentSource.value)
    }

    @Test
    fun givenEventsWithLiveSource_whenGathering_thenCurrentSourceIsLive() = runTest(testScope) {
        val event = TestEvent.memberJoin()
            .wrapInEnvelope(source = EventSource.LIVE)

        val webSocketEventFlow = channelFlow<List<EventEnvelope>> {
            send(listOf(event))
            awaitCancellation()
        }

        val (_, eventGatherer) = Arrangement()
            .withLocalEventsReturning(webSocketEventFlow)
            .withLiveEventsReturning(Either.Right(emptyFlow()))
            .arrange(this.backgroundScope)

        eventGatherer.gatherEvents().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(EventSource.LIVE, eventGatherer.currentSource.value)
    }

    @Test
    fun givenWebSocketOpensAndFetchingPendingEventsFail_whenGathering_thenGatheringShouldFailWithSyncException() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val failureCause = NetworkFailure.ServerMiscommunication(IOException())
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Left(failureCause)))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
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
    fun givenWebSocketReceivesEventsAndFetchingPendingEventsFail_whenGathering_thenEventsShouldNotBeEmitted() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val failureCause = NetworkFailure.ServerMiscommunication(IOException())
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Left(failureCause)))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.receiveAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(Unit))

            advanceUntilIdle()

            awaitError()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoEvents_whenGathering_thenSyncSourceDefaultsToPending() = runTest(testScope) {
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(emptyFlow()))
            .withLocalEventsReturning(emptyFlow())
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            eventGatherer.currentSource.test {
                assertEquals(EventSource.PENDING, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnPendingSource_whenGathering_theEventIsEmitted() = runTest(testScope) {
        val event = TestEvent.memberJoin().wrapInEnvelope()

        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(flowOf(Either.Right(event)))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        eventGatherer.liveEvents().test {
            awaitItem()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnLiveSource_whenGathering_theEventIsEmitted() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        // Event from the Websocket
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(Unit))

        eventGatherer.liveEvents().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenPendingEventsFailWith404_whenGathering_thenShouldThrowExceptionWithEventNotFoundCause() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

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
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open())
            advanceUntilIdle()

            val error = awaitError()
            assertIs<KaliumSyncException>(error)
            assertIs<CoreFailure.SyncEventOrClientNotFound>(error.coreFailureCause)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpens_whenGatheringAndAsyncNotificationsCapable_thenShouldNotFetchPendingEventsNorLastEvent() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withIsClientAsyncNotificationsCapableReturning(true)
            .withPendingEventsReturning(emptyFlow())
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange(this.backgroundScope)

        eventGatherer.liveEvents().test {
            coVerify {
                arrangement.isClientAsyncNotificationsCapableProvider.invoke()
            }.wasInvoked(exactly = once)

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open(shouldProcessPendingEvents = false))

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.liveEvents()
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.eventRepository.fetchEvents()
            }.wasNotInvoked()

            coVerify {
                arrangement.serverTimeHandler.computeTimeOffset(any())
            }.wasNotInvoked()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoLastSavedEventId_whenGettingLiveEventsWithoutAsyncNotifications_thenReturnSyncEventOrClientNotFoundToRecover() =
        runTest(testScope) {
            val (_, eventGatherer) = Arrangement()
                .withIsClientAsyncNotificationsCapableReturning(false)
                .withLastEventIdReturning(Either.Left(StorageFailure.DataNotFound))
                .arrange(this.backgroundScope)

            eventGatherer.liveEvents().test {
                advanceUntilIdle()
                awaitError().let {
                    assertIs<KaliumSyncException>(it).also {
                        assertIs<CoreFailure.SyncEventOrClientNotFound>(it.coreFailureCause)
                    }
                }
            }
        }

    @Test
    fun givenPendingEventThenWebSocketOpen_whenGathering_thenCurrentSourceUpdatesTwice() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<Unit>>(capacity = Channel.UNLIMITED)

        val event = TestEvent.memberJoin()
            .wrapInEnvelope(source = EventSource.PENDING)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning("lastEventId".right())
            .withPendingEventsReturning(flowOf(Either.Right(event)))
            .withLocalEventsReturning(flowOf(listOf(event)))
            .withLiveEventsReturning(liveEventsChannel.consumeAsFlow().right())
            .arrange(this.backgroundScope)

        eventGatherer.gatherEvents().test {
            awaitItem()
            awaitComplete()
        }

        eventGatherer.currentSource.test {
            assertEquals(EventSource.PENDING, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        eventGatherer.liveEvents().test {
            liveEventsChannel.send(WebSocketEvent.Open())
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()

            eventGatherer.currentSource.test {
                assertEquals(EventSource.LIVE, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {

        companion object {
            val testScope = TestKaliumDispatcher.main
        }

        val liveSourceChangeHandler = mock(LiveSourceChangeHandler::class)

        val eventRepository = mock(EventRepository::class)
        val isClientAsyncNotificationsCapableProvider = mock(IsClientAsyncNotificationsCapableProvider::class)
        val serverTimeHandler = mock(ServerTimeHandler::class)

        init {
            runBlocking {
                withIsClientAsyncNotificationsCapableReturning(false)
            }
        }

        suspend fun withIsClientAsyncNotificationsCapableReturning(value: Boolean) = apply {
            coEvery {
                isClientAsyncNotificationsCapableProvider.invoke()
            }.returns(value.right())
        }

        suspend fun withLocalEventsReturning(flow: Flow<List<EventEnvelope>>) = apply {
            coEvery {
                eventRepository.observeEvents()
            }.returns(flow)
        }

        suspend fun withLiveEventsReturning(either: Either<CoreFailure, Flow<WebSocketEvent<Unit>>>) = apply {
            coEvery {
                eventRepository.liveEvents()
            }.returns(either)
        }

        suspend fun withFetchServerTimeReturning(time: String?) = apply {
            coEvery {
                eventRepository.fetchServerTime()
            }.returns(time)
        }

        suspend fun withPendingEventsReturning(either: Flow<Either<CoreFailure, EventEnvelope>>) = apply {
            coEvery {
                eventRepository.fetchEvents()
            }.returns(either)
        }

        suspend fun withLastEventIdReturning(either: Either<StorageFailure, String>) = apply {
            coEvery {
                eventRepository.lastSavedEventId()
            }.returns(either)
        }

        fun arrange(processingScope: CoroutineScope) = this to EventGathererImpl(
            isClientAsyncNotificationsCapableProvider = isClientAsyncNotificationsCapableProvider,
            eventRepository = eventRepository,
            processingScope = processingScope,
            liveSourceChangeHandler = liveSourceChangeHandler,
            serverTimeHandler = serverTimeHandler
        )
    }
}
