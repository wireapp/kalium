/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import app.cash.sqldelight.async.coroutines.awaitAsOne
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
import kotlin.test.assertFalse

class RequiredMessageWritesTest : BaseDatabaseTest() {
    @Test
    fun baseContentAndUnreadFailuresRollBackInsteadOfReturningSuccess() = runTest(dispatcher) {
        val userId = UserIDEntity("required-writes", "wire.test")
        val database = createDatabase(userId, encryptedDBSecret, true)
        try {
            val conversation = newConversationEntity("required-writes")
            val sender = newUserEntity("sender")
            database.conversationDAO.insertConversation(conversation)
            database.userDAO.upsertUser(sender)
            val message = newRegularMessageEntity(
                id = "text", conversationId = conversation.id, senderUserId = sender.id,
                content = MessageEntityContent.Text("required text"), date = Instant.parse("2026-09-05T12:00:00Z"),
            )
            for (table in listOf("Message", "MessageTextContent", "UnreadEvent")) {
                database.sqlDriver.execute(null, "CREATE TRIGGER fail_required BEFORE INSERT ON $table BEGIN SELECT RAISE(ABORT, 'write failed'); END", 0)
                assertFails { database.messageDAO.insertOrIgnoreMessage(message) }
                assertFalse(database.database.messagesQueries.messageExists(message.id, conversation.id).awaitAsOne())
                database.sqlDriver.execute(null, "DROP TRIGGER fail_required", 0)
            }
            database.messageDAO.insertOrIgnoreMessage(message)
            database.messageDAO.insertOrIgnoreMessage(message.copy(content = MessageEntityContent.Text("duplicate")))
            val stored = database.messageDAO.getMessageById(message.id, conversation.id) as MessageEntity.Regular
            assertEquals("required text", (stored.content as MessageEntityContent.Text).messageBody)
        } finally {
            database.close()
        }
    }
}
