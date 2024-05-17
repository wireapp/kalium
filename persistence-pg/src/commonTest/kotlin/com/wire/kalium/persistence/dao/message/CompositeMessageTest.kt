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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompositeMessageTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var compositeMessageDAO: CompositeMessageDAO

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
        compositeMessageDAO = db.compositeMessageDAO
    }

    @Test
    fun givenSuccess_whenInsertingCompositeMessage_thenMessageCanBeRetrieved() = runTest {
        val conversation = conversationEntity1
        conversationDAO.insertConversation(conversation)
        userDAO.upsertUser(userEntity1)

        val compositeMessage = newRegularMessageEntity().copy(
            senderUserId = userEntity1.id,
            conversationId = conversation.id,
            content = MessageEntityContent.Composite(
                MessageEntityContent.Text("text"),
                listOf(
                    ButtonEntity("text", "id", false)
                )
            )
        )

        messageDAO.insertOrIgnoreMessages(listOf(compositeMessage))

        messageDAO.getMessageById(compositeMessage.id, compositeMessage.conversationId).also {
            assertNotNull(it)
            assertIs<MessageEntityContent.Composite>(it.content)
            assertEquals("text", (it.content as MessageEntityContent.Composite).text?.messageBody)
            assertEquals(1, (it.content as MessageEntityContent.Composite).buttonList.size)
            assertEquals("text", (it.content as MessageEntityContent.Composite).buttonList[0].text)
            assertEquals("id", (it.content as MessageEntityContent.Composite).buttonList[0].id)
        }
    }

    @Test
    fun givenCompositeMessage_whenMarkingButtonAsSelected_thenOnlyOneItIsMarked() = runTest {
        val conversation = conversationEntity1
        conversationDAO.insertConversation(conversation)
        userDAO.upsertUser(userEntity1)

        val compositeMessage = newRegularMessageEntity().copy(
            senderUserId = userEntity1.id,
            conversationId = conversation.id,
            content = MessageEntityContent.Composite(
                MessageEntityContent.Text("text"),
                listOf(
                    ButtonEntity("text1", "id1", false),
                    ButtonEntity("tex2", "id2", false),
                    ButtonEntity("tex3", "id3", false),
                    ButtonEntity("tex4", "id4", false)
                )
            )
        )
        messageDAO.insertOrIgnoreMessages(listOf(compositeMessage))

        compositeMessageDAO.markAsSelected(compositeMessage.id, compositeMessage.conversationId, "id2")

        messageDAO.getMessageById(compositeMessage.id, compositeMessage.conversationId).also {
            assertEquals(4, (it?.content as MessageEntityContent.Composite).buttonList.size)
            assertTrue { (it.content as MessageEntityContent.Composite).buttonList[1].isSelected }
        }

        compositeMessageDAO.markAsSelected(compositeMessage.id, compositeMessage.conversationId, "id4")

        messageDAO.getMessageById(compositeMessage.id, compositeMessage.conversationId).also {
            assertEquals(4, (it?.content as MessageEntityContent.Composite).buttonList.size)
            assertFalse { (it.content as MessageEntityContent.Composite).buttonList[1].isSelected }
            assertTrue { (it.content as MessageEntityContent.Composite).buttonList[3].isSelected }
        }
    }

    @Test
    fun givenCompositeMessageWithSelection_whenResetSelection_thenSelectionIsFalse() = runTest {
        val conversation = conversationEntity1
        conversationDAO.insertConversation(conversation)
        userDAO.upsertUser(userEntity1)

        val compositeMessage = newRegularMessageEntity().copy(
            senderUserId = userEntity1.id,
            conversationId = conversation.id,
            content = MessageEntityContent.Composite(
                MessageEntityContent.Text("text"),
                listOf(
                    ButtonEntity("text1", "id1", false),
                    ButtonEntity("tex2", "id2", false),
                    ButtonEntity("tex3", "id3", false),
                    ButtonEntity("tex4", "id4", false)
                )
            )
        )
        messageDAO.insertOrIgnoreMessages(listOf(compositeMessage))

        compositeMessageDAO.markAsSelected(compositeMessage.id, compositeMessage.conversationId, "id2")
        messageDAO.getMessageById(compositeMessage.id, compositeMessage.conversationId).also {
            assertEquals(4, (it?.content as MessageEntityContent.Composite).buttonList.size)
            assertTrue { (it.content as MessageEntityContent.Composite).buttonList[1].isSelected }
        }

        compositeMessageDAO.resetSelection(compositeMessage.id, compositeMessage.conversationId)
        messageDAO.getMessageById(compositeMessage.id, compositeMessage.conversationId).also {
            assertEquals(4, (it?.content as MessageEntityContent.Composite).buttonList.size)
            assertFalse { (it.content as MessageEntityContent.Composite).buttonList[1].isSelected }
        }
    }
}
