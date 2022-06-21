package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.api.notification.WebSocketEvent
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    @Mock
    private val workScheduler: UserSessionWorkScheduler = configure(mock(UserSessionWorkScheduler::class)) {
        stubsUnitByDefault = true
    }

    @Mock
    private val eventRepository: EventRepository = configure(mock(EventRepository::class)) { stubsUnitByDefault = true }

    @Mock
    private val conversationEventReceiver: ConversationEventReceiver =
        configure(mock(ConversationEventReceiver::class)) { stubsUnitByDefault = true }

    @Mock
    private val userEventReceiver: UserEventReceiver =
        configure(mock(UserEventReceiver::class)) { stubsUnitByDefault = true }

    private lateinit var syncRepository: SyncRepository
    private lateinit var syncManager: SyncManager

    @BeforeTest
    fun setup() {
        syncRepository = InMemorySyncRepository()
        syncManager = SyncManagerImpl(
            workScheduler,
            eventRepository,
            syncRepository,
            conversationEventReceiver,
            userEventReceiver,
            TestKaliumDispatcher
        )
    }

    @Test
    fun givenSyncStatusWaiting_whenWaitingForSyncToComplete_thenShouldSuspendUntilCompletion() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Waiting }

        //When
        val waitJob = launch {
            syncManager.waitUntilLive()
        }

        //Then
        // Is suspending
        assertTrue(waitJob.isActive)

        // Sync completes
        syncRepository.updateSyncState { SyncState.Live }
        waitJob.join()

        // Stops suspending
        assertFalse(waitJob.isActive)
    }

    @Test
    fun givenSyncStatusWaiting_whenWaitingForSlowSyncToComplete_thenShouldSuspendUntilSlowSyncCompletion() =
        runTest(TestKaliumDispatcher.default) {
            //Given
            syncRepository.updateSyncState { SyncState.Waiting }

            //When
            val waitJob = launch {
                syncManager.waitUntilSlowSyncCompletion()
            }

            //Then
            // Is suspending
            assertTrue(waitJob.isActive)

            // Sync completes
            syncRepository.updateSyncState { SyncState.GatheringPendingEvents }
            waitJob.join()

            // Stops suspending
            assertFalse(waitJob.isActive)
        }

    @Test
    fun givenSyncStatusSlowSync_whenWaitingForSlowSyncToComplete_thenShouldSuspendUntilSlowSyncCompletion() =
        runTest(TestKaliumDispatcher.default) {
            //Given
            syncRepository.updateSyncState { SyncState.SlowSync }

            //When
            val waitJob = launch {
                syncManager.waitUntilSlowSyncCompletion()
            }

            //Then
            // Is suspending
            assertTrue(waitJob.isActive)

            // Sync completes
            syncRepository.updateSyncState { SyncState.GatheringPendingEvents }
            waitJob.join()

            // Stops suspending
            assertFalse(waitJob.isActive)
        }

    @Test
    fun givenSyncStatusIsFailed_whenWaitingForSyncToComplete_thenShouldSuspendUntilCompletion() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Failed(NetworkFailure.NoNetworkConnection(null)) }

        //When
        val waitJob = launch {
            syncManager.waitUntilLive()
        }

        //Then
        // Is suspending
        assertTrue(waitJob.isActive)

        // Sync completes
        syncRepository.updateSyncState { SyncState.Live }
        waitJob.join()

        // Stops suspending
        assertFalse(waitJob.isActive)
    }


    @Test
    fun givenSyncStatusIsLive_whenWaitingForSyncToComplete_thenShouldNotSuspend() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Live }

        //When
        val waitJob = launch {
            syncManager.waitUntilLive()
        }

        //Then
        waitJob.join()
        // Is not suspending
        assertFalse(waitJob.isActive)
    }

    @Test
    fun givenSyncIsFailed_whenWaitingForSyncToComplete_thenShouldStartSyncCallingTheScheduler() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Failed(NetworkFailure.NoNetworkConnection(null)) }

        //When
        val waitJob = launch {
            syncManager.waitUntilLive()
        }
        // Do stuff until there's no other job waiting
        advanceUntilIdle()

        // Sync completes
        syncRepository.updateSyncState { SyncState.Live }
        waitJob.join()

        //Then
        verify(workScheduler)
            .function(workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncStatusIsLive_whenStartingSync_thenShouldCallTheSlowSyncScheduler() = runTest(TestKaliumDispatcher.default) {
        syncRepository.updateSyncState { SyncState.Live }

        syncManager.startSyncIfIdle()

        verify(workScheduler)
            .function(workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncStatusIsGatheringPendingEvents_whenStartingSync_thenShouldCallTheSlowSyncScheduler() = runTest(TestKaliumDispatcher.default) {
        syncRepository.updateSyncState { SyncState.GatheringPendingEvents }

        syncManager.startSyncIfIdle()

        verify(workScheduler)
            .function(workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncStatusIsSlowSync_whenStartingSync_thenShouldCallTheSlowSyncScheduler() = runTest(TestKaliumDispatcher.default) {
        syncRepository.updateSyncState { SyncState.SlowSync }

        syncManager.startSyncIfIdle()

        verify(workScheduler)
            .function(workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncIsFailed_whenStartingSync_thenShouldCallTheSlowSyncScheduler() = runTest(TestKaliumDispatcher.default) {
        syncRepository.updateSyncState { SyncState.Failed(NetworkFailure.NoNetworkConnection(null)) }

        syncManager.startSyncIfIdle()

        verify(workScheduler)
            .function(workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncIsWaiting_whenStartingSync_thenShouldCallTheSlowSyncScheduler() = runTest(TestKaliumDispatcher.default) {
        syncRepository.updateSyncState { SyncState.Waiting }

        syncManager.startSyncIfIdle()

        verify(workScheduler)
            .function(workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompleted_whenCallingOnSlowSyncCompleted_thenShouldStartGatheringEvents() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.SlowSync }

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(emptyFlow())

        given(eventRepository)
            .suspendFunction(eventRepository::lastEventId)
            .whenInvoked()
            .thenReturn(Either.Right("lastProcessedEventId"))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(emptyFlow()))

        syncRepository.syncState.test {
            assertIs<SyncState.SlowSync>(awaitItem())

            //When
            syncManager.onSlowSyncComplete()
            advanceUntilIdle()

            //Then
            assertIs<SyncState.GatheringPendingEvents>(awaitItem())

            // A failure happens when live events close the flow
            cancelAndIgnoreRemainingEvents()
        }

        //Then
        verify(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompletedAndWebSocketOpened_whenCallingOnSlowSyncCompleted_thenShouldFetchPendingEvents() =
        runTest(TestKaliumDispatcher.default) {
            //Given
            syncRepository.updateSyncState { SyncState.SlowSync }

            given(eventRepository)
                .suspendFunction(eventRepository::pendingEvents)
                .whenInvoked()
                .thenReturn(emptyFlow())

            given(eventRepository)
                .suspendFunction(eventRepository::liveEvents)
                .whenInvoked()
                .thenReturn(Either.Right(flowOf(WebSocketEvent.Open())))

            given(eventRepository)
                .suspendFunction(eventRepository::lastEventId)
                .whenInvoked()
                .thenReturn(Either.Right("lastProcessedId"))

            syncRepository.syncState.test {
                assertIs<SyncState.SlowSync>(awaitItem())

                //When
                syncManager.onSlowSyncComplete()
                advanceUntilIdle()

                //Then
                assertIs<SyncState.GatheringPendingEvents>(awaitItem())

                // A failure happens when live events close the flow
                cancelAndIgnoreRemainingEvents()
            }

            //Then
            verify(eventRepository)
                .suspendFunction(eventRepository::pendingEvents)
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenSlowSyncCompletedAndAPendingEvent_whenSyncing_thenTheLastProcessedEventIdIsUpdated() = runTest(TestKaliumDispatcher.default) {
        //Given
        val eventId = "eventId"
        val event = Event.Conversation.MemberJoin(
            eventId,
            TestConversation.ID,
            TestUser.USER_ID,
            listOf(),
            "2022-03-30T15:36:00.000Z"
        )

        given(eventRepository)
            .suspendFunction(eventRepository::lastEventId)
            .whenInvoked()
            .thenReturn(Either.Right("lastProcessedId"))

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(event)))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(flowOf(WebSocketEvent.Open())))

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        //Then
        verify(eventRepository)
            .suspendFunction(eventRepository::updateLastProcessedEventId)
            .with(eq(eventId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompletedAndAPendingEvent_whenSyncing_thenTheEventReceiverIsCalled() = runTest(TestKaliumDispatcher.default) {
        //Given
        val eventId = "eventId"
        val event = Event.Conversation.MemberJoin(
            eventId,
            TestConversation.ID,
            TestUser.USER_ID,
            listOf(),
            "2022-03-30T15:36:00.000Z"
        )
        given(eventRepository)
            .suspendFunction(eventRepository::lastEventId)
            .whenInvoked()
            .thenReturn(Either.Right("lastProcessedId"))

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(event)))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(flowOf(WebSocketEvent.Open())))

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        //Then
        verify(conversationEventReceiver)
            .suspendFunction(conversationEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompletedAndALiveEvent_whenSyncing_thenTheLastProcessedEventIdIsUpdated() = runTest(TestKaliumDispatcher.default) {
        //Given
        val eventId = "eventId"
        val event = Event.Conversation.MemberJoin(
            eventId,
            TestConversation.ID,
            TestUser.USER_ID,
            listOf(),
            "2022-03-30T15:36:00.000Z"
        )
        val liveEventsChannel = Channel<WebSocketEvent<Event>>()

        given(eventRepository)
            .suspendFunction(eventRepository::lastEventId)
            .whenInvoked()
            .thenReturn(Either.Right("lastProcessedId"))

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(emptyFlow())

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(liveEventsChannel.receiveAsFlow()))

        //When
        syncManager.onSlowSyncComplete()

        liveEventsChannel.send(WebSocketEvent.Open())
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(event))

        //Wait processing
        advanceUntilIdle()

        //Then
        verify(eventRepository)
            .suspendFunction(eventRepository::updateLastProcessedEventId)
            .with(eq(eventId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompletedAndALiveEvent_whenSyncing_thenTheEventReceiverIsCalled() = runTest(TestKaliumDispatcher.default) {
        //Given
        val eventId = "eventId"
        val event = Event.Conversation.MemberJoin(
            eventId,
            TestConversation.ID,
            TestUser.USER_ID,
            listOf(),
            "2022-03-30T15:36:00.000Z"
        )
        val liveEventsChannel = Channel<WebSocketEvent<Event>>()

        given(eventRepository)
            .suspendFunction(eventRepository::lastEventId)
            .whenInvoked()
            .thenReturn(Either.Right("lastProcessedId"))

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(emptyFlow())

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .then {
                Either.Right(liveEventsChannel.receiveAsFlow())
            }

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        liveEventsChannel.send(WebSocketEvent.Open())
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(event))

        //Wait processing
        advanceUntilIdle()

        //Then
        verify(conversationEventReceiver)
            .suspendFunction(conversationEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnEventIsInBothOnPendingAndLiveSources_whenSyncing_theEventReceiverIsCalledOnce() = runTest(TestKaliumDispatcher.default) {
        //Given
        val eventId = "eventId"
        val event = Event.Conversation.MemberJoin(
            eventId,
            TestConversation.ID,
            TestUser.USER_ID,
            listOf(),
            "2022-03-30T15:36:00.000Z"
        )
        val liveEventsChannel = Channel<WebSocketEvent<Event>>()

        given(eventRepository)
            .suspendFunction(eventRepository::lastEventId)
            .whenInvoked()
            .thenReturn(Either.Right("lastProcessedId"))

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(event)))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .then {
                Either.Right(liveEventsChannel.receiveAsFlow())
            }

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        liveEventsChannel.send(WebSocketEvent.Open())
        liveEventsChannel.send(WebSocketEvent.BinaryPayloadReceived(event))

        //Wait processing
        advanceUntilIdle()

        //Then
        verify(conversationEventReceiver)
            .suspendFunction(conversationEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }
}
