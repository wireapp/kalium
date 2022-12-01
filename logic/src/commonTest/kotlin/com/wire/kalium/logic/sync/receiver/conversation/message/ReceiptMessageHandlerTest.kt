package com.wire.kalium.logic.sync.receiver.conversation.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.message.ReceiptMessageHandlerImpl
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptMessageHandlerTest {

    private val userDatabase = TestUserDatabase(SELF_USER_ID_ENTITY)
    private val receiptRepository: ReceiptRepository = ReceiptRepositoryImpl(userDatabase.builder.receiptDAO)
    private val receiptMessageHandler = ReceiptMessageHandlerImpl(SELF_USER_ID, receiptRepository)

    private suspend fun insertTestData() {
        userDatabase.builder.userDAO.insertUser(TestUser.ENTITY.copy(id = SELF_USER_ID_ENTITY))
        userDatabase.builder.userDAO.insertUser(TestUser.ENTITY.copy(id = OTHER_USER_ID_ENTITY))
        userDatabase.builder.conversationDAO.insertConversation(CONVERSATION_ENTITY)
        userDatabase.builder.messageDAO.insertMessage(MESSAGE_ENTITY)
    }

    @Test
    fun givenAReceiptIsHandled_whenFetchingReceiptsOfThatType_thenTheResultShouldContainTheNewReceipt() = runTest {
        insertTestData()

        val date = Clock.System.now()
        val senderUserId = OTHER_USER_ID
        val type = ReceiptType.READ

        handleNewReceipt(type, date, senderUserId)

        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            assertEquals(1, awaitItem().size)
        }
    }

    @Test
    fun givenAReceiptIsHandled_whenFetchingReceiptsOfThatType_thenTheResultShouldMatchTheDateAndUser() = runTest {
        insertTestData()

        val date = Clock.System.now()
        val senderUserId = OTHER_USER_ID
        val type = ReceiptType.READ

        handleNewReceipt(type, date, senderUserId)

        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            val batch = awaitItem()
            assertEquals(1, batch.size)
            val receipt = batch.first()
            assertEquals(date, receipt.date)
            assertEquals(senderUserId, receipt.userSummary.userId)
        }
    }

    @Test
    fun givenAReceiptOfSelfUserIsHandled_whenFetchingReceiptsOfThatType_thenTheResultShouldContainNoReceipts() = runTest {
        insertTestData()

        val date = Clock.System.now()
        // Using Self User ID for the receipt
        val senderUserId = SELF_USER_ID
        val type = ReceiptType.READ

        handleNewReceipt(type, date, senderUserId)

        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            assertTrue { awaitItem().isEmpty() }
        }
    }

    @Test
    fun givenAReceiptIsHandled_whenFetchingReceiptsOfAnotherType_thenTheResultShouldContainNoReceipts() = runTest {
        insertTestData()

        val date = Clock.System.now()
        val senderUserId = OTHER_USER_ID
        // Delivery != Read
        val type = ReceiptType.DELIVERY

        handleNewReceipt(type, date, senderUserId)

        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            assertTrue { awaitItem().isEmpty() }
        }
    }

    private suspend fun handleNewReceipt(
        type: ReceiptType,
        date: Instant,
        senderUserId: QualifiedID
    ) {
        val content = MessageContent.Receipt(
            type = type,
            messageIds = listOf(MESSAGE_ID)
        )
        receiptMessageHandler.handle(
            message = Message.Signaling(
                id = "signalingId",
                content = content,
                conversationId = CONVERSATION_ID,
                date = date.toString(),
                senderUserId = senderUserId,
                senderClientId = ClientId("SomeClientId"),
                status = Message.Status.SENT,
            ),
            messageContent = content
        )
    }

    private companion object {
        val CONVERSATION_ID = TestConversation.CONVERSATION.id
        val CONVERSATION_ENTITY_ID = ConversationIDEntity(CONVERSATION_ID.value, CONVERSATION_ID.domain)
        val CONVERSATION_ENTITY = TestConversation.ENTITY.copy(id = CONVERSATION_ENTITY_ID)

        val SELF_USER_ID = TestUser.SELF.id
        val SELF_USER_ID_ENTITY = UserIDEntity(SELF_USER_ID.value, SELF_USER_ID.domain)

        val OTHER_USER_ID = TestUser.OTHER_USER_ID
        val OTHER_USER_ID_ENTITY = UserIDEntity(OTHER_USER_ID.value, OTHER_USER_ID.domain)

        const val MESSAGE_ID = "messageId"
        val MESSAGE_ENTITY = TestMessage.ENTITY.copy(
            id = MESSAGE_ID,
            conversationId = CONVERSATION_ENTITY_ID,
            senderUserId = SELF_USER_ID_ENTITY,
        )
    }
}
