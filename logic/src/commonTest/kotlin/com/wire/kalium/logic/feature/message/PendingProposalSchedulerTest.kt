package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class PendingProposalSchedulerTest {

    @Test
    fun givenConversation_onScheduleCommit_thenProposalTimerIsScheduled() = runTest {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withScheduleProposalTimerSuccessful()
            .arrange()

        pendingProposalsScheduler.scheduleCommit(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::setProposalTimer)
            .with(eq(Arrangement.PROPOSAL_TIMER))
    }

    @Test
    fun givenExpiredProposalTimer_whenSyncFinishes_thenPendingProposalsIsCommitted() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(TestConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsSuccessful()
            .arrange()

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
            .with(eq(TestConversation.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNonExpiredProposalTimer_whenSyncFinishes_thenPendingProposalIsNotCommitted() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(TestConversation.GROUP_ID, Arrangement.INSTANT_NEAR_FUTURE)))
            .withCommitPendingProposalsSuccessful()
            .arrange()

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
            .with(eq(TestConversation.GROUP_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenNonExpiredProposalTimer_whenSyncFinishesAndWeWait_thenPendingProposalIsCommitted() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(TestConversation.GROUP_ID, Arrangement.INSTANT_NEAR_FUTURE)))
            .withCommitPendingProposalsSuccessful()
            .arrange()

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()
        advanceUntilIdle()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
            .with(eq(TestConversation.GROUP_ID))
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val incrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        val pendingProposalScheduler = PendingProposalSchedulerImpl(
            incrementalSyncRepository,
            lazy { mlsConversationRepository },
            TestKaliumDispatcher
        )

        fun arrange() = this to pendingProposalScheduler

        fun withScheduleProposalTimerSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::setProposalTimer)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withCommitPendingProposalsSuccessful() = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::commitPendingProposals)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withScheduledProposalTimers(timers: List<ProposalTimer>) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::observeProposalTimers)
                .whenInvoked()
                .thenReturn(flowOf(timers))
        }

        companion object {
            val INSTANT_PAST = Instant.DISTANT_PAST
            val INSTANT_NEAR_FUTURE = Clock.System.now().plus(5.seconds)
            val INSTANT_FUTURE = Instant.DISTANT_FUTURE
            val PROPOSAL_TIMER = ProposalTimer(TestConversation.GROUP_ID, INSTANT_FUTURE)
        }
    }
}
