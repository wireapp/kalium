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
import com.wire.kalium.network.api.conversation.ConversationMembers
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncManagerTest {

    private val workScheduler = FakeWorkScheduler()

    @Mock
    private val eventRepository: EventRepository = configure(mock(EventRepository::class)) { stubsUnitByDefault = true }

    @Mock
    private val conversationEventReceiver: ConversationEventReceiver =
        configure(mock(ConversationEventReceiver::class)) { stubsUnitByDefault = true }

    private lateinit var syncRepository: SyncRepository
    private lateinit var syncManager: SyncManager

    @BeforeTest
    fun setup() {
        syncRepository = InMemorySyncRepository()
        syncManager = SyncManagerImpl(
            workScheduler,
            eventRepository,
            syncRepository,
            conversationEventReceiver
        )
    }

    @Test
    fun givenSyncStatusWaiting_whenWaitingForSyncToComplete_thenShouldSuspendUntilCompletion() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Waiting }

        //When
        val waitJob = launch {
            syncManager.waitForSyncToComplete()
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
    fun givenSyncStatusIsFailed_whenWaitingForSyncToComplete_thenShouldSuspendUntilCompletion() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Failed(NetworkFailure.NoNetworkConnection(null)) }

        //When
        val waitJob = launch {
            syncManager.waitForSyncToComplete()
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
            syncManager.waitForSyncToComplete()
        }

        //Then
        waitJob.join()
        // Is not suspending
        assertFalse(waitJob.isActive)
    }

    @Test
    fun givenSyncIsWaiting_whenWaitingForSyncToComplete_thenShouldStartSyncCallingTheScheduler() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.Failed(NetworkFailure.NoNetworkConnection(null)) }

        //When
        val waitJob = launch {
            syncManager.waitForSyncToComplete()
        }
        // Do stuff until there's no other job waiting
        advanceUntilIdle()

        // Sync completes
        syncRepository.updateSyncState { SyncState.Live }
        waitJob.join()

        //Then
        assertEquals(1, workScheduler.enqueueImmediateWorkCallCount)
    }

    @Test
    fun givenSlowSyncCompleted_whenCallingOnSlowSyncCompleted_thenShouldStartProcessingEvents() = runTest(TestKaliumDispatcher.default) {
        //Given
        syncRepository.updateSyncState { SyncState.SlowSync }

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(emptyFlow())

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
            assertIs<SyncState.ProcessingPendingEvents>(awaitItem())
            assertIs<SyncState.Live>(awaitItem())

            // A failure happens when live events close the flow
            cancelAndIgnoreRemainingEvents()
        }

        //Then
        verify(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .wasInvoked(exactly = once)

        verify(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
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
            ConversationMembers(listOf(), listOf()),
            "from"
        )

        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(event)))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(emptyFlow()))

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
            ConversationMembers(listOf(), listOf()),
            "from"
        )
        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(event)))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(emptyFlow()))

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
            eventId, TestConversation.ID, TestUser.USER_ID, ConversationMembers(
                listOf(), listOf()
            ), "from"
        )
        val liveEventsChannel = Channel<Event>()
        given(eventRepository)
            .suspendFunction(eventRepository::pendingEvents)
            .whenInvoked()
            .thenReturn(flowOf(Either.Right(event)))

        given(eventRepository)
            .suspendFunction(eventRepository::liveEvents)
            .whenInvoked()
            .thenReturn(Either.Right(liveEventsChannel.receiveAsFlow()))

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        liveEventsChannel.send(event)
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
            eventId, TestConversation.ID, TestUser.USER_ID, ConversationMembers(
                listOf(), listOf()
            ), "from"
        )
        val liveEventsChannel = Channel<Event>()
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

        liveEventsChannel.send(event)

        //Then
        verify(conversationEventReceiver)
            .suspendFunction(conversationEventReceiver::onEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }
}
