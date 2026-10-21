/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingDeliveryConfirmationDAOTest : BaseDatabaseTest() {
    private val userId = UserIDEntity("pending-delivery", "wire.test")
    private val conversation = QualifiedIDEntity("conversation", "wire.test")

    @Test
    fun duplicateWorkSurvivesDatabaseReopenAndOnlyAcknowledgedRowsAreRemoved() = runTest(dispatcher) {
        deleteDatabase(userId)
        val first = createDatabase(userId, encryptedDBSecret, true)
        val queue = first.pendingDeliveryConfirmationDAO
        queue.enqueue(conversation, "one")
        queue.enqueue(conversation, "one")
        queue.enqueue(conversation, "two")
        val original = queue.getPendingAfter(0, 10)
        assertEquals(listOf("one", "two"), original.map { it.messageId })
        first.close()
        val second = createDatabase(userId, encryptedDBSecret, true)
        try {
            val reopened = second.pendingDeliveryConfirmationDAO
            assertEquals(original, reopened.getPendingAfter(0, 10))
            reopened.acknowledge(listOf(original.first().id))
            assertEquals(listOf(original.last()), reopened.getPendingAfter(0, 10))
            reopened.enqueue(conversation, "three")
            assertEquals(listOf("three"), reopened.getPendingAfter(original.last().id, 10).map { it.messageId })
        } finally {
            second.close()
        }
    }

    @Test
    fun migrationAddsTheDurableQueueWithoutRemovingExistingMetadata() = runTest(dispatcher) {
        deleteDatabase(userId)
        val database = createDatabase(userId, encryptedDBSecret, true)
        try {
            database.metadataDAO.insertValue("preserved", "migration-proof")
            database.sqlDriver.execute(null, "DROP TABLE PendingDeliveryConfirmations", 0).await()
            UserDatabase.Schema.migrate(database.sqlDriver, 148, 149).await()
            database.pendingDeliveryConfirmationDAO.enqueue(conversation, "after-migration")
            assertEquals("preserved", database.metadataDAO.valueByKey("migration-proof"))
            assertEquals(1, database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).size)
            assertTrue(database.pendingDeliveryConfirmationDAO.getPendingAfter(Long.MAX_VALUE, 10).isEmpty())
        } finally {
            database.close()
        }
    }
}
