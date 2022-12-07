package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
            newRegularMessageEntity(
                "1",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING,
                senderName = userInQuestion.name!!
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                status = MessageEntity.Status.PENDING,
                senderName = userInQuestion.name!!
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ,
                senderName = userInQuestion.name!!
            ),
            newRegularMessageEntity(
                "4",
                conversationId = conversationEntity1.id,
                // Different user
                senderUserId = otherUser.id,
                status = MessageEntity.Status.PENDING,
                senderName = otherUser.name!!
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
            newRegularMessageEntity(
                "3",
                conversationId = conversationEntity1.id,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.READ
            ),
            newRegularMessageEntity(
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
            newRegularMessageEntity(
                deleteMessageUuid,
                conversationId = deleteMessageConversationId,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.SENT
            ),
            newRegularMessageEntity(
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
            newRegularMessageEntity(
                messageUuid,
                conversationId = deleteMessageConversationId,
                senderUserId = userInQuestion.id,
                // Different status
                status = MessageEntity.Status.SENT
            ),
            newRegularMessageEntity(
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

    @Test
    fun givenMessagesAreInserted_whenGettingMessagesByConversation_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val otherConversation = conversationEntity2

        val visibilityInQuestion = MessageEntity.Visibility.VISIBLE
        val otherVisibility = MessageEntity.Visibility.HIDDEN

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!
            ),
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "3",
                // different conversation
                conversationId = otherConversation.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!
            ),
            newRegularMessageEntity(
                "4",
                // different conversation
                conversationId = otherConversation.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = visibilityInQuestion,
                senderName = userEntity1.name!!
            ),
            newRegularMessageEntity(
                "5",
                // different conversation
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                visibility = otherVisibility,
                senderName = userEntity1.name!!
            )
        )

        messageDAO.insertMessages(allMessages)
        val result =
            messageDAO.getMessagesByConversationAndVisibility(conversationInQuestion.id, 10, 0, listOf(visibilityInQuestion))
        assertContentEquals(expectedMessages, result.first())
    }

    @Test
    fun givenMessagesAreInserted_whenGettingMessagesByConversationAfterDate_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val dateInQuestion = "2022-03-30T15:36:00.000Z"

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                // date after
                date = "2022-03-30T15:37:00.000Z",
                senderName = userEntity1.name!!
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:35:00.000Z",
                senderName = userEntity1.name!!
            )
        )

        messageDAO.insertMessages(allMessages)
        val result = messageDAO.observeMessagesByConversationAndVisibilityAfterDate(conversationInQuestion.id, dateInQuestion)
        assertContentEquals(expectedMessages, result.first())
    }

    @Test
    fun givenConversations_whenGettingUnreadConversationCount_ThenReturnCorrectCount() = runTest {
        // given
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))

        conversationDAO.insertConversation(
            conversationEntity1.copy(
                lastModifiedDate = "2000-01-01T12:30:00.000Z",
                lastReadDate = "2000-01-01T12:00:00.000Z"
            )
        )

        conversationDAO.insertConversation(
            conversationEntity2.copy(
                lastModifiedDate = "2000-01-01T12:30:00.000Z",
                lastReadDate = "2000-01-01T13:00:00.000Z"
            )
        )

        // when
        val result = conversationDAO.getUnreadConversationCount()

        // then
        assertEquals(1L, result)
    }

    @Test
    fun givenMessagesAreInserted_whenGettingPendingMessagesByConversationAfterDate_thenOnlyRelevantMessagesAreReturned() = runTest {
        insertInitialData()

        val conversationInQuestion = conversationEntity1
        val dateInQuestion = "2022-03-30T15:36:00.000Z"

        val expectedMessages = listOf(
            newRegularMessageEntity(
                "1",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.PENDING,
                // date after
                date = "2022-03-30T15:37:00.000Z",
                senderName = userEntity1.name!!,
                content = MessageEntityContent.Text(
                    "Text Message [1]",
                    expectsReadConfirmation = true
                )
            )
        )

        val allMessages = expectedMessages + listOf(
            newRegularMessageEntity(
                "2",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:38:00.000Z",
                senderName = userEntity1.name!!,
                content = MessageEntityContent.Text(
                    "Text Message [2]",
                    expectsReadConfirmation = false
                )
            ),

            newRegularMessageEntity(
                "3",
                conversationId = conversationInQuestion.id,
                senderUserId = userEntity1.id,
                status = MessageEntity.Status.READ,
                // date before
                date = "2022-03-30T15:39:00.000Z",
                senderName = userEntity1.name!!,
                content = MessageEntityContent.Text(
                    "Text Message [3]",
                    expectsReadConfirmation = true
                )
            )
        )

        messageDAO.insertMessages(allMessages)
        val result = messageDAO.getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(conversationInQuestion.id, dateInQuestion)
        assertEquals(2, result.size)
    }

    private suspend fun insertInitialData() {
        userDAO.upsertUsers(listOf(userEntity1, userEntity2))
        conversationDAO.insertConversation(conversationEntity1)
        conversationDAO.insertConversation(conversationEntity2)
    }

}
