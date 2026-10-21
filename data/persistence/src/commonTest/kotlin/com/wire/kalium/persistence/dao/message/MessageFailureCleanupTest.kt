/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageFailureCleanupTest : BaseDatabaseTest() {
    @Test
    fun receiptBetweenCleanupReadAndWriteCannotBeOverwritten() = runTest {
        val self = UserIDEntity("cleanup-race", "example.com")
        deleteDatabase(self)
        val database = createDatabase(self, encryptedDBSecret, true)
        try {
            val sender = newUserEntity("sender")
            val conversation = newConversationEntity("cleanup")
            database.userDAO.upsertUser(sender)
            database.conversationDAO.insertConversation(conversation)
            for (status in listOf(MessageEntity.Status.SENT, MessageEntity.Status.DELIVERED, MessageEntity.Status.READ)) {
                for (failure in listOf(MessageEntity.Status.FAILED, MessageEntity.Status.FAILED_REMOTELY)) {
                    val message = newRegularMessageEntity(
                        id = "$status-$failure", conversationId = conversation.id, senderUserId = sender.id,
                    )
                    database.messageDAO.insertOrIgnoreMessage(message)
                    // Cleanup has read PENDING; a receipt then commits before its status write.
                    assertEquals(MessageEntity.Status.PENDING, database.messageDAO.getMessageById(message.id, conversation.id)?.status)
                    database.messageDAO.updateMessageStatus(status, message.id, conversation.id)
                    database.messageDAO.updateMessageStatusIfNotAcknowledged(failure, message.id, conversation.id)
                    assertEquals(status, database.messageDAO.getMessageById(message.id, conversation.id)?.status)
                }
            }
            val unfinished = newRegularMessageEntity(id = "unfinished", conversationId = conversation.id, senderUserId = sender.id)
            database.messageDAO.insertOrIgnoreMessage(unfinished)
            database.messageDAO.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, unfinished.id, conversation.id)
            assertEquals(MessageEntity.Status.FAILED, database.messageDAO.getMessageById(unfinished.id, conversation.id)?.status)
        } finally {
            database.close()
            deleteDatabase(self)
        }
    }
}
