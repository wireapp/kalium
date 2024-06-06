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

package com.wire.kalium.persistence.dao.message.draft

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.utils.stubs.TestStubs.conversationEntity1
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("LargeClass")
class MessageDraftDAOTest : BaseDatabaseTest() {

    private lateinit var messageDraftDAO: MessageDraftDAO
    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val userEntity1 = newUserEntity()
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDraftDAO = db.messageDraftDAO
        conversationDAO = db.conversationDAO
        messageDAO = db.messageDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenConversationId_whenSavingMessageDraft_thenItShouldBeProperlySavedInDb() = runTest {
        // Given
        insertInitialData()

        // When
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // Then
        val result = messageDraftDAO.getMessageDraft(MESSAGE_DRAFT.conversationId)
        assertEquals(MESSAGE_DRAFT, result)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenUpserting_thenItShouldBeProperlyUpdatedInDb() = runTest {
        // Given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(text = "@John I need"))

        // When
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // Then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertEquals(MESSAGE_DRAFT, result)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenDeletingIt_thenItShouldBeProperlyRemovedInDb() = runTest {
        // Given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertEquals(MESSAGE_DRAFT, result)

        // When
        messageDraftDAO.removeMessageDraft(conversationEntity1.id)

        // Then
        val removedResult = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertEquals(null, removedResult)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenConversationIsRemoved_thenItShouldBeProperlyRemovedInDb() = runTest {
        // Given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertEquals(MESSAGE_DRAFT, result)

        // When
        conversationDAO.deleteConversationByQualifiedID(conversationEntity1.id)

        // Then
        val removedResult = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertEquals(null, removedResult)
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(userEntity1))
        conversationDAO.insertConversation(conversationEntity1)
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "editMessageId",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id
            )
        )
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "quotedMessageId",
                conversationId = conversationEntity1.id,
                senderUserId = userEntity1.id
            )
        )
    }

    companion object {
        val MESSAGE_DRAFT = MessageDraftEntity(
            conversationId = conversationEntity1.id,
            text = "@John I need help",
            editMessageId = "editMessageId",
            quotedMessageId = "quotedMessageId",
            selectedMentionList = listOf(
                MessageEntity.Mention(
                    0,
                    4,
                    QualifiedIDEntity("userId", "domain")
                )
            )
        )
    }
}
