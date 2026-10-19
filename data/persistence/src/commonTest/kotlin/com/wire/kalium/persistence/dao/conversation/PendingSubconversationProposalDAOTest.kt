/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.conversation

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.turbine.test
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Instant

class PendingSubconversationProposalDAOTest : BaseDatabaseTest() {
    private val user = UserIDEntity("pending-subconversation-proposal-dao", "test")
    private lateinit var db: UserDatabaseBuilder
    private val first = PendingSubconversationProposalEntity("first", Instant.parse("2026-10-05T12:00:00.123456789Z"), "first-token")
    private val second = first.copy(groupId = "second", token = "second-token", leaveRequested = true)

    @BeforeTest
    fun setUp() {
        deleteDatabase(user)
        db = createDatabase(user, null, true)
    }

    @AfterTest
    fun tearDown() {
        db.close()
        deleteDatabase(user)
    }

    @Test
    fun preciseRecordsAndRetirementSurviveReopenAndMappingDeletion() = runTest(dispatcher) {
        val conversation = UserIDEntity("conversation", "test")
        db.subconversationDAO.upsert(conversation, "conference", first.groupId)
        db.pendingSubconversationProposalDAO.schedule(first)
        db.pendingSubconversationProposalDAO.schedule(second)
        val marked = db.pendingSubconversationProposalDAO.markForRetirement(first)
        db.subconversationDAO.delete(conversation, "conference")
        db.close()
        db = createDatabase(user, null, true)
        assertEquals(first.copy(leaveRequested = true), marked)
        assertEquals(marked, db.pendingSubconversationProposalDAO.current(first.groupId))
        assertEquals(second.copy(leaveRequested = false), db.pendingSubconversationProposalDAO.current(second.groupId))
        assertFalse(db.subconversationDAO.containsGroup(first.groupId))
        db.pendingSubconversationProposalDAO.acknowledge(checkNotNull(marked))
        assertNull(db.pendingSubconversationProposalDAO.current(first.groupId))
        assertTrue(db.pendingSubconversationProposalDAO.isCurrent(second.copy(leaveRequested = false)))
    }

    @Test
    fun replacementByAnotherOwnerSurvivesStaleAcknowledgementAndRetirement() = runTest(dispatcher) {
        db.pendingSubconversationProposalDAO.schedule(first)
        val otherOwner = createDatabase(user, null, true)
        try {
            val replacement = first.copy(token = "replacement")
            otherOwner.pendingSubconversationProposalDAO.schedule(replacement)
            db.pendingSubconversationProposalDAO.acknowledge(first)
            assertNull(db.pendingSubconversationProposalDAO.markForRetirement(first))
            assertFalse(db.pendingSubconversationProposalDAO.isCurrent(first))
            assertEquals(replacement, db.pendingSubconversationProposalDAO.current(first.groupId))
            val marked = checkNotNull(otherOwner.pendingSubconversationProposalDAO.markForRetirement(replacement))
            db.pendingSubconversationProposalDAO.acknowledge(replacement)
            assertNull(db.pendingSubconversationProposalDAO.markForRetirement(replacement))
            assertFalse(db.pendingSubconversationProposalDAO.isCurrent(replacement))
            assertEquals(marked, db.pendingSubconversationProposalDAO.current(first.groupId))
            db.pendingSubconversationProposalDAO.acknowledge(marked)
            assertNull(otherOwner.pendingSubconversationProposalDAO.current(first.groupId))
        } finally {
            otherOwner.close()
        }
    }

    @Test
    fun schedulePreservesEarliestDeadlineToNanosecondsAndClearsRetirement() = runTest(dispatcher) {
        db.pendingSubconversationProposalDAO.schedule(first)
        db.pendingSubconversationProposalDAO.markForRetirement(first)
        db.pendingSubconversationProposalDAO.schedule(first.copy(deadline = first.deadline + 1.nanoseconds, token = "later"))
        assertEquals(first.copy(token = "later"), db.pendingSubconversationProposalDAO.current(first.groupId))
        val earlier = first.copy(deadline = first.deadline - 1.nanoseconds, token = "earlier")
        db.pendingSubconversationProposalDAO.schedule(earlier.copy(leaveRequested = true))
        assertEquals(earlier, db.pendingSubconversationProposalDAO.current(first.groupId))
    }

    @Test
    fun matchingTokenStillRequiresMatchingDeadlineAndMarker() = runTest(dispatcher) {
        db.pendingSubconversationProposalDAO.schedule(first)
        val wrongDeadline = first.copy(deadline = first.deadline + 1.nanoseconds)
        val wrongMarker = first.copy(leaveRequested = true)
        db.pendingSubconversationProposalDAO.acknowledge(wrongDeadline)
        db.pendingSubconversationProposalDAO.acknowledge(wrongMarker)
        assertNull(db.pendingSubconversationProposalDAO.markForRetirement(wrongDeadline))
        assertNull(db.pendingSubconversationProposalDAO.markForRetirement(wrongMarker))
        assertEquals(first, db.pendingSubconversationProposalDAO.current(first.groupId))
        val marked = checkNotNull(db.pendingSubconversationProposalDAO.markForRetirement(first))
        assertEquals(marked, db.pendingSubconversationProposalDAO.markForRetirement(marked))
        assertTrue(db.pendingSubconversationProposalDAO.isCurrent(marked))
    }

    @Test
    fun observationImportsMarkedAndUnmarkedLegacyRecordsBeforeEmitting() = runTest(dispatcher) {
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        db.pendingSubconversationProposalDAO.observe().test {
            assertEquals(listOf(first, second), awaitItem())
            assertNull(db.metadataDAO.valueByKey(legacyKey))
            db.pendingSubconversationProposalDAO.acknowledge(first)
            assertEquals(listOf(second), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        db.close()
        db = createDatabase(user, null, true)
        assertEquals(listOf(second), db.pendingSubconversationProposalDAO.observe().first())
    }

    @Test
    fun malformedLegacyJsonIsPreservedAndRetryImportsThroughSameOwner() = runTest(dispatcher) {
        db.metadataDAO.insertValue("invalid json", legacyKey)
        assertFails { db.pendingSubconversationProposalDAO.current(first.groupId) }
        assertEquals("invalid json", db.metadataDAO.valueByKey(legacyKey))
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        assertEquals(first, db.pendingSubconversationProposalDAO.current(first.groupId))
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    @Test
    fun invalidDeadlineAndDuplicateGroupsPreserveLegacyWithoutPartialImport() = runTest(dispatcher) {
        val invalid = legacyJson.replace("2026-10-05T12:00:00.123456789Z", "invalid")
        db.metadataDAO.insertValue(invalid, legacyKey)
        assertFails { db.pendingSubconversationProposalDAO.observe().first() }
        assertEquals(emptyList(), db.database.pendingSubconversationProposalQueries.selectAll().awaitAsList())
        assertEquals(invalid, db.metadataDAO.valueByKey(legacyKey))
        val duplicate = legacyJson.replace("\"second\"", "\"first\"")
        db.metadataDAO.insertValue(duplicate, legacyKey)
        assertFails { db.pendingSubconversationProposalDAO.current(first.groupId) }
        assertEquals(emptyList(), db.database.pendingSubconversationProposalQueries.selectAll().awaitAsList())
        assertEquals(duplicate, db.metadataDAO.valueByKey(legacyKey))
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        assertEquals(listOf(first, second), db.pendingSubconversationProposalDAO.observe().first())
    }

    @Test
    fun legacyConflictRollsBackEarlierRowsAndNeverReplacesNewerSqlToken() = runTest(dispatcher) {
        val replacement = second.copy(token = "replacement", leaveRequested = false)
        db.pendingSubconversationProposalDAO.schedule(replacement)
        val otherOwner = createDatabase(user, null, true)
        try {
            db.metadataDAO.insertValue(legacyJson, legacyKey)
            assertFails { otherOwner.pendingSubconversationProposalDAO.current(first.groupId) }
            assertNull(db.database.pendingSubconversationProposalQueries.selectCurrent(first.groupId).awaitAsOneOrNull())
            assertEquals("replacement", db.database.pendingSubconversationProposalQueries.selectCurrent(second.groupId).awaitAsOneOrNull()?.token)
            assertEquals(legacyJson, db.metadataDAO.valueByKey(legacyKey))
            // Resolve the conflict explicitly; the same DAO must retry the complete import.
            db.database.pendingSubconversationProposalQueries.acknowledge(
                replacement.groupId, replacement.deadline.toString(), replacement.token, 0
            )
            assertEquals(first, otherOwner.pendingSubconversationProposalDAO.current(first.groupId))
            assertEquals(second, db.pendingSubconversationProposalDAO.current(second.groupId))
            assertNull(db.metadataDAO.valueByKey(legacyKey))
        } finally {
            otherOwner.close()
        }
    }

    @Test
    fun identicalExistingRecordAllowsLegacyImportThroughTwoOwners() = runTest(dispatcher) {
        db.pendingSubconversationProposalDAO.schedule(first)
        val otherOwner = createDatabase(user, null, true)
        try {
            db.metadataDAO.insertValue(legacyJson, legacyKey)
            assertEquals(first, db.pendingSubconversationProposalDAO.current(first.groupId))
            assertEquals(second, otherOwner.pendingSubconversationProposalDAO.current(second.groupId))
            assertNull(db.metadataDAO.valueByKey(legacyKey))
        } finally {
            otherOwner.close()
        }
    }

    @Test
    fun failedLegacyDeletionRollsBackAllRowsAndCanBeRetried() = runTest(dispatcher) {
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        db.sqlDriver.execute(
            null,
            """
                CREATE TRIGGER reject_proposal_legacy_delete BEFORE DELETE ON Metadata
                WHEN OLD.key = '$legacyKey'
                BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END
            """.trimIndent(),
            0
        ).await()
        assertFails { db.pendingSubconversationProposalDAO.current(first.groupId) }
        assertEquals(legacyJson, db.metadataDAO.valueByKey(legacyKey))
        assertEquals(emptyList(), db.database.pendingSubconversationProposalQueries.selectAll().awaitAsList())
        db.sqlDriver.execute(null, "DROP TRIGGER reject_proposal_legacy_delete", 0).await()
        assertEquals(listOf(first, second), db.pendingSubconversationProposalDAO.observe().first())
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    @Test
    fun migrationCreatesIndependentTableAndPreservesLegacyUntilImport() = runTest(dispatcher) {
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        db.sqlDriver.execute(null, "DROP TABLE PendingSubconversationProposal", 0).await()
        UserDatabase.Schema.migrate(db.sqlDriver, 156, 157).await()
        assertEquals(legacyJson, db.metadataDAO.valueByKey(legacyKey))
        assertEquals(listOf(first, second), db.pendingSubconversationProposalDAO.observe().first())
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    @Test
    fun clearingClientWorkIsAtomicAndDoesNotImportMalformedLegacy() = runTest(dispatcher) {
        db.pendingSubconversationProposalDAO.schedule(first)
        db.metadataDAO.insertValue("malformed legacy", legacyKey)
        db.sqlDriver.execute(null, """
            CREATE TRIGGER reject_queue_reset BEFORE DELETE ON Metadata
            WHEN OLD.key = '$legacyKey'
            BEGIN SELECT RAISE(ABORT, 'simulated reset failure'); END
        """.trimIndent(), 0).await()

        assertFails { db.pendingSubconversationProposalDAO.clear() }
        assertEquals(first.token, db.database.pendingSubconversationProposalQueries.selectCurrent(first.groupId).awaitAsOneOrNull()?.token)
        assertEquals("malformed legacy", db.metadataDAO.valueByKey(legacyKey))
        db.sqlDriver.execute(null, "DROP TRIGGER reject_queue_reset", 0).await()

        db.pendingSubconversationProposalDAO.clear()

        assertTrue(db.pendingSubconversationProposalDAO.observe().first().isEmpty())
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    private companion object {
        const val legacyKey = "pending-subconversation-proposals-v1"
        val legacyJson = """
            [
              {"group":"first","deadline":"2026-10-05T12:00:00.123456789Z","token":"first-token"},
              {"group":"second","deadline":"2026-10-05T12:00:00.123456789Z","token":"second-token","leaveRequested":true}
            ]
        """.trimIndent()
    }
}
