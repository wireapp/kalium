/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceivedMessagesTest : BaseDatabaseTest() {
    @Test
    fun requiredEffectsAreAtomicAndReplayDoesNotRecreateReadOrAcknowledgedWork() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("received", "wire.test"), encryptedDBSecret, true)
        try {
            val conversation = newConversationEntity("received")
            val sender = newUserEntity("sender")
            database.conversationDAO.insertConversation(conversation)
            database.userDAO.upsertUser(sender)
            val message = newRegularMessageEntity(
                id = "text", conversationId = conversation.id, senderUserId = sender.id,
                content = MessageEntityContent.Text("required text"), date = Instant.parse("2026-09-05T12:00:00Z"),
            )
            val effects = listOf(ReceivedMessage(message, true, true))
            for (table in EFFECT_TABLES) {
                database.sqlDriver.execute(null, "CREATE TRIGGER fail_effect BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'failed'); END", 0)
                assertFails { database.messageDAO.insertReceivedMessages(effects) }
                assertNull(database.messageDAO.getMessageById(message.id, conversation.id))
                assertTrue(database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
                assertEquals(conversation.lastModifiedDate, database.conversationDAO.getConversationById(conversation.id)?.lastModifiedDate)
                database.sqlDriver.execute(null, "DROP TRIGGER fail_effect", 0)
            }
            database.messageDAO.insertReceivedMessages(effects)
            assertEquals(listOf(message.id), database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
            val stored = database.messageDAO.getMessageById(message.id, conversation.id)
            database.sqlDriver.execute(null, "DELETE FROM PendingDeliveryConfirmations", 0)
            database.sqlDriver.execute(null, "DELETE FROM UnreadEvent", 0)
            database.sqlDriver.execute(null, "UPDATE Conversation SET last_modified_date = 2000000000000, last_notified_date = 2000000000000", 0)
            val afterRead = database.conversationDAO.getConversationById(conversation.id)
            for (table in EFFECT_TABLES) {
                database.sqlDriver.execute(null, "CREATE TRIGGER repeat_$table BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'repeated'); END", 0)
            }
            database.sqlDriver.execute(null, "CREATE TRIGGER repeat_order BEFORE UPDATE ON Conversation BEGIN SELECT RAISE(ABORT, 'repeated'); END", 0)
            database.messageDAO.insertReceivedMessages(effects)
            assertEquals(afterRead, database.conversationDAO.getConversationById(conversation.id))
            assertTrue(database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).isEmpty())
            assertEquals(stored, database.messageDAO.getMessageById(message.id, conversation.id))
        } finally {
            database.close()
        }
    }

    private companion object {
        val EFFECT_TABLES = listOf("Message", "MessageTextContent", "UnreadEvent", "PendingDeliveryConfirmations")
    }
}
