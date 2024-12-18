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

import app.cash.turbine.test
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
    fun givenAlreadyExistingMessageDraft_whenUpsertingTextChange_thenItShouldBeProperlyUpdatedInDb() = runTest {
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
    fun givenAlreadyExistingMessageDraft_whenUpsertingDifferentQuotedMessageId_thenItShouldBeProperlyUpdatedInDb() = runTest {
        // given
        val newQuotedMessageId = "newQuotedMessageId"
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // when
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(quotedMessageId = newQuotedMessageId))

        // then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertNotNull(result)
        assertEquals(newQuotedMessageId, result.quotedMessageId)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenUpsertingNullQuotedMessageId_thenItShouldBeProperlyUpdatedInDb() = runTest {
        // given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // when
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(quotedMessageId = null))

        // then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertNotNull(result)
        assertNull(result.quotedMessageId)
    }

    @Test
    fun givenAlreadyExistingMessageDraftWithoutQuotedMessageId_whenUpsertingQuotedMessageId_thenItShouldBeProperlyUpdatedInDb() = runTest{
        // given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(quotedMessageId = null))

        // when
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertNotNull(result)
        assertEquals(MESSAGE_DRAFT.quotedMessageId, result.quotedMessageId)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenUpsertingDifferentEditMessageId_thenItShouldBeProperlyUpdatedInDb() = runTest {
        // given
        val newEditMessageId = "newEditMessageId"
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // when
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(editMessageId = newEditMessageId))

        // then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertNotNull(result)
        assertEquals(newEditMessageId, result.editMessageId)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenUpsertingNullEditMessageId_thenItShouldBeProperlyUpdatedInDb() = runTest {
        // given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // when
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(editMessageId = null))

        // then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertNotNull(result)
        assertNull(result.editMessageId)
    }

    @Test
    fun givenAlreadyExistingMessageDraft_whenUpsertingEmptyMentionList_thenItShouldBeProperlyUpdatedInDb() = runTest {
        // given
        insertInitialData()
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // when
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT.copy(selectedMentionList = emptyList()))

        // then
        val result = messageDraftDAO.getMessageDraft(conversationEntity1.id)
        assertNotNull(result)
        assertEquals(emptyList(), result.selectedMentionList)
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

    @Test
    fun givenMessageIsRemoved_whenUpsertingDraft_thenItShouldIgnore() = runTest {
        // Given
        insertInitialData()
        messageDAO.deleteMessage("editMessageId", conversationEntity1.id)

        // When
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // Then
        val result = messageDraftDAO.getMessageDraft(MESSAGE_DRAFT.conversationId)
        assertEquals(null, result)
    }

    @Test
    fun givenConversationIsRemoved_whenUpsertingDraft_thenItShouldIgnore() = runTest {
        // Given
        insertInitialData()
        conversationDAO.deleteConversationByQualifiedID(conversationEntity1.id)

        // When
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // Then
        val result = messageDraftDAO.getMessageDraft(MESSAGE_DRAFT.conversationId)
        assertEquals(null, result)
    }

    @Test
    fun givenQuotedMessageIsRemoved_whenUpsertingDraft_thenItShouldIgnore() = runTest {
        // Given
        insertInitialData()
        messageDAO.deleteMessage("quotedMessageId", conversationEntity1.id)

        // When
        messageDraftDAO.upsertMessageDraft(MESSAGE_DRAFT)

        // Then
        val result = messageDraftDAO.getMessageDraft(MESSAGE_DRAFT.conversationId)
        assertEquals(null, result)
    }

    @Test
    fun givenSavedDraft_whenUpsertingTheSameExactDraft_thenItShouldIgnoreAndNotNotifyOtherQueries() = runTest {
        // Given
        insertInitialData()
        val draft = MESSAGE_DRAFT
        messageDraftDAO.upsertMessageDraft(draft)

        messageDraftDAO.observeMessageDrafts().test {
            val initialValue = awaitItem()
            assertEquals(listOf(draft), initialValue)

            // When
            messageDraftDAO.upsertMessageDraft(draft) // the same exact draft is being saved again

            // Then
            expectNoEvents() // other query should not be notified
        }
    }

    @Test
    fun givenSavedDraft_whenUpsertingUpdatedDraft_thenItShouldBeSavedAndOtherQueriesShouldBeUpdated() = runTest {
        // Given
        insertInitialData()
        val draft = MESSAGE_DRAFT
        val updatedDraft = MESSAGE_DRAFT.copy(text = MESSAGE_DRAFT.text + " :)")
        messageDraftDAO.upsertMessageDraft(draft)

        messageDraftDAO.observeMessageDrafts().test {
            val initialValue = awaitItem()
            assertEquals(listOf(draft), initialValue)

            // When
            messageDraftDAO.upsertMessageDraft(updatedDraft) // updated draft is being saved that should replace the old one

            // Then
            val updatedValue = awaitItem() // other query should be notified
            assertEquals(listOf(updatedDraft), updatedValue)
        }

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
                id = "newEditMessageId",
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
        messageDAO.insertOrIgnoreMessage(
            newRegularMessageEntity(
                id = "newQuotedMessageId",
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
