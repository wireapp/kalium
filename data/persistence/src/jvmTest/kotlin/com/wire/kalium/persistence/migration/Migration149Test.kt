/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.migration

import app.cash.sqldelight.async.coroutines.awaitMigrate
import app.cash.sqldelight.db.QueryResult
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.ReceivedMessage
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Migration149Test : BaseDatabaseTest() {
    @Test
    fun `both receipt schema versions upgrade without losing events or required message effects`() = runTest(dispatcher) {
        for (oldVersion in listOf(148L, 149L)) {
            val database = createDatabase(UserIDEntity("upgrade-$oldVersion", "wire.test"), encryptedDBSecret, true)
            try {
                var receiptSchema = File("src/commonMain/db_user/migrations/147.sqm").readText()
                if (oldVersion == 149L) {
                    for (column in listOf("message_id", "conversation_value", "conversation_domain")) {
                        receiptSchema = receiptSchema.replace("$column TEXT NOT NULL", "$column TEXT")
                    }
                }
                database.sqlDriver.execute(null, receiptSchema, 0)
                database.sqlDriver.execute(null, """
                    INSERT INTO NotificationReceipts VALUES
                    ('proteus', 'pending', 'old', NULL, 'text', 'conversation', 'wire.test', 0),
                    ('mls', 'completed', 'other', NULL, 'text', 'conversation', 'wire.test', 1)
                """.trimIndent(), 0)
                val conversation = newConversationEntity("upgrade")
                val sender = newUserEntity("sender")
                database.conversationDAO.insertConversation(conversation)
                database.userDAO.upsertUser(sender)
                val message = newRegularMessageEntity(
                    id = "text", conversationId = conversation.id, senderUserId = sender.id, content = MessageEntityContent.Text("stored"),
                )
                database.eventDAO.insertEvents(listOf(NewEventEntity("pending", "[]", false, false)))
                database.messageDAO.insertReceivedMessages(listOf(ReceivedMessage(message, true, true)))
                val before = database.messageDAO.getMessageById("text", conversation.id)
                // The fixture uses the current schema; replay only the receipt migrations under test.
                UserDatabase.Schema.awaitMigrate(database.sqlDriver, oldVersion, 150L)
                assertEquals(before, database.messageDAO.getMessageById("text", conversation.id))
                assertFalse(checkNotNull(database.eventDAO.getEventById("pending")).isProcessed)
                assertEquals(listOf("text"), database.pendingDeliveryConfirmationDAO.getPendingAfter(0, 10).map { it.messageId })
                database.sqlDriver.executeQuery(null, "SELECT count(*) FROM sqlite_master WHERE name = 'NotificationReceipts'", { cursor ->
                    cursor.next()
                    assertEquals(0L, cursor.getLong(0))
                    QueryResult.Unit
                }, 0)
            } finally {
                database.close()
            }
        }
    }
}
