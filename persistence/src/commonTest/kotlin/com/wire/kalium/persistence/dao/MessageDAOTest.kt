package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.kaliumLogger
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageDAOTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO

    private val conversationEntity1 = newConversationEntity("Test1")
    private val conversationEntity2 = newConversationEntity("Test2")
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

    @Test
    fun givenListOfMessages_WhenMarkMessageAsDeleted_OnlyTheTargetedMessageVisibilityIsDeleted() = runTest {
        insertInitialData()
        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val deleteMessageUuid = "3"
        val deleteMessageConversationId = conversationEntity1.id
        val visibleMessageUuid = "4"
        val visibleMessageConversationId = conversationEntity2.id

        val allMessages = listOf(
            newMessageEntity(
                deleteMessageUuid,
                conversationId = deleteMessageConversationId,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.SENT
            ),
            newMessageEntity(
                visibleMessageUuid,
                conversationId = visibleMessageConversationId,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.SENT
            )
        )
        messageDAO.insertMessages(allMessages)

        messageDAO.markMessageAsDeleted(deleteMessageUuid, deleteMessageConversationId)

        val resultDeletedMessage = messageDAO.getMessageById(deleteMessageUuid, deleteMessageConversationId)

        assertTrue { resultDeletedMessage.first()?.visibility == MessageEntity.Visibility.DELETED }

        val notDeletedMessage = messageDAO.getMessageById(visibleMessageUuid, visibleMessageConversationId)
        assertTrue { notDeletedMessage.first()?.visibility == MessageEntity.Visibility.VISIBLE }
    }

    @Test
    fun givenMessagesBySameMessageIdDifferentConvId_WhenMarkMessageAsDeleted_OnlyTheMessageWithCorrectConIdVisibilityIsDeleted() = runTest {
        insertInitialData()
        val userInQuestion = userEntity1
        val otherUser = userEntity2

        val messageUuid = "sameMessageUUID"
        val deleteMessageConversationId = conversationEntity1.id
        val visibleMessageConversationId = conversationEntity2.id

        val allMessages = listOf(
            newMessageEntity(
                messageUuid,
                conversationId = deleteMessageConversationId,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.SENT
            ),
            newMessageEntity(
                messageUuid,
                conversationId = visibleMessageConversationId,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.SENT
            )
        )
        messageDAO.insertMessages(allMessages)

        messageDAO.markMessageAsDeleted(messageUuid, deleteMessageConversationId)

        val resultDeletedMessage = messageDAO.getMessageById(messageUuid, deleteMessageConversationId)

        assertTrue { resultDeletedMessage.first()?.visibility == MessageEntity.Visibility.DELETED }

        val notDeletedMessage = messageDAO.getMessageById(messageUuid, visibleMessageConversationId)
        assertTrue { notDeletedMessage.first()?.visibility == MessageEntity.Visibility.VISIBLE }
    }

    private suspend fun insertInitialData() {
        userDAO.insertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
    }

}
