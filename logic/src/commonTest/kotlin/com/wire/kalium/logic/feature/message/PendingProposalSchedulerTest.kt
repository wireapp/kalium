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

import com.wire.kalium.common.error.serverMiscommunication

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatten
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ProposalTimer
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PendingSubconversationProposal
import com.wire.kalium.logic.data.message.PendingSubconversationProposals
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield

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
            arrangement.mlsConversationRepository.setProposalTimer(Arrangement.PROPOSAL_TIMER, false)
        }
    }

    @Test
    fun givenSubconversation_onScheduleCommit_thenProposalTimerIsPersisted() = runTest {
        val (arrangement, pendingProposalsScheduler) = Arrangement()
            .withSubconversationRepositoryContainsGroup(Arrangement.PROPOSAL_TIMER.groupID)
            .withScheduleProposalTimerSuccessful()
            .arrange(testDispatcher())

        pendingProposalsScheduler.scheduleCommit(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.subgroupProposals.schedule(Arrangement.PROPOSAL_TIMER.groupID, Arrangement.PROPOSAL_TIMER.timestamp)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
        }
    }

    @Test
    fun givenUserSessionScopeIsCancelled_whenSyncFinishes_thenPendingProposalsAreNotCommitted() = runTest {
        val userSessionJob = SupervisorJob()
        val (arrangement, _) = Arrangement()
            .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher(), userSessionJob)

        userSessionJob.cancel()
        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        advanceUntilIdle()

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), any())
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.clearProposalTimer(MockConversation.GROUP_ID)
        }
    }

    @Test
    fun givenExpiredProposalTimer_whenCommitFailsWithServerConversationNotFound_thenTimerIsCleared() =
        runTest {
            val (arrangement, _) = Arrangement()
                .withScheduledProposalTimers(listOf(ProposalTimer(MockConversation.GROUP_ID, Arrangement.INSTANT_PAST)))
                .withCommitPendingProposalsFailing(serverMiscommunication(TestNetworkException.noConversation))
                .withClearProposalTimerSuccessful()
                .arrange(testDispatcher())

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            yield()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.clearProposalTimer(MockConversation.GROUP_ID)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.clearProposalTimer(MockConversation.GROUP_ID)
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
            arrangement.mlsConversationRepository.commitPendingProposals(any(), MockConversation.GROUP_ID)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.clearProposalTimer(any())
        }
    }

    @Test
    fun givenLeaveRequestedAndNativeSubconversationExists_whenTimerExpires_thenPendingProposalIsCommitted() = runTest {
        val record = Arrangement.leaveRequestedProposal()
        val (arrangement, _) = Arrangement()
            .withPendingSubconversationProposal(record)
            .withNativeConversationExists(true)
            .withCommitPendingProposalsSuccessful()
            .arrange(testDispatcher(), backgroundScope.coroutineContext)

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.conversationExists(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), record.group)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.subgroupProposals.acknowledge(record)
        }
    }

    @Test
    fun givenLeaveRequested_whenNativeLookupFails_thenPendingProposalIsRetained() = runTest {
        val record = Arrangement.leaveRequestedProposal()
        val (arrangement, _) = Arrangement()
            .withPendingSubconversationProposal(record)
            .withNativeConversationLookupFailing()
            .arrange(testDispatcher(), backgroundScope.coroutineContext)

        arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
        runCurrent()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.conversationExists(any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsConversationRepository.commitPendingProposals(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.subgroupProposals.acknowledge(any())
        }
    }

    @Test
    fun givenLeaveRequestedAndNativeSubconversationMissing_whenTransactionCommits_thenPendingProposalIsAcknowledged() =
        runTest {
            val record = Arrangement.leaveRequestedProposal()
            val transactionCanCommit = CompletableDeferred<Unit>()
            val (arrangement, _) = Arrangement()
                .withPendingSubconversationProposal(record)
                .withNativeConversationExists(false)
                .arrange(testDispatcher(), backgroundScope.coroutineContext)
            everySuspend {
                arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
            } calls {
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, Unit>
                block(arrangement.transactionContext).also { transactionCanCommit.await() }
            }

            arrangement.incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            runCurrent()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsContext.conversationExists(any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsConversationRepository.commitPendingProposals(any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.subgroupProposals.acknowledge(any())
            }

            transactionCanCommit.complete(Unit)
            runCurrent()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.subgroupProposals.acknowledge(record)
            }
        }

    private class Arrangement: CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        val incrementalSyncRepository = InMemoryIncrementalSyncRepository()
        val mlsConversationRepository = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
        val subgroupProposals = mock<PendingSubconversationProposals>(mode = MockMode.autoUnit)

        init {
            everySuspend { subgroupProposals.observe() } returns flowOf(emptyList())
        }

        val subconversationRepository = mock<SubconversationRepository>(mode = MockMode.autoUnit)

        suspend fun arrange(
            testDispatcher: KaliumDispatcher,
            parentContext: CoroutineContext = SupervisorJob(),
        ) = this to PendingProposalSchedulerImpl(
            incrementalSyncRepository,
            lazy { mlsConversationRepository },
            lazy { subconversationRepository },
            cryptoTransactionProvider,
            subgroupProposals,
            testDispatcher,
            parentContext,
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
                subconversationRepository.containsSubconversation(groupID)
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

        suspend fun withPendingSubconversationProposal(record: PendingSubconversationProposal) = apply {
            everySuspend { subgroupProposals.observe() } returns flowOf(listOf(record))
            everySuspend { subgroupProposals.isCurrent(record) } returns true
            withScheduledProposalTimers(emptyList())
        }

        suspend fun withNativeConversationExists(exists: Boolean) = apply {
            everySuspend { mlsContext.conversationExists(any()) } returns exists
        }

        suspend fun withNativeConversationLookupFailing() = apply {
            everySuspend { mlsContext.conversationExists(any()) } throws IllegalStateException("Native lookup failed")
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

            fun leaveRequestedProposal() = PendingSubconversationProposal(
                group = MockConversation.GROUP_ID,
                deadline = INSTANT_PAST,
                token = "leave-requested",
                leaveRequested = true,
            )
        }
    }
}
