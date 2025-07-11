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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class EventGathererTest {

    @Test
    fun givenSomeEvents_whenGathering_thenShouldReceiveEvents() = runTest(testScope) {
        val event = TestEvent.memberJoin().wrapInEnvelope()
        val eventList = listOf(event)
        val localEventsFlow = flowOf(listOf(event))

        val (_, eventGatherer) = Arrangement()
            .withLocalEventsReturning(localEventsFlow)
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(flowOf(WebSocketEvent.Open())))
            .arrange()

        eventGatherer.gatherEvents().test {
            val firstItem = awaitItem()
            assertIs<EventStreamData.NewEvents>(firstItem)
            assertContentEquals(eventList, firstItem.eventList)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoMoreEvents_whenGathering_thenShouldReceiveUpToDate() = runTest(testScope) {
        val webSocketEventFlow = channelFlow<List<EventEnvelope>> {
            send(listOf())
            awaitCancellation()
        }

        val (_, eventGatherer) = Arrangement()
            .withLocalEventsReturning(webSocketEventFlow)
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(flowOf(WebSocketEvent.Open())))
            .arrange()

        eventGatherer.gatherEvents().test {
            val item = awaitItem()
            assertIs<EventStreamData.IsUpToDate>(item)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenNoMoreEvents_whenGathering_thenShouldReceiveUpToDateOnlyAfterWebsocketIsConnected() = runTest(testScope) {
        val localEvents = flowOf<List<EventEnvelope>>(listOf())
        val websocketEvents = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withLocalEventsReturning(localEvents)
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(websocketEvents.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
            expectNoEvents()
            websocketEvents.send(WebSocketEvent.Open())
            assertIs<EventStreamData.IsUpToDate>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
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
//             .arrange()
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
//             .arrange()
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
    fun givenWebSocketOpens_whenGatheringAndAsyncNotificationsCapable_thenShouldNotFetchLastEvent() = runTest(testScope) {
        val liveEventsChannel = Channel<WebSocketEvent<EventVersion>>(capacity = Channel.UNLIMITED)

        val (arrangement, eventGatherer) = Arrangement()
            .withIsClientAsyncNotificationsCapableReturning(true)
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLiveEventsReturning(Either.Right(liveEventsChannel.consumeAsFlow()))
            .arrange()

        eventGatherer.gatherEvents().test {
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
                .withLastEventIdReturning(Either.Right("lastEventId"))
                .withLastEventIdReturning(Either.Left(StorageFailure.DataNotFound))
                .arrange()

            eventGatherer.gatherEvents().test {
                advanceUntilIdle()
                awaitError().let {
                    assertIs<KaliumSyncException>(it).also {
                        assertIs<CoreFailure.SyncEventOrClientNotFound>(it.coreFailureCause)
                    }
                }
            }
        }

    @Test
    fun givenSomeEventsThenNoEvents_whenGathering_thenUpToDateIsEmitted() = runTest {
        val event1 = TestEvent.memberJoin().wrapInEnvelope()

        val localEventsChannel = Channel<List<EventEnvelope>>(capacity = Channel.UNLIMITED)

        val (_, eventGatherer) = Arrangement()
            .withIsClientAsyncNotificationsCapableReturning(false)
            .withLastEventIdReturning(Either.Right("lastEventId"))
            .withLocalEventsReturning(localEventsChannel.consumeAsFlow())
            .withLiveEventsReturning(Either.Right(flowOf(WebSocketEvent.Open())))
            .arrange()

        eventGatherer.gatherEvents().test {
            localEventsChannel.send(listOf(event1))
            assertIs<EventStreamData.NewEvents>(awaitItem())
            localEventsChannel.send(listOf())
            assertIs<EventStreamData.IsUpToDate>(awaitItem())
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

        suspend fun withLastEventIdReturning(either: Either<StorageFailure, String>) = apply {
            coEvery {
                eventRepository.lastSavedEventId()
            }.returns(either)
        }

        fun arrange() = this to EventGathererImpl(
            isClientAsyncNotificationsCapableProvider = isClientAsyncNotificationsCapableProvider,
            eventRepository = eventRepository,
            serverTimeHandler = serverTimeHandler
        )
    }
}
