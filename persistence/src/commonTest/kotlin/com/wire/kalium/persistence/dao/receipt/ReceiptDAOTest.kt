package com.wire.kalium.persistence.dao.receipt

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptDAOTest : BaseDatabaseTest() {

    private lateinit var receiptDAO: ReceiptDAO
    private lateinit var userDAO: UserDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var messageDAO: MessageDAO

    @BeforeTest
    fun setUp() {
        deleteDatabase(SELF_USER_ID)
        val db = createDatabase(SELF_USER_ID)
        receiptDAO = db.receiptDAO
        userDAO = db.userDAO
        conversationDAO = db.conversationDAO
        messageDAO = db.messageDAO
    }

    @Test
    fun givenNoReceiptIsInserted_whenGettingDetails_shouldReturnEmptyResult() = runTest {
        insertTestData()

        val result = receiptDAO
            .observeDetailedReceiptsForMessage(TEST_CONVERSATION.id, TEST_MESSAGE.id, ReceiptTypeEntity.DELIVERY)
            .first()

        assertTrue { result.isEmpty() }
    }

    @Test
    fun givenReceiptIsInserted_whenGettingDetails_shouldReturnItWithCorrectData() = runTest {
        insertTestData()

        val insertedInstant = Clock.System.now()
        receiptDAO.insertReceipts(
            OTHER_USER.id, TEST_CONVERSATION.id, insertedInstant, ReceiptTypeEntity.DELIVERY, TEST_MESSAGE.id
        )

        val result = receiptDAO
            .observeDetailedReceiptsForMessage(TEST_CONVERSATION.id, TEST_MESSAGE.id, ReceiptTypeEntity.DELIVERY)
            .first()

        assertEquals(1, result.size)

        with(result.first()) {
            assertEquals(ReceiptTypeEntity.DELIVERY, type)
            assertEquals(insertedInstant, date)
            assertEquals(OTHER_USER.id, userId)
            assertEquals(OTHER_USER.name, userName)
            assertEquals(OTHER_USER.handle, userHandle)
            assertEquals(OTHER_USER.previewAssetId, userPreviewAssetId)
            assertEquals(OTHER_USER.userType, userType)
            assertEquals(OTHER_USER.deleted, isUserDeleted)
            assertEquals(OTHER_USER.connectionStatus, connectionStatus)
            assertEquals(OTHER_USER.availabilityStatus, availabilityStatus)
        }
    }

    @Test
    fun givenReceiptsFromMultipleUsersForSameMessage_whenGettingDetails_shouldReturnAllEntries() = runTest {
        insertTestData()

        receiptDAO.insertReceipts(
            OTHER_USER.id, TEST_CONVERSATION.id, Clock.System.now(), ReceiptTypeEntity.DELIVERY, TEST_MESSAGE.id
        )
        receiptDAO.insertReceipts(
            SELF_USER_ID, TEST_CONVERSATION.id, Clock.System.now(), ReceiptTypeEntity.DELIVERY, TEST_MESSAGE.id
        )

        val result = receiptDAO
            .observeDetailedReceiptsForMessage(TEST_CONVERSATION.id, TEST_MESSAGE.id, ReceiptTypeEntity.DELIVERY)
            .first()

        val resultingUserIds = result.map { it.userId }

        assertContentEquals(listOf(OTHER_USER.id, SELF_USER_ID), resultingUserIds)
    }

    @Test
    fun givenReceiptsOfMultipleTypesForSameMessage_whenGettingDetails_shouldReturnOnlyFromSpecifiedType() = runTest {
        insertTestData()

        receiptDAO.insertReceipts(
            OTHER_USER.id, TEST_CONVERSATION.id, Clock.System.now(), ReceiptTypeEntity.DELIVERY, TEST_MESSAGE.id
        )
        receiptDAO.insertReceipts(
            SELF_USER_ID, TEST_CONVERSATION.id, Clock.System.now(), ReceiptTypeEntity.READ, TEST_MESSAGE.id
        )

        val result = receiptDAO
            .observeDetailedReceiptsForMessage(TEST_CONVERSATION.id, TEST_MESSAGE.id, ReceiptTypeEntity.DELIVERY)
            .first()

        assertEquals(1, result.size)
        assertEquals(OTHER_USER.id, result.first().userId)
    }

    @Test
    fun givenReceiptsOfMultipleMessages_whenGettingDetails_shouldReturnOnlyFromSpecifiedMessage() = runTest {
        insertTestData()
        val otherMessageId = "someOtherTestMessage"
        messageDAO.insertMessage(
            newRegularMessageEntity(
                id = otherMessageId,
                senderUserId = SELF_USER_ID,
                conversationId = TEST_CONVERSATION.id
            )
        )

        receiptDAO.insertReceipts(
            OTHER_USER.id, TEST_CONVERSATION.id, Clock.System.now(), ReceiptTypeEntity.DELIVERY, TEST_MESSAGE.id
        )
        receiptDAO.insertReceipts(
            OTHER_USER.id, TEST_CONVERSATION.id, Clock.System.now(), ReceiptTypeEntity.DELIVERY, otherMessageId
        )

        val result = receiptDAO
            .observeDetailedReceiptsForMessage(TEST_CONVERSATION.id, TEST_MESSAGE.id, ReceiptTypeEntity.DELIVERY)
            .first()

        assertEquals(1, result.size)
        assertEquals(ReceiptTypeEntity.DELIVERY, result.first().type)
    }

    private suspend fun insertTestData() {
        userDAO.insertUser(SELF_USER)
        userDAO.insertUser(OTHER_USER)
        conversationDAO.insertConversation(TEST_CONVERSATION)
        messageDAO.insertMessage(TEST_MESSAGE)
    }

    private companion object {
        val TEST_CONVERSATION = newConversationEntity()
        val SELF_USER = newUserEntity("selfUser")
        val OTHER_USER = newUserEntity("anotherUser")
        val SELF_USER_ID = SELF_USER.id
        val TEST_MESSAGE = newRegularMessageEntity(conversationId = TEST_CONVERSATION.id, senderUserId = OTHER_USER.id)
    }
}
