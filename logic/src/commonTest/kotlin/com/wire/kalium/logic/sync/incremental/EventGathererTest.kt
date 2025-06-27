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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.event.EventVersion
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventGathererTest.Arrangement.Companion.testScope
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class EventGathererTest {

    @Test
    fun givenLocalThousandsEventsAndKeepAlivePolicy_whenGathering_thenShouldEmitAllEvents() = runTest(testScope) {
        val repeatValue = 10_000
        val webSocketEventFlow = channelFlow<WebSocketEvent<EventVersion>> {
            send(WebSocketEvent.Open())
            repeat(repeatValue) { value ->
                send(WebSocketEvent.BinaryPayloadReceived(EventVersion.LEGACY))
            }
            awaitCancellation()
        }

        val (arrangement, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(webSocketEventFlow))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.receiveEvents().test {
            repeat(repeatValue) { value ->
                awaitItem()
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

//     @Test
//     fun givenWebSocketOpensAndFetchingPendingEventsFail_whenGathering_thenGatheringShouldFailWithSyncException() = runTest(testScope) {
//         val liveEventsChannel = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)
//
//         val failureCause = NetworkFailure.ServerMiscommunication(IOException())
//         val (_, eventGatherer) = Arrangement()
//             .withLastEventIdReturning(Either.Right("lastEventId"))
//             .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
//             .withFetchServerTimeReturning(null)
//             .arrange(this.backgroundScope)
//
//         eventGatherer.receiveEvents().test {
//             // Open Websocket should trigger fetching pending events
//             liveEventsChannel.send(WebSocketEvent.Open())
//             advanceUntilIdle()
//
//             val error = awaitError()
//             assertIs<KaliumSyncException>(error)
//             assertEquals(failureCause, error.coreFailureCause)
//             cancelAndIgnoreRemainingEvents()
//         }
//     }

//     @Test
//     fun givenWebSocketReceivesEventsAndFetchingPendingEventsFail_whenGathering_thenEventsShouldNotBeEmitted() = runTest(testScope) {
//         val liveEventsChannel = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)
//
//         val failureCause = NetworkFailure.ServerMiscommunication(IOException())
//         val (_, eventGatherer) = Arrangement()
//             .withLastEventIdReturning(Either.Right("lastEventId"))
//             .withLiveEventsReturning(Either.Right(liveEventsChannel.receiveAsFlow()))
//             .withFetchServerTimeReturning(null)
//             .arrange(this.backgroundScope)
//
//         eventGatherer.receiveEvents().test {
//             // Open Websocket should trigger fetching pending events
//             liveEventsChannel.send(WebSocketEvent.Open())
//             liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(EventVersion.LEGACY))
//
//             advanceUntilIdle()
//
//             awaitError()
//             cancelAndIgnoreRemainingEvents()
//         }
//     }

    @Test
    fun givenNoEvents_whenGathering_thenSyncSourceDefaultsToPending() = runTest(testScope) {
        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(emptyFlow()))
            .withLocalEventsReturning(emptyFlow())
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        eventGatherer.receiveEvents().test {
            eventGatherer.currentSource.test {
                assertEquals(EventSource.PENDING, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnPendingSource_whenGathering_theEventIsEmitted() = runTest(testScope) {

        val liveEventsChannel = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        eventGatherer.receiveEvents().test {
            awaitItem()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenAnEventIsInOnLiveSource_whenGathering_theEventIsEmitted() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .withFetchServerTimeReturning(null)
            .arrange(this.backgroundScope)

        // Open Websocket should trigger fetching pending events
        liveEventsChannel.send(WebSocketEvent.Open())
        // Event from the Websocket
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(EventVersion.LEGACY))

        eventGatherer.receiveEvents().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketOpens_whenGatheringAndAsyncNotificationsCapable_thenShouldNotFetchLastEvent() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withIsClientAsyncNotificationsCapableReturning(true)
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange(this.backgroundScope)

        eventGatherer.receiveEvents().test {
            coVerify {
                arrangement.isClientAsyncNotificationsCapableProvider.invoke()
            }.wasInvoked(exactly = once)

            advanceUntilIdle()

            // Open Websocket should trigger fetching pending events
            liveEventsChannel.send(WebSocketEvent.Open(shouldProcessPendingEvents = false))

            advanceUntilIdle()

            coVerify {
                arrangement.eventRepository.liveEvents()
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.eventRepository.lastSavedEventId()
            }.wasNotInvoked()

            coVerify {
                arrangement.serverTimeHandler.computeTimeOffset(any())
            }.wasNotInvoked()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoLastSavedEventId_whenGettingReceiveEventsWithoutAsyncNotifications_thenReturnSyncEventOrClientNotFoundToRecover() =
        runTest(testScope) {
            val (_, eventGatherer) = Arrangement()
                .withIsClientAsyncNotificationsCapableReturning(false)
                .withLastEventIdReturning(Either.Left(StorageFailure.DataNotFound))
                .arrange(this.backgroundScope)

            eventGatherer.receiveEvents().test {
                advanceUntilIdle()
                awaitError().let {
                    assertIs<KaliumSyncException>(it).also {
                        assertIs<CoreFailure.SyncEventOrClientNotFound>(it.coreFailureCause)
                    }
                }
            }
        }

    @Test
    fun givenFirstEventPendingThenLive_whenGathering_thenCurrentSourceUpdates() = runTest {
        val event1 = TestEvent.memberJoin().wrapInEnvelope(source = EventSource.PENDING)
        val event2 = TestEvent.memberJoin().wrapInEnvelope(source = EventSource.LIVE)

        val localEventsChannel = Channel<List<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withIsClientAsyncNotificationsCapableReturning(false)
            .withLocalEventsReturning(localEventsChannel.consumeAsFlow())
            .withLiveEventsReturning(Either.Right(emptyFlow()))
            .arrange(backgroundScope)

        eventGatherer.gatherEvents().test {
            assertEquals(EventSource.PENDING, eventGatherer.currentSource.value)
            localEventsChannel.send(listOf(event1))
            awaitItem()
            assertEquals(EventSource.PENDING, eventGatherer.currentSource.value)
            localEventsChannel.send(listOf(event2))
            awaitItem()
            assertEquals(EventSource.LIVE, eventGatherer.currentSource.value)
            cancelAndIgnoreRemainingEvents()
        }
    }


    private class Arrangement {

        companion object {
            val testScope = TestKaliumDispatcher.main
        }

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

        suspend fun withLiveEventsReturning(either: Either<CoreFailure, Flow<WebSocketEvent<EventVersion>>>) = apply {
            coEvery {
                eventRepository.liveEvents()
            }.returns(either)
        }

        suspend fun withFetchServerTimeReturning(time: String?) = apply {
            coEvery {
                eventRepository.fetchServerTime()
            }.returns(time)
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
            serverTimeHandler = serverTimeHandler
        )
    }
}
