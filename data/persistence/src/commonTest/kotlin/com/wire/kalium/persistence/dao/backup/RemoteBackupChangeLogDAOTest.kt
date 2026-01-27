/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.persistence.dao.backup

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteBackupChangeLogDAOTest : BaseDatabaseTest() {

    private lateinit var dao: RemoteBackupChangeLogDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        dao = db.remoteBackupChangeLogDAO
    }

    @Test
    fun givenNoEntries_whenLoggingMessageUpsert_thenEntryIsStored() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertEquals(MESSAGE_NONCE_1, messageId)
            assertEquals(ChangeLogEventType.MESSAGE_UPSERT, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
        }
    }

    @Test
    fun givenEmptymessageId_whenLoggingMessageUpsert_thenInsertFails() = runTest(dispatcher) {
        val failure = runCatching {
            dao.logMessageUpsert(CONVERSATION_ID_1, "", TIMESTAMP_1)
        }.exceptionOrNull()

        assertTrue(failure != null)
    }

    @Test
    fun givenExistingMessageUpsert_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        assertEquals(TIMESTAMP_2, result.first().timestampMs)
    }

    @Test
    fun givenNoEntries_whenLoggingMessageDelete_thenEntryIsStored() = runTest(dispatcher) {
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertEquals(MESSAGE_NONCE_1, messageId)
            assertEquals(ChangeLogEventType.MESSAGE_DELETE, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
        }
    }

    @Test
    fun givenExistingMessageDelete_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        assertEquals(TIMESTAMP_2, result.first().timestampMs)
    }

    @Test
    fun givenNoEntries_whenLoggingReactionsSync_thenEntryIsStored() = runTest(dispatcher) {
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertEquals(MESSAGE_NONCE_1, messageId)
            assertEquals(ChangeLogEventType.REACTIONS_SYNC, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
        }
    }

    @Test
    fun givenExistingReactionsSync_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        assertEquals(TIMESTAMP_2, result.first().timestampMs)
    }

    @Test
    fun givenNoEntries_whenLoggingReadReceiptsSync_thenEntryIsStored() = runTest(dispatcher) {
        dao.logReadReceiptsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertEquals(MESSAGE_NONCE_1, messageId)
            assertEquals(ChangeLogEventType.READ_RECEIPT_SYNC, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
        }
    }

    @Test
    fun givenExistingReadReceiptsSync_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logReadReceiptsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logReadReceiptsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        assertEquals(TIMESTAMP_2, result.first().timestampMs)
    }

    @Test
    fun givenNoEntries_whenLoggingConversationDelete_thenEntryIsStored() = runTest(dispatcher) {
        dao.logConversationDelete(CONVERSATION_ID_1, TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertNull(messageId)
            assertEquals(ChangeLogEventType.CONVERSATION_DELETE, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
        }
    }

    @Test
    fun givenExistingMessageEntriesForConversation_whenLoggingConversationDelete_thenMessageEntriesAreCleared() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_2, TIMESTAMP_1)
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)

        dao.logConversationDelete(CONVERSATION_ID_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()
        assertEquals(1, result.size)
        assertEquals(ChangeLogEventType.CONVERSATION_DELETE, result.first().eventType)
    }

    @Test
    fun givenEntriesInOtherConversations_whenLoggingConversationDelete_thenOnlyTargetConversationEntriesAreCleared() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_2, TIMESTAMP_1)

        dao.logConversationDelete(CONVERSATION_ID_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()
        assertEquals(2, result.size)
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_2 && it.eventType == ChangeLogEventType.MESSAGE_UPSERT })
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_1 && it.eventType == ChangeLogEventType.CONVERSATION_DELETE })
    }

    @Test
    fun givenNoEntries_whenLoggingConversationClear_thenEntryIsStored() = runTest(dispatcher) {
        dao.logConversationClear(CONVERSATION_ID_1, TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertNull(messageId)
            assertEquals(ChangeLogEventType.CONVERSATION_CLEAR, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
        }
    }

    @Test
    fun givenExistingMessageEntriesForConversation_whenLoggingConversationClear_thenMessageEntriesAreCleared() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_2, TIMESTAMP_1)
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)

        dao.logConversationClear(CONVERSATION_ID_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()
        assertEquals(1, result.size)
        assertEquals(ChangeLogEventType.CONVERSATION_CLEAR, result.first().eventType)
    }

    @Test
    fun givenEntriesInOtherConversations_whenLoggingConversationClear_thenOnlyTargetConversationEntriesAreCleared() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_2, TIMESTAMP_1)

        dao.logConversationClear(CONVERSATION_ID_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()
        assertEquals(2, result.size)
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_2 && it.eventType == ChangeLogEventType.MESSAGE_UPSERT })
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_1 && it.eventType == ChangeLogEventType.CONVERSATION_CLEAR })
    }

    @Test
    fun givenNoEntries_whenGettingPendingChanges_thenEmptyListIsReturned() = runTest(dispatcher) {
        val result = dao.getPendingChanges()

        assertTrue(result.isEmpty())
    }

    @Test
    fun givenMultipleEntries_whenGettingPendingChanges_thenEntriesAreOrderedByTimestamp() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_3)
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_2, TIMESTAMP_1)
        dao.logReactionsSync(CONVERSATION_ID_2, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(3, result.size)
        assertEquals(TIMESTAMP_1, result[0].timestampMs)
        assertEquals(TIMESTAMP_2, result[1].timestampMs)
        assertEquals(TIMESTAMP_3, result[2].timestampMs)
    }

    @Test
    fun givenSameMessageInDifferentConversations_whenLogging_thenBothEntriesAreStored() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(2, result.size)
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_1 })
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_2 })
    }

    @Test
    fun givenSameMessageWithDifferentEventTypes_whenLogging_thenAllEntriesAreStored() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_3)

        val result = dao.getPendingChanges()

        assertEquals(3, result.size)
        assertTrue(result.any { it.eventType == ChangeLogEventType.MESSAGE_UPSERT })
        assertTrue(result.any { it.eventType == ChangeLogEventType.MESSAGE_DELETE })
        assertTrue(result.any { it.eventType == ChangeLogEventType.REACTIONS_SYNC })
    }

    @Test
    fun givenDifferentMessagesInSameConversation_whenLogging_thenAllEntriesAreStored() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_2, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(2, result.size)
        assertTrue(result.any { it.messageId == MESSAGE_NONCE_1 })
        assertTrue(result.any { it.messageId == MESSAGE_NONCE_2 })
    }

    private companion object {
        val CONVERSATION_ID_1 = QualifiedIDEntity("conv1", "domain.com")
        val CONVERSATION_ID_2 = QualifiedIDEntity("conv2", "domain.com")
        const val MESSAGE_NONCE_1 = "message-nonce-1"
        const val MESSAGE_NONCE_2 = "message-nonce-2"
        const val TIMESTAMP_1 = 1000L
        const val TIMESTAMP_2 = 2000L
        const val TIMESTAMP_3 = 3000L
    }
}
