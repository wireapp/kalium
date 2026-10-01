/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.conversation

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubconversationDAOTest : BaseDatabaseTest() {
    private val user = UserIDEntity("subconversation-dao", "test")
    private val conversation = UserIDEntity("conversation", "one.example")
    private lateinit var db: UserDatabaseBuilder

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
    fun mappingsSurviveReopeningAndUpdatingOneRowPreservesOtherRows() = runTest(dispatcher) {
        val otherDomain = conversation.copy(domain = "two.example")
        db.subconversationDAO.upsert(conversation, "conference", "first")
        db.subconversationDAO.upsert(otherDomain, "conference", "other-domain")
        db.subconversationDAO.upsert(conversation, "other-sub", "other-sub")
        db.close()
        db = createDatabase(user, null, true)
        assertEquals("first", db.subconversationDAO.getGroupId(conversation, "conference"))
        val secondOwner = createDatabase(user, null, true)
        try {
            secondOwner.subconversationDAO.upsert(conversation, "conference", "replacement")
            db.subconversationDAO.upsert(otherDomain, "conference", "changed-domain")
            assertEquals("replacement", db.subconversationDAO.getGroupId(conversation, "conference"))
            assertEquals("changed-domain", secondOwner.subconversationDAO.getGroupId(otherDomain, "conference"))
            assertEquals("other-sub", secondOwner.subconversationDAO.getGroupId(conversation, "other-sub"))
            assertFalse(db.subconversationDAO.containsGroup("first"))
            assertTrue(db.subconversationDAO.containsGroup("replacement"))
            secondOwner.subconversationDAO.delete(conversation, "conference")
            assertNull(db.subconversationDAO.getGroupId(conversation, "conference"))
            assertEquals("other-sub", db.subconversationDAO.getGroupId(conversation, "other-sub"))
        } finally {
            secondOwner.close()
        }
    }

    @Test
    fun legacyJsonIsImportedAndRemovedOnlyAfterSuccess() = runTest(dispatcher) {
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        assertEquals("first", db.subconversationDAO.getGroupId(conversation, "conference"))
        assertTrue(db.subconversationDAO.containsGroup("second"))
        assertNull(db.metadataDAO.valueByKey(legacyKey))
        db.close()
        db = createDatabase(user, null, true)
        assertEquals("first", db.subconversationDAO.getGroupId(conversation, "conference"))
        assertEquals("second", db.subconversationDAO.getGroupId(conversation, "other-sub"))
    }

    @Test
    fun malformedLegacyJsonIsPreservedAndImportCanBeRetried() = runTest(dispatcher) {
        db.metadataDAO.insertValue("invalid json", legacyKey)
        assertFails { db.subconversationDAO.containsGroup("first") }
        assertEquals("invalid json", db.metadataDAO.valueByKey(legacyKey))
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        assertEquals("first", db.subconversationDAO.getGroupId(conversation, "conference"))
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    @Test
    fun conflictRollsBackEarlierRowsAndPreservesJsonForRetry() = runTest(dispatcher) {
        db.database.subconversationQueries.upsert(conversation.value, conversation.domain, "other-sub", "conflict")
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        assertFails { db.subconversationDAO.containsGroup("first") }
        assertNull(db.database.subconversationQueries.selectGroup(conversation.value, conversation.domain, "conference").awaitAsOneOrNull())
        assertEquals(legacyJson, db.metadataDAO.valueByKey(legacyKey))
        db.database.subconversationQueries.deleteMapping(conversation.value, conversation.domain, "other-sub")
        assertEquals("first", db.subconversationDAO.getGroupId(conversation, "conference"))
    }

    @Test
    fun migrationCreatesTableAndPreservesLegacyDataUntilImported() = runTest(dispatcher) {
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        db.sqlDriver.execute(null, "DROP TABLE Subconversation", 0).await()
        UserDatabase.Schema.migrate(db.sqlDriver, 151, 152).await()
        assertEquals(legacyJson, db.metadataDAO.valueByKey(legacyKey))
        assertEquals("first", db.subconversationDAO.getGroupId(conversation, "conference"))
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    @Test
    fun failureToDeleteLegacyJsonRollsBackImportedRows() = runTest(dispatcher) {
        db.metadataDAO.insertValue(legacyJson, legacyKey)
        db.sqlDriver.execute(
            null,
            """
                CREATE TRIGGER reject_legacy_delete BEFORE DELETE ON Metadata
                WHEN OLD.key = '$legacyKey'
                BEGIN SELECT RAISE(ABORT, 'simulated storage failure'); END
            """.trimIndent(),
            0
        ).await()
        assertFails { db.subconversationDAO.containsGroup("first") }
        assertEquals(legacyJson, db.metadataDAO.valueByKey(legacyKey))
        assertNull(db.database.subconversationQueries.selectGroup(conversation.value, conversation.domain, "conference").awaitAsOneOrNull())
        db.sqlDriver.execute(null, "DROP TRIGGER reject_legacy_delete", 0).await()
        assertTrue(db.subconversationDAO.containsGroup("first"))
        assertNull(db.metadataDAO.valueByKey(legacyKey))
    }

    private companion object {
        const val legacyKey = "calling-subconversation-groups-v1"
        val legacyJson = """
            [
              {"conversation":{"id":"conversation","domain":"one.example"},"subId":"conference","group":"first"},
              {"conversation":{"id":"conversation","domain":"one.example"},"subId":"other-sub","group":"second"}
            ]
        """.trimIndent()
    }
}
