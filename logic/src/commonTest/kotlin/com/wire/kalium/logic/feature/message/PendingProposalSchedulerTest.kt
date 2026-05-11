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
package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatten
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PendingProposalSchedulerTest {

    private fun TestScope.testDispatcher(): KaliumDispatcher =
        StandardTestDispatcher(testScheduler).testKaliumDispatcher()

    @Test
    fun givenConversation_onScheduleCommit_thenProposalTimerIsScheduled() = runTest {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withSubconversationRepositoryDoesNotContainGroup()
            .withScheduleProposalTimerSuccessful()
            .arrange(testDispatcher())

        pendingProposalsScheduler.scheduleCommit(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.setProposalTimer(eq(Arrangement.PROPOSAL_TIMER), eq(false))
        }
    }

    @Test
    fun givenSubconversation_onScheduleCommit_thenProposalTimerIsScheduledInMemory() = runTest {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withSubconversationRepositoryContainsGroup(Arrangement.PROPOSAL_TIMER.groupID)
            .withScheduleProposalTimerSuccessful()
            .arrange(testDispatcher())

        pendingProposalsScheduler.scheduleCommit(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.setProposalTimer(eq(Arrangement.PROPOSAL_TIMER), eq(true))
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenSyncFinishes_thenPendingProposalsIsCommitted() = runTest {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenNonExpiredProposalTimer_whenSyncFinishes_thenPendingProposalIsNotCommitted() = runTest {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_NEAR_FUTURE)))
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenNonExpiredProposalTimer_whenSyncFinishesAndWeWait_thenPendingProposalIsCommitted() = runTest {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_NEAR_FUTURE)))
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenSyncIsLive_thenPendingProposalIsCommitted() = runTest {
        val proposalChannel = Channel<List<ProposalTimer>>(Channel.UNLIMITED)
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimersFlow(proposalChannel.consumeAsFlow())
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        advanceUntilIdle()
        proposalChannel.trySend(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_NEAR_FUTURE)))
        advanceUntilIdle()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenSyncIsPending_thenPendingProposalIsNotCommitted() = runTest {
        val proposalChannel = Channel<List<ProposalTimer>>(Channel.UNLIMITED)
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimersFlow(proposalChannel.consumeAsFlow())
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        advanceUntilIdle()
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        advanceUntilIdle()
        proposalChannel.trySend(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenCommitFailsWithConversationNotFound_thenTimerIsCleared() = runTest {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsFailing(MLSFailure.ConversationNotFound)
            .withClearProposalTimerSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.clearProposalTimer(eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenCommitFailsWithServerConversationNotFound_thenTimerIsCleared() =
        runTest {
            val (arrangement, _) = Arrangement()
                .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
                .withCommitPendingProposalsFailing(NetworkFailure.ServerMiscommunication(TestNetworkException.noConversation))
                .withClearProposalTimerSuccessful()
                .arrange(testDispatcher())

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.clearProposalTimer(eq(MockConversation.GROUP_ID))
            }
        }

    @Test
    fun givenExpiredProposalTimer_whenCommitFailsWithStaleProposal_thenTimerIsCleared() = runTest {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsFailing(MLSFailure.StaleProposal)
            .withClearProposalTimerSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.clearProposalTimer(eq(MockConversation.GROUP_ID))
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenCommitFailsWithOtherError_thenTimerIsNotCleared() = runTest {
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsFailing(CoreFailure.Unknown(null))
            .withClearProposalTimerSuccessful()
            .arrange(testDispatcher())

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        yield()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), eq(MockConversation.GROUP_ID))
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.clearProposalTimer(any())
        }
    }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val incrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val subconversationRepository = mock<SubconversationRepository>(mode = MockMode.autoUnit)

        suspend fun arrange(testDispatcher: KaliumDispatcher) = this to PendingProposalSchedulerImpl(
            incrementalSyncRepository,
            lazy { mlsConversationRepository },
            lazy { subconversationRepository },
            cryptoTransactionProvider,
            testDispatcher
        ).also {
            withTransactionReturning(Either.Right(Unit))
        }

        suspend fun withSubconversationRepositoryDoesNotContainGroup() = apply {
            everySuspend {
                subconversationRepository.containsSubconversation(any())
            } returns false
        }

        suspend fun withSubconversationRepositoryContainsGroup(groupID: GroupID) = apply {
            everySuspend {
                subconversationRepository.containsSubconversation(eq(groupID))
            } returns true
        }

        suspend fun withScheduleProposalTimerSuccessful() = apply {
            everySuspend {
                mlsConversationRepository.setProposalTimer(any(), any())
            } returns Unit
        }

        suspend fun withCommitPendingProposalsSuccessful() = apply {
            everySuspend {
                mlsConversationRepository.commitPendingProposals(any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withCommitPendingProposalsFailing(failure: CoreFailure) = apply {
            everySuspend {
                mlsConversationRepository.commitPendingProposals(any(), any())
            } returns Either.Left(failure)
        }

        suspend fun withClearProposalTimerSuccessful() = apply {
            everySuspend {
                mlsConversationRepository.clearProposalTimer(any())
            } returns Unit
        }

        suspend fun withScheduledProposalTimers(timers: List<ProposalTimer>) = apply {
            everySuspend {
                mlsConversationRepository.observeProposalTimers()
            } returns flowOf(timers).flatten()
        }

        suspend fun withScheduledProposalTimersFlow(timersFlow: Flow<List<ProposalTimer>>) = apply {
            everySuspend {
                mlsConversationRepository.observeProposalTimers()
            } returns timersFlow.flatten()
        }

        companion object {
            val INSTANT_PAST = Instant.DISTANT_PAST
            val INSTANT_NEAR_FUTURE = DateTimeUtil.currentInstant().plus(5.seconds)
            val INSTANT_FUTURE = Instant.DISTANT_FUTURE
            val PROPOSAL_TIMER = ProposalTimer(MockConversation.GROUP_ID, INSTANT_FUTURE)
        }
    }
}
