package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.event.EventProcessor
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class SyncManagerTest {

    @Test
    fun givenSyncManagerWasCreated_whenSyncCriteriaAreMet_thenShouldStartSlowSync() = runTest(TestKaliumDispatcher.default) {
        // Given
        val syncCriteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)

        val (arrangement, _) = Arrangement()
            .withCriteriaProviderReturning(syncCriteriaChannel.consumeAsFlow())
            .arrange()

        // When
        syncCriteriaChannel.send(SyncCriteriaResolution.Ready)
        advanceUntilIdle()

        // Then
        verify(arrangement.workScheduler)
            .function(arrangement.workScheduler::enqueueSlowSyncIfNeeded)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncManagerWasCreated_whenSyncCriteriaAreNotYetMet_thenShouldNotStartSlowSync() = runTest {
        // Given
        val syncCriteriaChannel = Channel<SyncCriteriaResolution>(Channel.UNLIMITED)

        val (arrangement, _) = Arrangement()
            .withCriteriaProviderReturning(syncCriteriaChannel.consumeAsFlow())
            .arrange()

        // When
        syncCriteriaChannel.send(SyncCriteriaResolution.MissingRequirement("Test cause"))

        // Then
        verify(arrangement.workScheduler)
            .function(arrangement.workScheduler::enqueueSlowSyncIfNeeded)
            .wasNotInvoked()
    }

    @Test
    fun givenSyncCriteriaAreMet_whenCallingOnSlowSyncCompleted_thenShouldStartGatheringEvents() = runTest(TestKaliumDispatcher.default) {
        // Given
        val (arrangement, syncManager) = Arrangement()
            .withSyncCriteriaMet()
            .withEventGathererReturning(emptyFlow())
            .arrange()

        arrangement.syncRepository.syncState.test {
            // starts with Waiting
            assertIs<SyncState.Waiting>(awaitItem())

            // When
            syncManager.onSlowSyncComplete()
            advanceUntilIdle()

            // Then
            assertIs<SyncState.GatheringPendingEvents>(awaitItem())

            // A failure happens when live events close the flow
            cancelAndIgnoreRemainingEvents()
        }

        // Then
        verify(arrangement.eventGatherer)
            .suspendFunction(arrangement.eventGatherer::gatherEvents)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSyncCriteriaAreMet_whenSlowSyncIsNotYetCompleted_thenShouldNotGatherEvents() = runTest(TestKaliumDispatcher.default) {
        // Given
        val (arrangement, _) = Arrangement()
            .withSyncCriteriaMet()
            .withEventGathererReturning(emptyFlow())
            .arrange()

        arrangement.syncRepository.syncState.test {
            // Then
            assertIs<SyncState.Waiting>(awaitItem())

            expectNoEvents()

            // A failure happens when live events close the flow
            cancelAndIgnoreRemainingEvents()
        }

        // Then
        verify(arrangement.eventGatherer)
            .suspendFunction(arrangement.eventGatherer::gatherEvents)
            .wasNotInvoked()
    }

    @Test
    fun givenSlowSyncCompletedAndAnEventIsReceived_whenSyncing_thenTheEventProcessorIsCalled() = runTest(TestKaliumDispatcher.default) {
        // Given
        val event = TestEvent.memberJoin()
        val (arrangement, syncManager) = Arrangement()
            .withSyncCriteriaMet()
            .withEventGathererReturning(flowOf(event))
            .arrange()

        // When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        // Then
        verify(arrangement.eventProcessor)
            .suspendFunction(arrangement.eventProcessor::processEvent)
            .with(eq(event))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenGathererFails_whenSyncing_thenTheStatusIsUpdatedToFailed() = runTest(TestKaliumDispatcher.default) {
        // Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, syncManager) = Arrangement()
            .withSyncCriteriaMet()
            .withEventGathererThrowing(KaliumSyncException("Oopsie", coreFailureCause))
            .arrange()

        // When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        assertEquals(SyncState.Failed(coreFailureCause), arrangement.syncRepository.syncState.first())
    }

    @Test
    fun givenGathererFlowThrows_whenSyncing_thenTheStatusIsUpdatedToFailed() = runTest(TestKaliumDispatcher.default) {
        // Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, syncManager) = Arrangement()
            .withSyncCriteriaMet()
            .withEventGathererReturning(flow { throw throw KaliumSyncException("Oopsie", coreFailureCause) })
            .arrange()

        // When
        syncManager.onSlowSyncComplete()
        advanceUntilIdle()

        assertEquals(SyncState.Failed(coreFailureCause), arrangement.syncRepository.syncState.first())
    }

    private class Arrangement {

        @Mock
        val workScheduler: UserSessionWorkScheduler = configure(mock(UserSessionWorkScheduler::class)) {
            stubsUnitByDefault = true
        }

        @Mock
        val eventProcessor: EventProcessor = configure(mock(EventProcessor::class)) { stubsUnitByDefault = true }

        @Mock
        val eventGatherer: EventGatherer = mock(EventGatherer::class)

        @Mock
        val syncCriteriaProvider: SyncCriteriaProvider = mock(SyncCriteriaProvider::class)

        val syncRepository: SyncRepository = InMemorySyncRepository()

        fun withCriteriaProviderReturning(criteriaFlow: Flow<SyncCriteriaResolution>) = apply {
            given(syncCriteriaProvider)
                .suspendFunction(syncCriteriaProvider::syncCriteriaFlow)
                .whenInvoked()
                .thenReturn(criteriaFlow)
        }

        fun withSyncCriteriaMet() = apply {
            withCriteriaProviderReturning(flowOf(SyncCriteriaResolution.Ready))
        }

        fun withEventGathererReturning(eventFlow: Flow<Event>) = apply {
            given(eventGatherer)
                .suspendFunction(eventGatherer::gatherEvents)
                .whenInvoked()
                .thenReturn(eventFlow)
        }

        fun withEventGathererThrowing(throwable: Throwable) = apply {
            given(eventGatherer)
                .suspendFunction(eventGatherer::gatherEvents)
                .whenInvoked()
                .thenThrow(throwable)
        }

        private val syncManager = SyncManagerImpl(
            workScheduler,
            syncRepository,
            eventProcessor,
            eventGatherer,
            syncCriteriaProvider,
            TestKaliumDispatcher
        )

        fun arrange() = this to syncManager
    }
}
