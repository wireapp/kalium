package com.wire.kalium.logic.data.reaction

import com.wire.kalium.logic.data.message.reaction.ReactionRepositoryImpl
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReactionRepositoryTest {

    private val userDatabase = TestUserDatabase(TestUser.ENTITY_ID)
    private val reactionsDao = userDatabase.provider.reactionDAO
    private val conversationDao = userDatabase.provider.conversationDAO
    private val userDao = userDatabase.provider.userDAO
    private val messageDao = userDatabase.provider.messageDAO

    private val reactionRepository = ReactionRepositoryImpl(SELF_USER_ID, reactionsDao)

    @AfterTest
    fun tearDown() {
        userDatabase.delete()
    }

    @Test
    fun givenSelfUserReactionWasPersisted_whenGettingSelfUserReactions_thenShouldReturnPreviouslyStored() = runTest {
        insertInitialData()
        val emoji = "🫡"
        reactionRepository.persistReaction(TEST_MESSAGE_ID, TEST_CONVERSATION_ID, SELF_USER_ID, "Date", emoji)

        val result = reactionRepository.getSelfUserReactionsForMessage(TEST_MESSAGE_ID, TEST_CONVERSATION_ID)

        result.shouldSucceed {
            assertTrue(it.size == 1)
            assertEquals(emoji, it.first())
        }
    }

    suspend fun insertInitialData() {
        userDao.insertUser(TEST_SELF_USER_ENTITY)
        conversationDao.insertConversation(TEST_CONVERSATION_ENTITY)
        messageDao.insertMessage(TEST_MESSAGE_ENTITY)
    }

    private companion object {
        private val SELF_USER_ID = TestUser.USER_ID
        private val TEST_SELF_USER_ENTITY = TestUser.ENTITY.copy(
            id = QualifiedIDEntity(
                SELF_USER_ID.value,
                SELF_USER_ID.domain
            )
        )
        private val TEST_CONVERSATION_ID = TestConversation.ID
        private val TEST_CONVERSATION_ENTITY =
            TestConversation.ENTITY.copy(
                id = QualifiedIDEntity(
                    TEST_CONVERSATION_ID.value,
                    TEST_CONVERSATION_ID.domain
                )
            )
        private val TEST_MESSAGE_ID = TestMessage.TEST_MESSAGE_ID
        private val TEST_MESSAGE_ENTITY = TestMessage.ENTITY.copy(
            id = TEST_MESSAGE_ID,
            senderUserId = TEST_SELF_USER_ENTITY.id,
            conversationId = TEST_CONVERSATION_ENTITY.id
        )
    }
}
