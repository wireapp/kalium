package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
    private val eventGatherer: EventGatherer = mock(EventGatherer::class)

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
            eventGatherer,
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
    fun givenSyncStatusIsGatheringPendingEvents_whenStartingSync_thenShouldCallTheSlowSyncScheduler() =
        runTest(TestKaliumDispatcher.default) {
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

        given(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .whenInvoked()
            .thenReturn(emptyFlow())

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
        verify(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompletedAndALiveEvent_whenSyncing_thenTheLastProcessedEventIdIsUpdated() = runTest(TestKaliumDispatcher.default) {
        //Given
        val event = TestEvent.memberJoin()

        given(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .whenInvoked()
            .thenReturn(flowOf(event))

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        //Then
        verify(eventRepository)
            .suspendFunction(eventRepository::updateLastProcessedEventId)
            .with(eq(event.id))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSlowSyncCompletedAndAnEventIsReceived_whenSyncing_thenTheEventReceiverIsCalled() = runTest(TestKaliumDispatcher.default) {
        //Given
        val event = TestEvent.memberJoin()

        given(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .whenInvoked()
            .thenReturn(flowOf(event))

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
    fun givenGathererFails_whenSyncing_thenTheStatusIsUpdatedToFailed() = runTest(TestKaliumDispatcher.default) {
        //Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        given(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .whenInvoked()
            .thenThrow(KaliumSyncException("Oopsie", coreFailureCause))

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        assertEquals(SyncState.Failed(coreFailureCause), syncRepository.syncState.first())
    }

    @Test
    fun givenGathererFlowThrows_whenSyncing_thenTheStatusIsUpdatedToFailed() = runTest(TestKaliumDispatcher.default) {
        //Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        given(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .whenInvoked()
            .thenReturn(flow { throw KaliumSyncException("Oopsie", coreFailureCause) })

        //When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        assertEquals(SyncState.Failed(coreFailureCause), syncRepository.syncState.first())
    }

    @Test
    fun givenGathererFails_whenSyncingForTheSecondTime_thenShouldGatherAgain() = runTest(TestKaliumDispatcher.default) {
        //Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        given(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .whenInvoked()
            .thenThrow(KaliumSyncException("Oopsie", coreFailureCause))

        //When
        syncManager.onSlowSyncComplete()
        // Fails
        advanceUntilIdle()
        // Try Again
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        verify(eventGatherer)
            .suspendFunction(eventGatherer::gatherEvents)
            .wasInvoked(exactly = twice)
    }
}
