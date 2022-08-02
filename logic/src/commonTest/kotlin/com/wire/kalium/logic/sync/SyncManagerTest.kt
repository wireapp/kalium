package com.wire.kalium.logic.sync

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.InMemorySyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.sync.incremental.EventGatherer
import com.wire.kalium.logic.sync.incremental.EventProcessor
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun givenSyncCriteriaAreMet_whenCallingOnSlowSyncCompleted_thenShouldStartGatheringEvents() = runTest(TestKaliumDispatcher.default) {
        // Given
        val (arrangement, _) = Arrangement()
            .withSlowSyncComplete()
            .withEventGathererReturning(emptyFlow())
            .arrange()

        arrangement.syncRepository.syncState.test {
            // starts with Waiting
            assertIs<SyncState.Waiting>(awaitItem())

            // When
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
            .withSlowSyncComplete()
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
            .withSlowSyncComplete()
            .withEventGathererReturning(flowOf(event))
            .arrange()

        // When
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
            .withSlowSyncComplete()
            .withEventGathererThrowing(KaliumSyncException("Oopsie", coreFailureCause))
            .arrange()

        // When
        advanceUntilIdle()

        assertEquals(SyncState.Failed(coreFailureCause), arrangement.syncRepository.syncState.first())
    }

    @Test
    fun givenGathererFlowThrows_whenSyncing_thenTheStatusIsUpdatedToFailed() = runTest(TestKaliumDispatcher.default) {
        // Given
        val coreFailureCause = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, syncManager) = Arrangement()
            .withSlowSyncComplete()
            .withEventGathererReturning(flow { throw throw KaliumSyncException("Oopsie", coreFailureCause) })
            .arrange()

        // When
        advanceUntilIdle()

        assertEquals(SyncState.Failed(coreFailureCause), arrangement.syncRepository.syncState.first())
    }

    private class Arrangement {

        @Mock
        val eventProcessor: EventProcessor = configure(mock(EventProcessor::class)) { stubsUnitByDefault = true }

        @Mock
        val eventGatherer: EventGatherer = mock(EventGatherer::class)

        @Mock
        val slowSyncRepository: SlowSyncRepository = mock(SlowSyncRepository::class)

        val syncRepository: SyncRepository = InMemorySyncRepository()

        fun withSlowSyncRepositoryReturning(slowSyncStatusFlow: StateFlow<SlowSyncStatus>) = apply {
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(slowSyncStatusFlow)
        }

        fun withSlowSyncComplete() = apply {
            withSlowSyncRepositoryReturning(MutableStateFlow(SlowSyncStatus.Complete))
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
            syncRepository,
            eventProcessor,
            eventGatherer,
            slowSyncRepository,
            TestKaliumDispatcher
        )

        fun arrange() = this to syncManager
    }
}
