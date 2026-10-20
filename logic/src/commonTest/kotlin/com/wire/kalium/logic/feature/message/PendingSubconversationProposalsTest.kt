/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.PendingSubconversationProposalsImpl
import com.wire.kalium.logic.data.sync.InMemoryIncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.conversation.PendingSubconversationProposalDAO
import com.wire.kalium.persistence.dao.conversation.PendingSubconversationProposalEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class PendingSubconversationProposalsTest {
    private val group = GroupID("subgroup")
    private val deadline = Instant.parse("2020-01-01T00:00:00Z")

    @Test
    fun legacyRecordDefaultsToNotRequestedForRetirement() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-legacy", "test").toDao(), TestKaliumDispatcher.default)
        try {
            db.builder.metadataDAO.insertValue(
                """[{"group":"subgroup","deadline":"2020-01-01T00:00:00Z","token":"legacy-token"}]""",
                "pending-subconversation-proposals-v1",
            )

            val record = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO).observe().first().single()

            assertFalse(record.leaveRequested)
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun ownerRecreationRetainsRetirementMarker() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-retirement-reopen", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val first = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            first.schedule(group, deadline)
            val record = first.current(group)!!

            val marked = first.markForRetirement(record)!!
            val resumed = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)

            assertTrue(marked.leaveRequested)
            assertEquals(record.token, marked.token)
            assertEquals(marked, resumed.current(group))
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun retirementAndAcknowledgementOnlyAffectTheExactToken() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-retirement-token", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            queue.schedule(group, deadline)
            val superseded = queue.current(group)!!
            queue.schedule(group, deadline)
            val current = queue.current(group)!!

            assertEquals(null, queue.markForRetirement(superseded))
            assertEquals(current, queue.current(group))
            assertFalse(current.leaveRequested)

            val marked = queue.markForRetirement(current)!!
            queue.schedule(group, deadline)
            val replacement = queue.current(group)!!
            queue.acknowledge(marked)

            assertEquals(replacement, queue.current(group))
            assertFalse(replacement.leaveRequested)
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun ownerRecreationRetainsDeadlineAndOldAcknowledgementCannotEraseNewProposal() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-reopen", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val first = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            first.schedule(group, deadline)
            val old = first.observe().first().single()
            val resumed = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            assertEquals(old, resumed.observe().first().single())
            resumed.schedule(group, deadline)
            first.acknowledge(old)
            assertFalse(resumed.isCurrent(old))
            val newer = resumed.observe().first().single()
            assertEquals(deadline, newer.deadline)
            resumed.acknowledge(newer)
            assertTrue(PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO).observe().first().isEmpty())
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun failedScheduleAndAcknowledgementPreserveCommittedWork() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-fail", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val proposals = db.builder.pendingSubconversationProposalDAO
            val queue = PendingSubconversationProposalsImpl(proposals)
            queue.schedule(group, deadline)
            val record = queue.observe().first().single()
            val failing = PendingSubconversationProposalsImpl(object : PendingSubconversationProposalDAO by proposals {
                override suspend fun schedule(record: PendingSubconversationProposalEntity) { error("Unavailable") }
                override suspend fun markForRetirement(record: PendingSubconversationProposalEntity): PendingSubconversationProposalEntity? {
                    error("Unavailable")
                }
                override suspend fun acknowledge(record: PendingSubconversationProposalEntity) { error("Unavailable") }
            })
            assertFails { failing.schedule(group, Instant.DISTANT_PAST) }
            assertFails { failing.markForRetirement(record) }
            assertFails { failing.acknowledge(record) }
            assertEquals(record, PendingSubconversationProposalsImpl(proposals).observe().first().single())
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun failingSubgroupDoesNotBlockAnotherGroupsProposalCommit() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-independent", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            queue.schedule(group, deadline)
            val other = GroupID("other-subgroup")
            queue.schedule(other, deadline)
            val transaction = CryptoTransactionProviderArrangementImpl()
            transaction.withTransactionReturning(Either.Right(Unit))
            val mls = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
            val subgroups = mock<SubconversationRepository>(mode = MockMode.autoUnit)
            everySuspend { mls.observeProposalTimers() } returns flowOf()
            everySuspend { mls.commitPendingProposals(any(), any()) } calls {
                if (it.args[1] == group) Either.Left(CoreFailure.Unknown(null)) else Either.Right(Unit)
            }
            val sync = InMemoryIncrementalSyncRepository()
            PendingProposalSchedulerImpl(sync, lazy { mls }, lazy { subgroups }, transaction.cryptoTransactionProvider,
                queue, TestKaliumDispatcher, backgroundScope.coroutineContext).also { it.start() }
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            val remaining = queue.observe().first { records -> records.none { it.group == other } }
            assertEquals(group, remaining.single().group)
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun missingNativeSubgroupDoesNotBlockReadySubgroupAndRetainsItsRecord() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-missing-independent", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            queue.schedule(group, deadline)
            val ready = GroupID("ready-subgroup")
            queue.schedule(ready, deadline)
            val transaction = CryptoTransactionProviderArrangementImpl()
            transaction.withTransactionReturning(Either.Right(Unit))
            val mls = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
            val subgroups = mock<SubconversationRepository>(mode = MockMode.autoUnit)
            everySuspend { mls.observeProposalTimers() } returns flowOf()
            var missingAttempts = 0
            var readyAttempts = 0
            everySuspend { mls.commitPendingProposals(any(), any()) } calls {
                if (it.args[1] == group) {
                    missingAttempts++
                    Either.Left(MLSFailure.ConversationNotFound)
                } else {
                    readyAttempts++
                    Either.Right(Unit)
                }
            }
            val sync = InMemoryIncrementalSyncRepository()
            PendingProposalSchedulerImpl(
                sync,
                lazy { mls },
                lazy { subgroups },
                transaction.cryptoTransactionProvider,
                queue,
                TestKaliumDispatcher,
                backgroundScope.coroutineContext,
            ).also { it.start() }
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            val remaining = queue.observe().first { records -> records.none { it.group == ready } }
            assertEquals(group, remaining.single().group)
            assertTrue(missingAttempts > 0)
            assertEquals(1, readyAttempts)
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun actualSchedulerRetriesOuterTransactionFailureAndAcknowledgesOnlyAfterCommit() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-consumer", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            queue.schedule(group, deadline)
            val record = queue.observe().first().single()
            val transaction = CryptoTransactionProviderArrangementImpl()
            val mls = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
            val subgroups = mock<SubconversationRepository>(mode = MockMode.autoUnit)
            everySuspend { mls.observeProposalTimers() } returns flowOf()
            everySuspend { mls.commitPendingProposals(any(), any()) } returns Either.Right(Unit)
            var attempts = 0
            everySuspend { transaction.cryptoTransactionProvider.transaction<Unit>(any(), any()) } calls {
                assertTrue(queue.isCurrent(record))
                @Suppress("UNCHECKED_CAST")
                val block = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, Unit>
                assertEquals(Either.Right(Unit), block(transaction.transactionContext))
                assertTrue(queue.isCurrent(record)) // successful block is not a committed transaction
                attempts++
                if (attempts == 1) Either.Left(CoreFailure.Unknown(null)) else Either.Right(Unit)
            }
            val sync = InMemoryIncrementalSyncRepository()
            PendingProposalSchedulerImpl(sync, lazy { mls }, lazy { subgroups }, transaction.cryptoTransactionProvider,
                PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO), TestKaliumDispatcher, backgroundScope.coroutineContext).also { it.start() }
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            queue.observe().first { it.isEmpty() }
            assertEquals(2, attempts)
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun missingNativeSubgroupRetainsProposalUntilTheOwnerRestoresIt() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-missing-native", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.pendingSubconversationProposalDAO)
            queue.schedule(group, deadline)
            val record = queue.observe().first().single()
            val transaction = CryptoTransactionProviderArrangementImpl()
            transaction.withTransactionReturning(Either.Right(Unit))
            val mls = mock<MLSConversationRepository>(mode = MockMode.autoUnit)
            val subgroups = mock<SubconversationRepository>(mode = MockMode.autoUnit)
            everySuspend { mls.observeProposalTimers() } returns flowOf()
            var attempts = 0
            everySuspend { mls.commitPendingProposals(any(), any()) } calls {
                assertTrue(queue.isCurrent(record))
                attempts++
                if (attempts == 1) Either.Left(MLSFailure.ConversationNotFound) else Either.Right(Unit)
            }
            val sync = InMemoryIncrementalSyncRepository()
            PendingProposalSchedulerImpl(
                sync,
                lazy { mls },
                lazy { subgroups },
                transaction.cryptoTransactionProvider,
                queue,
                TestKaliumDispatcher,
                backgroundScope.coroutineContext,
            ).also { it.start() }
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            queue.observe().first { it.isEmpty() }
            assertEquals(2, attempts)
        } finally { db.builder.close(); db.delete() }
    }
}
