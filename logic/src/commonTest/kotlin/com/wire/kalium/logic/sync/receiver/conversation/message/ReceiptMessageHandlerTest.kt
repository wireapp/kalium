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

package com.wire.kalium.logic.sync.receiver.conversation.message

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepositoryImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.ReceiptMessageHandlerImpl
import com.wire.kalium.logic.util.IgnoreIOS
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiptMessageHandlerTest {

    private val userDatabase = TestUserDatabase(SELF_USER_ID_ENTITY)
    private val receiptRepository: ReceiptRepository = ReceiptRepositoryImpl(userDatabase.builder.receiptDAO)

    @Mock
    private val messageRepository: MessageRepository = mock(classOf<MessageRepository>())

    private val receiptMessageHandler = ReceiptMessageHandlerImpl(SELF_USER_ID, receiptRepository, messageRepository)

    private suspend fun insertTestData() {
        userDatabase.builder.userDAO.upsertUser(TestUser.ENTITY.copy(id = SELF_USER_ID_ENTITY))
        userDatabase.builder.userDAO.upsertUser(TestUser.ENTITY.copy(id = OTHER_USER_ID_ENTITY))
        userDatabase.builder.conversationDAO.insertConversation(CONVERSATION_ENTITY)
        userDatabase.builder.messageDAO.insertOrIgnoreMessage(MESSAGE_ENTITY)
    }

    @Test
    fun givenAReceiptIsHandled_whenFetchingReceiptsOfThatType_thenTheResultShouldContainTheNewReceipt() = runTest {
        // given
        insertTestData()

        val date = DateTimeUtil.currentInstant()
        val senderUserId = OTHER_USER_ID
        val type = ReceiptType.READ

        coEvery {
            messageRepository.updateMessagesStatus(any(), any(), any())
        }.returns(Either.Right(Unit))
        // when
        handleNewReceipt(type, date, senderUserId)

        // given
        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            assertEquals(1, awaitItem().size)
        }
    }

    @IgnoreIOS // TODO investigate why test is failing, timestamp precision?
    @Test
    fun givenAReceiptIsHandled_whenFetchingReceiptsOfThatType_thenTheResultShouldMatchTheDateAndUser() = runTest {
        // given
        insertTestData()

        val date = DateTimeUtil.currentInstant()
        val senderUserId = OTHER_USER_ID
        val type = ReceiptType.READ

        coEvery {
            messageRepository.updateMessagesStatus(any(), any(), any())
        }.returns(Either.Right(Unit))
        // when
        handleNewReceipt(type, date, senderUserId)

        // given
        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            val batch = awaitItem()
            assertEquals(1, batch.size)
            val receipt = batch.first()
            assertEquals(date, receipt.date)
            assertEquals(senderUserId, receipt.userSummary.userId)
        }
    }

    @IgnoreIOS // TODO investigate why test is failing, timestamp precision?
    @Test
    fun givenAReceiptOfSelfUserIsHandled_whenFetchingReceiptsOfThatType_thenTheResultShouldContainNoReceipts() = runTest {
        // given
        insertTestData()

        val date = DateTimeUtil.currentInstant()
        // Using Self User ID for the receipt
        val senderUserId = SELF_USER_ID
        val type = ReceiptType.READ

        coEvery {
            messageRepository.updateMessagesStatus(any(), any(), any())
        }.returns(Either.Right(Unit))
        // when
        handleNewReceipt(type, date, senderUserId)

        // then
        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            assertTrue { awaitItem().isEmpty() }
        }
    }

    @IgnoreIOS // TODO investigate why test is failing, timestamp precision?
    @Test
    fun givenAReceiptIsHandled_whenFetchingReceiptsOfAnotherType_thenTheResultShouldContainNoReceipts() = runTest {
        // given
        insertTestData()

        val date = DateTimeUtil.currentInstant()
        val senderUserId = OTHER_USER_ID
        // Delivery != Read
        val type = ReceiptType.DELIVERED

        coEvery {
            messageRepository.updateMessagesStatus(any(), any(), any())
        }.returns(Either.Right(Unit))
        // when
        handleNewReceipt(type, date, senderUserId)

        // then
        receiptRepository.observeMessageReceipts(CONVERSATION_ID, MESSAGE_ID, ReceiptType.READ).test {
            assertTrue { awaitItem().isEmpty() }
        }
    }

    @Test
    fun givenAReceipt_whenHandlingAReceipt_theMessageStatusIsUpdatedAsExpected() = runTest {
        // given
        val date = DateTimeUtil.currentInstant()
        val senderUserId = OTHER_USER_ID
        val type = ReceiptType.DELIVERED
        val messageUuids = listOf("1", "2", "3")

        coEvery {
            messageRepository.updateMessagesStatus(any(), any(), any())
        }.returns(Either.Right(Unit))

        // when
        handleNewReceipt(type, date, senderUserId, messageUuids)

        // then
        coVerify {
            messageRepository.updateMessagesStatus(eq(MessageEntity.Status.DELIVERED), any(), eq(messageUuids))
        }.wasInvoked(exactly = once)
    }

    private suspend fun handleNewReceipt(
        type: ReceiptType,
        date: Instant,
        senderUserId: QualifiedID,
        messageIds: List<String> = listOf(MESSAGE_ID)
    ) {
        val content = MessageContent.Receipt(
            type = type,
            messageIds = messageIds
        )
        receiptMessageHandler.handle(
            message = Message.Signaling(
                id = "signalingId",
                content = content,
                conversationId = CONVERSATION_ID,
                date = date.toIsoDateTimeString(),
                senderUserId = senderUserId,
                senderClientId = ClientId("SomeClientId"),
                status = Message.Status.Sent,
                isSelfMessage = false,
                expirationData = null
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
