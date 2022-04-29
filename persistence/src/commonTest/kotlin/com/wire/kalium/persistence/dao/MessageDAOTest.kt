package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class MessageDAOTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val conversationEntity1 = newConversationEntity()
    private val userEntity1 = newUserEntity("userEntity1")
    private val userEntity2 = newUserEntity("userEntity2")

    @BeforeTest
    fun setUp() {
        deleteDatabase()
        val db = createDatabase()
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingMessagesByUser_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val expectedMessages = listOf(
            newMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING
            ),
            newMessageEntity(
                "2",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING
            )
        )

        val allMessages = expectedMessages + listOf(
            newMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ
            ),
            newMessageEntity(
                "4",
                conversationId = conversationEntity1.id,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.PENDING
            )
        )

        messageDAO.insertMessages(allMessages)

        val result = messageDAO.getAllPendingMessagesFromUser(userInQuestion.id)

        assertContentEquals(expectedMessages, result)
    }

    @Test
    fun givenMessagesNoRelevantMessagesAreInserted_whenGettingPendingMessagesByUser_thenAnEmptyListIsReturned() = runTest {
        insertInitialData()

        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val allMessages = listOf(
            newMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ
            ),
            newMessageEntity(
                "4",
                conversationId = conversationEntity1.id,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.PENDING
            )
        )

        messageDAO.insertMessages(allMessages)

        val result = messageDAO.getAllPendingMessagesFromUser(userInQuestion.id)

        assertTrue { result.isEmpty() }
    }

    private suspend fun insertInitialData() {
        userDAO.insertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(conversationEntity1)
    }

}
