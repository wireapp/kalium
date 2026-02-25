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
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.reaction.ReactionDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteBackupChangeLogDAOTest : BaseDatabaseTest() {

    private lateinit var dao: RemoteBackupChangeLogDAO
    private lateinit var userDAO: UserDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var messageDAO: MessageDAO
    private lateinit var reactionDAO: ReactionDAO
    private lateinit var receiptDAO: ReceiptDAO
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        dao = db.remoteBackupChangeLogDAO
        userDAO = db.userDAO
        conversationDAO = db.conversationDAO
        messageDAO = db.messageDAO
        receiptDAO = db.receiptDAO
        reactionDAO = db.reactionDAO
    }

    @Test
    fun givenNoEntries_whenLoggingMessageUpsert_thenEntryIsStored() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(CONVERSATION_ID_1, conversationId)
            assertEquals(MESSAGE_NONCE_1, messageId)
            assertEquals(ChangeLogEventType.MESSAGE_UPSERT, eventType)
            assertEquals(TIMESTAMP_1, timestampMs)
            assertEquals(MESSAGE_TIMESTAMP_1, messageTimestampMs)
        }
    }

    @Test
    fun givenExistingMessageUpsert_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2, MESSAGE_TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(TIMESTAMP_2, timestampMs)
            assertEquals(MESSAGE_TIMESTAMP_2, messageTimestampMs)
        }
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
            assertEquals(TIMESTAMP_1, messageTimestampMs)
        }
    }

    @Test
    fun givenExistingMessageDelete_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(TIMESTAMP_2, timestampMs)
            assertEquals(TIMESTAMP_2, messageTimestampMs)
        }
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
            assertEquals(TIMESTAMP_1, messageTimestampMs)
        }
    }

    @Test
    fun givenExistingReactionsSync_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(TIMESTAMP_2, timestampMs)
            assertEquals(TIMESTAMP_2, messageTimestampMs)
        }
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
            assertEquals(TIMESTAMP_1, messageTimestampMs)
        }
    }

    @Test
    fun givenExistingReadReceiptsSync_whenLoggingAgain_thenTimestampIsUpdated() = runTest(dispatcher) {
        dao.logReadReceiptsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logReadReceiptsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals(TIMESTAMP_2, timestampMs)
            assertEquals(TIMESTAMP_2, messageTimestampMs)
        }
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
            assertEquals(TIMESTAMP_1, messageTimestampMs)
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
            assertEquals(TIMESTAMP_1, messageTimestampMs)
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
    fun givenMessageUpsertEvent_whenGettingPayloadBatch_thenMessagePayloadIsIncluded() = runTest(dispatcher) {
        val senderId = QualifiedIDEntity("sender", "wire.com")
        userDAO.upsertUser(newUserEntity(selfUserId, "self"))
        userDAO.upsertUser(newUserEntity(senderId, "sender"))
        conversationDAO.insertConversation(newConversationEntity(CONVERSATION_ID_1))
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = MESSAGE_NONCE_1,
                content = com.wire.kalium.persistence.dao.message.MessageEntityContent.Text("hello-sync"),
                conversationId = CONVERSATION_ID_1,
                senderUserId = senderId,
                status = MessageEntity.Status.SENT
            )
        )
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)

        val result = dao.getLastPendingChangesWithPayload(limit = 1).first()
        val upsertEvent = assertIs<ChangeLogSyncEvent.MessageUpsert>(result)
        val payload = assertIs<SyncableMessagePayloadEntity.Text>(assertNotNull(upsertEvent.message))

        assertEquals(ChangeLogEventType.MESSAGE_UPSERT, upsertEvent.change.eventType)
        assertEquals(CONVERSATION_ID_1, upsertEvent.conversationId)
        assertEquals(MESSAGE_NONCE_1, upsertEvent.messageId)
        assertEquals(MessageEntity.ContentType.TEXT, payload.contentType)
        assertEquals("hello-sync", payload.text)
    }

    @Test
    fun givenReactionAndReadReceiptEvents_whenGettingPayloadBatch_thenAggregatedPayloadsAreIncluded() = runTest(dispatcher) {
        val senderId = QualifiedIDEntity("sender", "wire.com")
        val reactorId = QualifiedIDEntity("reactor", "wire.com")
        userDAO.upsertUser(newUserEntity(selfUserId, "self"))
        userDAO.upsertUser(newUserEntity(senderId, "sender"))
        userDAO.upsertUser(newUserEntity(reactorId, "reactor"))
        conversationDAO.insertConversation(newConversationEntity(CONVERSATION_ID_1))
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = MESSAGE_NONCE_1,
                content = com.wire.kalium.persistence.dao.message.MessageEntityContent.Text("event-data"),
                conversationId = CONVERSATION_ID_1,
                senderUserId = senderId,
                status = MessageEntity.Status.SENT
            )
        )

        reactionDAO.insertReaction(MESSAGE_NONCE_1, CONVERSATION_ID_1, senderId, Instant.fromEpochMilliseconds(100), "👍")
        reactionDAO.insertReaction(MESSAGE_NONCE_1, CONVERSATION_ID_1, reactorId, Instant.fromEpochMilliseconds(101), "😂")
        receiptDAO.insertReceipts(
            userId = reactorId,
            conversationId = CONVERSATION_ID_1,
            date = Instant.fromEpochMilliseconds(102),
            type = ReceiptTypeEntity.READ,
            messageIds = listOf(MESSAGE_NONCE_1)
        )

        dao.logReactionsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1)
        dao.logReadReceiptsSync(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_2)

        val result = dao.getLastPendingChangesWithPayload(limit = 2)
        val reactionsEvent = assertIs<ChangeLogSyncEvent.ReactionsSync>(result.first { it.change.eventType == ChangeLogEventType.REACTIONS_SYNC })
        val receiptsEvent = assertIs<ChangeLogSyncEvent.ReadReceiptSync>(
            result.first { it.change.eventType == ChangeLogEventType.READ_RECEIPT_SYNC }
        )
        assertEquals(CONVERSATION_ID_1, reactionsEvent.conversationId)
        assertEquals(MESSAGE_NONCE_1, reactionsEvent.messageId)
        assertEquals(CONVERSATION_ID_1, receiptsEvent.conversationId)
        assertEquals(MESSAGE_NONCE_1, receiptsEvent.messageId)

        val reactions = reactionsEvent.reactions
        assertEquals(2, reactions.reactionsByUser.size)
        assertTrue(reactions.reactionsByUser.any { it.userId == senderId && it.emojis.contains("👍") })
        assertTrue(reactions.reactionsByUser.any { it.userId == reactorId && it.emojis.contains("😂") })

        val readReceipts = receiptsEvent.readReceipts
        assertEquals(1, readReceipts.receipts.size)
        assertEquals(reactorId, readReceipts.receipts.first().userId)
    }

    @Test
    fun givenPagedEventsAcrossConversations_whenGettingConversationLastRead_thenReturnsOneLastReadPerConversationInPage() = runTest(dispatcher) {
        val conv3 = QualifiedIDEntity("conv3", "domain.com")
        val conv1LastRead = Instant.fromEpochMilliseconds(11)
        val conv2LastRead = Instant.fromEpochMilliseconds(22)
        val conv3LastRead = Instant.fromEpochMilliseconds(33)
        conversationDAO.insertConversation(newConversationEntity(CONVERSATION_ID_1, lastReadDate = conv1LastRead))
        conversationDAO.insertConversation(newConversationEntity(CONVERSATION_ID_2, lastReadDate = conv2LastRead))
        conversationDAO.insertConversation(newConversationEntity(conv3, lastReadDate = conv3LastRead))

        dao.logMessageUpsert(conv3, MESSAGE_NONCE_1, 500, 500)
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_1, 2500, 2500)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, 2000, 2000)
        dao.logMessageDelete(CONVERSATION_ID_1, MESSAGE_NONCE_2, 3000)

        val result = dao.getConversationLastReadForLastPendingChanges(limit = 2)

        assertEquals(
            listOf(
                ConversationLastReadSyncEntity(
                    conversationId = CONVERSATION_ID_1,
                    lastReadDate = conv1LastRead
                ),
                ConversationLastReadSyncEntity(
                    conversationId = CONVERSATION_ID_2,
                    lastReadDate = conv2LastRead
                ),
            ),
            result
        )
    }

    @Test
    fun givenPagedEvents_whenGettingAndObservingBatch_thenEventsAndLastReadsAreReturnedTogether() = runTest(dispatcher) {
        val conv1LastRead = Instant.fromEpochMilliseconds(1100)
        val conv2LastRead = Instant.fromEpochMilliseconds(2200)
        conversationDAO.insertConversation(newConversationEntity(CONVERSATION_ID_1, lastReadDate = conv1LastRead))
        conversationDAO.insertConversation(newConversationEntity(CONVERSATION_ID_2, lastReadDate = conv2LastRead))
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
        dao.logMessageDelete(CONVERSATION_ID_2, MESSAGE_NONCE_2, TIMESTAMP_2)

        val snapshot = dao.getLastPendingChangesBatch(limit = 2)
        val observed = dao.observeLastPendingChangesBatch(limit = 2).first()

        assertEquals(
            dao.getLastPendingChangesWithPayload(limit = 2),
            snapshot.events
        )
        assertEquals(
            dao.getConversationLastReadForLastPendingChanges(limit = 2),
            snapshot.conversationLastReads
        )
        assertEquals(snapshot, observed)
    }

    @Test
    fun givenMultipleEntries_whenGettingPendingChanges_thenEntriesAreOrderedByTimestamp() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_3, MESSAGE_TIMESTAMP_3)
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
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_1, TIMESTAMP_2, MESSAGE_TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(2, result.size)
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_1 })
        assertTrue(result.any { it.conversationId == CONVERSATION_ID_2 })
    }

    @Test
    fun givenSameMessageWithDifferentEventTypes_whenLogging_thenAllEntriesAreStored() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
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
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_2, TIMESTAMP_2, MESSAGE_TIMESTAMP_2)

        val result = dao.getPendingChanges()

        assertEquals(2, result.size)
        assertTrue(result.any { it.messageId == MESSAGE_NONCE_1 })
        assertTrue(result.any { it.messageId == MESSAGE_NONCE_2 })
    }

    @Test
    fun givenMessageAndEventTimestampsAreTied_whenGettingPendingChanges_thenOrderingIsDeterministic() = runTest(dispatcher) {
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_2, TIMESTAMP_1, TIMESTAMP_1)
        dao.logMessageDelete(CONVERSATION_ID_2, MESSAGE_NONCE_2, TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_2, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_2, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_1)
        dao.logMessageUpsert(CONVERSATION_ID_1, MESSAGE_NONCE_1, TIMESTAMP_1, MESSAGE_TIMESTAMP_2)

        val result = dao.getPendingChanges()
        assertEquals(
            listOf(
                Triple(CONVERSATION_ID_1, MESSAGE_NONCE_2, ChangeLogEventType.MESSAGE_UPSERT),
                Triple(CONVERSATION_ID_2, MESSAGE_NONCE_1, ChangeLogEventType.MESSAGE_UPSERT),
                Triple(CONVERSATION_ID_1, MESSAGE_NONCE_1, ChangeLogEventType.MESSAGE_UPSERT),
                Triple(CONVERSATION_ID_2, MESSAGE_NONCE_2, ChangeLogEventType.MESSAGE_UPSERT),
                Triple(CONVERSATION_ID_2, MESSAGE_NONCE_2, ChangeLogEventType.MESSAGE_DELETE),
            ),
            result.map { Triple(it.conversationId, it.messageId, it.eventType) }
        )
        assertEquals(
            listOf(
                Pair(TIMESTAMP_1, MESSAGE_TIMESTAMP_1),
                Pair(TIMESTAMP_1, MESSAGE_TIMESTAMP_1),
                Pair(TIMESTAMP_1, MESSAGE_TIMESTAMP_2),
                Pair(TIMESTAMP_1, TIMESTAMP_1),
                Pair(TIMESTAMP_1, TIMESTAMP_1),
            ),
            result.map { Pair(it.timestampMs, it.messageTimestampMs) }
        )
    }

    private companion object {
        val CONVERSATION_ID_1 = QualifiedIDEntity("conv1", "domain.com")
        val CONVERSATION_ID_2 = QualifiedIDEntity("conv2", "domain.com")
        const val MESSAGE_NONCE_1 = "message-nonce-1"
        const val MESSAGE_NONCE_2 = "message-nonce-2"
        const val TIMESTAMP_1 = 1000L
        const val TIMESTAMP_2 = 2000L
        const val TIMESTAMP_3 = 3000L
        const val MESSAGE_TIMESTAMP_1 = 100L
        const val MESSAGE_TIMESTAMP_2 = 200L
        const val MESSAGE_TIMESTAMP_3 = 300L
    }

}
