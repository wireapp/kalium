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

package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ReactionDAOTest : BaseDatabaseTest() {

    private lateinit var reactionDAO: ReactionDAO
    private lateinit var userDAO: UserDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var messageDAO: MessageDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER_ID)
        val db = createDatabase(SELF_USER_ID, encryptedDBSecret, true)
        reactionDAO = db.reactionDAO
        userDAO = db.userDAO
        conversationDAO = db.conversationDAO
        messageDAO = db.messageDAO
    }

    @Test
    fun givenAnReactionIsInserted_whenGettingReactions_thenTheReactionShouldBeReturned() = runTest {
        // Given
        insertTestUsers()
        conversationDAO.insertConversation(TEST_CONVERSATION)
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE)
        val expectedReaction = "🐻"

        reactionDAO.insertReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, "date", expectedReaction)

        // When
        val result = reactionDAO.getReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID)

        // Then
        assertContentEquals(setOf(expectedReaction), result.sorted())
    }

    @Test
    fun givenAnReactionIsInsertedAndThenDeleted_whenGettingReactions_thenTheReactionShouldNotBeReturned() = runTest {
        // Given
        insertTestUsers()
        conversationDAO.insertConversation(TEST_CONVERSATION)
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE)
        val expectedReaction = "🐻"

        reactionDAO.insertReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, "date", expectedReaction)
        reactionDAO.deleteReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, expectedReaction)

        // When
        val result = reactionDAO.getReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID)

        // Then
        assertTrue { result.isEmpty() }
    }

    @Test
    fun givenMultipleUsersAndReactions_whenGettingReactionsForUser_thenOnlyReactionsFromUserShouldBeReturned() = runTest {
        // Given
        insertTestUsers()
        conversationDAO.insertConversation(TEST_CONVERSATION)
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE)
        val wantedUserId = SELF_USER.id
        val otherUserId = OTHER_USER.id
        val expectedReactions = setOf("🐻", "🧱", "🍻")

        expectedReactions.forEach {
            reactionDAO.insertReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, wantedUserId, "date", it)
        }
        reactionDAO.insertReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, otherUserId, "date", "🪵")

        // When
        val result = reactionDAO.getReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, wantedUserId)

        // Then
        assertContentEquals(expectedReactions.sorted(), result.sorted())
    }

    @Test
    fun givenMultipleMessagesAndReactions_whenGettingReactionsForMessage_thenOnlyReactionsFromSpecifiedMessageIdAreReturned() = runTest {
        // Given
        insertTestUsers()
        conversationDAO.insertConversation(TEST_CONVERSATION)
        val wantedMessageId = "wantedMessageId"
        val otherMessageId = "otherMessageId"
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE.copy(id = wantedMessageId))
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE.copy(id = otherMessageId))
        val expectedReactions = setOf("🐻", "🧱", "🍻")

        expectedReactions.forEach {
            reactionDAO.insertReaction(wantedMessageId, TEST_MESSAGE.conversationId, SELF_USER_ID, "date", it)
        }
        reactionDAO.insertReaction(otherMessageId, TEST_MESSAGE.conversationId, SELF_USER_ID, "date", "🪵")

        // When
        val result = reactionDAO.getReaction(wantedMessageId, TEST_MESSAGE.conversationId, SELF_USER_ID)

        // Then
        assertContentEquals(expectedReactions.sorted(), result.sorted())
    }

    @Test
    fun givenMessagesWithSameId_whenGettingReactionsForMessage_thenOnlyReactionsFromSpecifiedConversationAreReturned() = runTest {
        // Given
        insertTestUsers()
        val wantedConversationId = TEST_CONVERSATION.id.copy(value = "wantedConversation")
        val otherConversationId = TEST_CONVERSATION.id.copy(value = "otherConversation")
        conversationDAO.insertConversation(TEST_CONVERSATION.copy(id = wantedConversationId))
        conversationDAO.insertConversation(TEST_CONVERSATION.copy(id = otherConversationId))
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE.copy(conversationId = wantedConversationId))
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE.copy(conversationId = otherConversationId))
        val expectedReactions = setOf("🐻", "🧱", "🍻")

        expectedReactions.forEach {
            reactionDAO.insertReaction(TEST_MESSAGE.id, wantedConversationId, SELF_USER_ID, "date", it)
        }
        reactionDAO.insertReaction(TEST_MESSAGE.id, otherConversationId, SELF_USER_ID, "date", "🪵")

        // When
        val result = reactionDAO.getReaction(TEST_MESSAGE.id, wantedConversationId, SELF_USER_ID)

        // Then
        assertContentEquals(expectedReactions.sorted(), result.sorted())
    }

    @Test
    fun givenInitiallyInsertedReaction_whenUpdatingReactionsOfUserWithoutInitiallyInserted_thenInitiallyInsertedIsDeleted() = runTest {
        // Given
        insertTestUsers()
        conversationDAO.insertConversation(TEST_CONVERSATION)
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE)
        val initialReaction = "😎"
        val expectedReactions = setOf("🐻", "🧱", "🍻")

        reactionDAO.insertReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, "Date", initialReaction)

        // Given
        reactionDAO.updateReactions(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, "Date", expectedReactions)

        // Then
        val result = reactionDAO.getReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID)

        assertContentEquals(expectedReactions.sorted(), result.sorted())
    }

    @Test
    fun givenInitiallyInsertedReaction_whenUpdatingReactionsOfUserWithInitiallyInserted_thenInitiallyInsertedIsKept() = runTest {
        // Given
        insertTestUsers()
        conversationDAO.insertConversation(TEST_CONVERSATION)
        messageDAO.insertOrIgnoreMessage(TEST_MESSAGE)
        val initialReaction = "😎"
        val expectedReactions = setOf("😎", "🐻", "🧱", "🍻")

        reactionDAO.insertReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, "Date", initialReaction)

        // Given
        reactionDAO.updateReactions(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID, "Date", expectedReactions)

        // Then
        val result = reactionDAO.getReaction(TEST_MESSAGE.id, TEST_MESSAGE.conversationId, SELF_USER_ID)

        assertContentEquals(expectedReactions.sorted(), result.sorted())
    }

    private suspend fun insertTestUsers() {
        userDAO.upsertUser(SELF_USER)
        userDAO.upsertUser(OTHER_USER)
    }

    private companion object {
        val TEST_CONVERSATION = newConversationEntity()
        val SELF_USER = newUserEntity("selfUser")
        val OTHER_USER = newUserEntity("anotherUser")
        val SELF_USER_ID = SELF_USER.id
        val TEST_MESSAGE = newRegularMessageEntity(conversationId = TEST_CONVERSATION.id, senderUserId = OTHER_USER.id)
    }
}
