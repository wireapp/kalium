package com.wire.kalium.persistence.dao.reaction

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
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
        val db = createDatabase(SELF_USER_ID)
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
        messageDAO.insertMessage(TEST_MESSAGE)
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
        messageDAO.insertMessage(TEST_MESSAGE)
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
        messageDAO.insertMessage(TEST_MESSAGE)
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
        messageDAO.insertMessage(TEST_MESSAGE.copy(id = wantedMessageId))
        messageDAO.insertMessage(TEST_MESSAGE.copy(id = otherMessageId))
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
        messageDAO.insertMessage(TEST_MESSAGE.copy(conversationId = wantedConversationId))
        messageDAO.insertMessage(TEST_MESSAGE.copy(conversationId = otherConversationId))
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
        messageDAO.insertMessage(TEST_MESSAGE)
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
        messageDAO.insertMessage(TEST_MESSAGE)
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
        userDAO.insertUser(SELF_USER)
        userDAO.insertUser(OTHER_USER)
    }

    private companion object {
        val TEST_CONVERSATION = newConversationEntity()
        val SELF_USER = newUserEntity("selfUser")
        val OTHER_USER = newUserEntity("anotherUser")
        val SELF_USER_ID = SELF_USER.id
        val TEST_MESSAGE = newRegularMessageEntity(conversationId = TEST_CONVERSATION.id, senderUserId = OTHER_USER.id)
    }
}
