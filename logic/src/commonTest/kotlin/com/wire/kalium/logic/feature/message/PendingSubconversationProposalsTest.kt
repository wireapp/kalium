/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.error.CoreFailure
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
import com.wire.kalium.persistence.dao.MetadataDAO
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
    fun ownerRecreationRetainsDeadlineAndOldAcknowledgementCannotEraseNewProposal() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-reopen", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val first = PendingSubconversationProposalsImpl(db.builder.metadataDAO)
            first.schedule(group, deadline)
            val old = first.observe().first().single()
            val resumed = PendingSubconversationProposalsImpl(db.builder.metadataDAO)
            assertEquals(old, resumed.observe().first().single())
            resumed.schedule(group, deadline)
            first.acknowledge(old)
            assertFalse(resumed.isCurrent(old))
            val newer = resumed.observe().first().single()
            assertEquals(deadline, newer.deadline)
            resumed.acknowledge(newer)
            assertTrue(PendingSubconversationProposalsImpl(db.builder.metadataDAO).observe().first().isEmpty())
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun failedScheduleAndAcknowledgementPreserveCommittedWork() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-fail", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val metadata = db.builder.metadataDAO
            val queue = PendingSubconversationProposalsImpl(metadata)
            queue.schedule(group, deadline)
            val record = queue.observe().first().single()
            val failing = PendingSubconversationProposalsImpl(object : MetadataDAO by metadata {
                override suspend fun insertValue(value: String, key: String) { error("Unavailable") }
            })
            assertFails { failing.schedule(group, Instant.DISTANT_PAST) }
            assertFails { failing.acknowledge(record) }
            assertEquals(record, PendingSubconversationProposalsImpl(metadata).observe().first().single())
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun failingSubgroupDoesNotBlockAnotherGroupsProposalCommit() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-independent", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.metadataDAO)
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
                queue, TestKaliumDispatcher, backgroundScope.coroutineContext)
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            val remaining = queue.observe().first { records -> records.none { it.group == other } }
            assertEquals(group, remaining.single().group)
        } finally { db.builder.close(); db.delete() }
    }

    @Test
    fun actualSchedulerRetriesOuterTransactionFailureAndAcknowledgesOnlyAfterCommit() = runTest(TestKaliumDispatcher.default) {
        val db = TestUserDatabase(QualifiedID("proposal-consumer", "test").toDao(), TestKaliumDispatcher.default)
        try {
            val queue = PendingSubconversationProposalsImpl(db.builder.metadataDAO)
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
                PendingSubconversationProposalsImpl(db.builder.metadataDAO), TestKaliumDispatcher, backgroundScope.coroutineContext)
            sync.updateIncrementalSyncState(IncrementalSyncStatus.Live)
            queue.observe().first { it.isEmpty() }
            assertEquals(2, attempts)
        } finally { db.builder.close(); db.delete() }
    }
}
