package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.emptyFlow
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
    private val eventRepository: EventRepository = mock(EventRepository::class)

    @Mock
    private val conversationEventReceiver: ConversationEventReceiver = mock(ConversationEventReceiver::class)

    private lateinit var syncRepository: SyncRepository
    private lateinit var syncManager: SyncManager

    @BeforeTest
    fun setup() {
        syncRepository = InMemorySyncRepository()
        syncManager = SyncManagerImpl(
            workScheduler,
            eventRepository,
            TestKaliumDispatcher,
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
}
