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

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
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

    private val conversationEntity1 = newConversationEntity("Test1")
    private val userEntity1 = newUserEntity("userEntity1")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        messageMetaDataDAO = db.messageMetaDataDAO
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
    }

    @Test
    fun givenNoMessagee_whenGettingOriginalSender_thenReturnNull() = runTest {
        val messageId = "testMessageId"

        messageMetaDataDAO.originalSenderId(conversationEntity1.id, messageId).also {
            assertNull(it)
        }
    }
}
