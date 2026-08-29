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

import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days

class MessageMetadataDAOTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var messageMetaDataDAO: MessageMetadataDAO
    private lateinit var userDatabase: UserDatabaseBuilder

    private val conversationEntity1 = newConversationEntity("Test1")
    private val userEntity1 = newUserEntity("userEntity1")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        userDatabase = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = userDatabase.messageDAO
        conversationDAO = userDatabase.conversationDAO
        userDAO = userDatabase.userDAO
        messageMetaDataDAO = userDatabase.messageMetaDataDAO
    }


    @Test
    fun givenMessage_whenGettingOriginalSender_thenReturnItsId() = runTest {
        val messageId = "testMessageId"
        val originalUser = userEntity1

        conversationDAO.insertConversation(conversationEntity1)
        userDAO.upsertUser(originalUser)

        val originalMessage = newRegularMessageEntity(
            id = messageId,
            conversationId = conversationEntity1.id,
            senderUserId = originalUser.id,
            senderClientId = "initialClientId",
            content = MessageEntityContent.Text("Howdy"),
            date = Instant.DISTANT_FUTURE - 5.days,
            visibility = MessageEntity.Visibility.VISIBLE
        )

        messageDAO.insertOrIgnoreMessage(originalMessage)


        messageMetaDataDAO.originalSenderId(originalMessage.conversationId, originalMessage.id).also {
            assertEquals(originalUser.id, it)
        }
        messageMetaDataDAO.originalSenderIdForCompositeEdit(originalMessage.conversationId, originalMessage.id).also {
            assertEquals(originalUser.id, it)
        }
    }

    @Test
    fun givenMessageWithoutUserDetails_whenGettingSenderForCompositeEdit_thenReturnNull() = runTest {
        val messageId = "orphaned-sender-message"
        val originalUser = userEntity1
        val originalMessage = newRegularMessageEntity(
            id = messageId,
            conversationId = conversationEntity1.id,
            senderUserId = originalUser.id,
            senderClientId = "initialClientId",
            content = MessageEntityContent.Text("Howdy"),
            date = Instant.DISTANT_FUTURE - 5.days,
            visibility = MessageEntity.Visibility.VISIBLE,
        )

        conversationDAO.insertConversation(conversationEntity1)
        userDAO.upsertUser(originalUser)
        messageDAO.insertOrIgnoreMessage(originalMessage)

        userDatabase.database.usersQueries.transaction {
            userDatabase.sqlDriver.execute(null, "PRAGMA defer_foreign_keys = ON", 0).await()
            userDatabase.database.usersQueries.deleteUser(originalUser.id)

            assertEquals(
                originalUser.id,
                userDatabase.database.messageMetadataQueries
                    .originalSenderId(originalMessage.conversationId, originalMessage.id)
                    .awaitAsOneOrNull(),
            )
            assertNull(
                userDatabase.database.messageMetadataQueries
                    .originalSenderIdForCompositeEdit(originalMessage.conversationId, originalMessage.id)
                    .awaitAsOneOrNull(),
            )
            rollback()
        }
    }

    @Test
    fun givenNoMessage_whenGettingOriginalSender_thenReturnNull() = runTest {
        val messageId = "testMessageId"

        messageMetaDataDAO.originalSenderId(conversationEntity1.id, messageId).also {
            assertNull(it)
        }
        messageMetaDataDAO.originalSenderIdForCompositeEdit(conversationEntity1.id, messageId).also {
            assertNull(it)
        }
    }
}
