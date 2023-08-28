/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatten
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PendingProposalSchedulerTest {

    @Test
    fun givenConversation_onScheduleCommit_thenProposalTimerIsScheduled() = runTest {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withSubconversationRepositoryDoesNotContainGroup()
            .withScheduleProposalTimerSuccessful()
            .arrange()

        pendingProposalsScheduler.scheduleCommit(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::setProposalTimer)
            .with(eq(Arrangement.PROPOSAL_TIMER), eq(false))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSubconversation_onScheduleCommit_thenProposalTimerIsScheduledInMemory() = runTest {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withSubconversationRepositoryContainsGroup(Arrangement.PROPOSAL_TIMER.groupID)
            .withScheduleProposalTimerSuccessful()
            .arrange()

        pendingProposalsScheduler.scheduleCommit(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::setProposalTimer)
            .with(eq(Arrangement.PROPOSAL_TIMER), eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenExpiredProposalTimer_whenSyncFinishes_thenPendingProposalsIsCommitted() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
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
    fun givenMLSSupportIsDisabled_whenSyncIsLive_thenPendingProposalIsNotCommitted() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(TestConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsSuccessful()
            .arrange()

        arrangement.kaliumConfigs.isMLSSupportEnabled = false
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
            .with(eq(TestConversation.GROUP_ID))
            .wasNotInvoked()
    }

    @Test
    fun givenNonExpiredProposalTimer_whenSyncFinishes_thenPendingProposalIsNotCommitted() = runTest(TestKaliumDispatcher.default) {
        val (arrangement, _) = Arrangement()
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
        val (arrangement, _) = Arrangement()
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

    @Test
    fun givenExpiredProposalTimer_whenSyncIsLive_thenPendingProposalIsCommitted() = runTest(TestKaliumDispatcher.default) {
        val proposalChannel = Channel<List<ProposalTimer>>(Channel.UNLIMITED)
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimersFlow(proposalChannel.consumeAsFlow())
            .withCommitPendingProposalsSuccessful()
            .arrange()

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        advanceUntilIdle()
        proposalChannel.trySend(listOf(ProposalTimer(TestConversation.GROUP_ID, Arrangement.INSTANT_NEAR_FUTURE)))
        advanceUntilIdle()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
            .with(eq(TestConversation.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenExpiredProposalTimer_whenSyncIsPending_thenPendingProposalIsNotCommitted() = runTest(TestKaliumDispatcher.default) {
        val proposalChannel = Channel<List<ProposalTimer>>(Channel.UNLIMITED)
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimersFlow(proposalChannel.consumeAsFlow())
            .withCommitPendingProposalsSuccessful()
            .arrange()

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        advanceUntilIdle()
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        advanceUntilIdle()
        proposalChannel.trySend(listOf(ProposalTimer(TestConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
        advanceUntilIdle()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
            .with(eq(TestConversation.GROUP_ID))
            .wasNotInvoked()
    }

    private class Arrangement {

        val kaliumConfigs = KaliumConfigs()

        @Mock
        val incrementalSyncRepository = InMemoryIncrementalSyncRepository()

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        val pendingProposalScheduler = PendingProposalSchedulerImpl(
            kaliumConfigs,
            incrementalSyncRepository,
            lazy { mlsConversationRepository },
            lazy { subconversationRepository },
            TestKaliumDispatcher
        )

        fun arrange() = this to pendingProposalScheduler

        fun withSubconversationRepositoryDoesNotContainGroup() = apply {
            given(subconversationRepository)
                .suspendFunction(subconversationRepository::containsSubconversation)
                .whenInvokedWith(any())
                .thenReturn(false)
        }

        fun withSubconversationRepositoryContainsGroup(groupID: GroupID) = apply {
            given(subconversationRepository)
                .suspendFunction(subconversationRepository::containsSubconversation)
                .whenInvokedWith(eq(groupID))
                .thenReturn(true)
        }

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
                .thenReturn(flowOf(timers).flatten())
        }

        fun withScheduledProposalTimersFlow(timersFlow: Flow<List<ProposalTimer>>) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::observeProposalTimers)
                .whenInvoked()
                .thenReturn(timersFlow.flatten())
        }

        companion object {
            val INSTANT_PAST = Instant.DISTANT_PAST
            val INSTANT_NEAR_FUTURE = DateTimeUtil.currentInstant().plus(5.seconds)
            val INSTANT_FUTURE = Instant.DISTANT_FUTURE
            val PROPOSAL_TIMER = ProposalTimer(TestConversation.GROUP_ID, INSTANT_FUTURE)
        }
    }
}
