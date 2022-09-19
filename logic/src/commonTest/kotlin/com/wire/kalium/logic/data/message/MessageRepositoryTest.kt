package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.QualifiedSendMessageResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.Status.SENT
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class MessageRepositoryTest {

    @Test
    fun givenAConversationId_whenGettingMessagesOfConversation_thenShouldUseIdMapperToMapTheConversationId() = runTest {
        // Given
        val mappedId: QualifiedIDEntity = TEST_QUALIFIED_ID_ENTITY
        val (arrangement, messageRepository) = Arrangement()
            .withMockedMessages(listOf())
            .withMappedId(mappedId)
            .withMappedMessageModel(TEST_MESSAGE)
            .arrange()

        // When
        messageRepository.getMessagesByConversationIdAndVisibility(TEST_CONVERSATION_ID, 0, 0).collect()

        // Then
        with(arrangement) {
            verify(messageDAO)
                .suspendFunction(messageDAO::getMessagesByConversationAndVisibility)
                .with(eq(mappedId), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenABaseMessageEntityAndMapper_whenGettingMessagesOfConversation_thenTheMapperShouldBeUsed() = runTest {
        // Given
        val mappedId: QualifiedIDEntity = TEST_QUALIFIED_ID_ENTITY
        val entity = TEST_MESSAGE_ENTITY
        val mappedMessage = TEST_MESSAGE
        val (arrangement, messageRepository) = Arrangement()
            .withMockedMessages(listOf(entity))
            .withMappedId(mappedId)
            .withMappedMessageModel(mappedMessage)
            .arrange()

        // When
        val messageList = messageRepository.getMessagesByConversationIdAndVisibility(TEST_CONVERSATION_ID, 0, 0).first()
        assertEquals(listOf(mappedMessage), messageList)

        // Then
        with(arrangement) {
            verify(messageMapper)
                .function(messageMapper::fromEntityToMessage)
                .with(eq(entity))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAMessage_whenPersisting_thenTheDAOShouldBeUsedWithMappedValues() = runTest {
        val mappedId: QualifiedIDEntity = TEST_QUALIFIED_ID_ENTITY
        val selfUserId = TEST_QUALIFIED_ID_ENTITY
        val message = TEST_MESSAGE
        val mappedEntity = TEST_MESSAGE_ENTITY
        val (arrangement, messageRepository) = Arrangement()
            .withMappedId(mappedId)
            .withMappedMessageEntity(mappedEntity)
            .arrange()

        messageRepository.persistMessage(message, selfUserId)

        with(arrangement) {
            verify(messageMapper)
                .function(messageMapper::fromMessageToEntity)
                .with(eq(message))
                .wasInvoked(exactly = once)

            verify(messageDAO)
                .suspendFunction(messageDAO::insertMessage)
                .with(eq(mappedEntity), eq(selfUserId), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAMessage_whenSendingReturnsSuccess_thenSuccessShouldBePropagatedWithServerTime() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val mappedId: NetworkQualifiedId = TEST_NETWORK_QUALIFIED_ID_ENTITY
        val timestamp = TEST_DATETIME

        val (_, messageRepository) = Arrangement()
            .withMappedApiModelId(mappedId)
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository.sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope)
            .shouldSucceed {
                assertSame(it, TEST_DATETIME)
            }
    }

    @Test
    fun givenAMessageWithExternalBlob_whenSending_thenApiShouldBeCalledWithBlob() = runTest {
        val mappedId = TEST_NETWORK_QUALIFIED_ID_ENTITY
        val dataBlob = EncryptedMessageBlob(byteArrayOf(0x42, 0x13, 0x69))
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf(), dataBlob)
        val timestamp = TEST_DATETIME

        val (arrangement, messageRepository) = Arrangement()
            .withMappedApiModelId(mappedId)
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository.sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope)
            .shouldSucceed {
                assertSame(it, TEST_DATETIME)
            }

        with(arrangement) {
            verify(messageApi)
                .suspendFunction(messageApi::qualifiedSendMessage)
                .with(matching { it.externalBlob!!.contentEquals(dataBlob.data) }, anything())
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {

        @Mock
        val idMapper = mock(IdMapper::class)

        @Mock
        val assetMapper = mock(AssetMapper::class)

        @Mock
        val messageApi = mock(MessageApi::class)

        @Mock
        val mlsMessageApi = mock(MLSMessageApi::class)

        @Mock
        val messageDAO = configure(mock(MessageDAO::class)) { stubsUnitByDefault = true }

        @Mock
        val sendMessageFailureMapper = mock(SendMessageFailureMapper::class)

        @Mock
        val messageMapper = mock(MessageMapper::class)

        fun withMockedMessages(messages: List<MessageEntity>): Arrangement {
            given(messageDAO)
                .suspendFunction(messageDAO::getMessagesByConversationAndVisibility)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .then { _, _, _, _ -> flowOf(messages) }
            return this
        }

        fun withMappedId(mappedId: QualifiedIDEntity): Arrangement {
            given(idMapper)
                .function(idMapper::toDaoModel)
                .whenInvokedWith(anything())
                .then { mappedId }
            return this
        }

        fun withMappedApiModelId(mappedId: NetworkQualifiedId): Arrangement {
            given(idMapper)
                .function(idMapper::toApiModel)
                .whenInvokedWith(anything())
                .then { mappedId }
            return this
        }

        fun withMappedMessageModel(message: Message.Regular): Arrangement {
            given(messageMapper)
                .function(messageMapper::fromEntityToMessage)
                .whenInvokedWith(anything())
                .then { message }
            return this
        }

        fun withMappedMessageEntity(message: MessageEntity.Regular): Arrangement {
            given(messageMapper)
                .function(messageMapper::fromMessageToEntity)
                .whenInvokedWith(anything())
                .then { message }
            return this
        }

        fun withSuccessfulMessageDelivery(timestamp: String): Arrangement {
            given(messageApi)
                .suspendFunction(messageApi::qualifiedSendMessage)
                .whenInvokedWith(anything(), anything())
                .then { _, _ ->
                    NetworkResponse.Success(
                        QualifiedSendMessageResponse.MessageSent(timestamp, mapOf(), mapOf(), mapOf()),
                        emptyMap(),
                        201
                    )
                }
            return this
        }

        fun arrange() = this to MessageDataSource(
            messageApi, mlsMessageApi, messageDAO, messageMapper, idMapper, assetMapper, sendMessageFailureMapper
        )
    }

    private companion object {
        val TEST_QUALIFIED_ID_ENTITY = PersistenceQualifiedId("value", "domain")
        val TEST_NETWORK_QUALIFIED_ID_ENTITY = NetworkQualifiedId("value", "domain")
        val TEST_MESSAGE_ENTITY =
            MessageEntity.Regular(
                id = "uid",
                content = MessageEntityContent.Text("content"),
                conversationId = TEST_QUALIFIED_ID_ENTITY,
                date = "date",
                senderUserId = TEST_QUALIFIED_ID_ENTITY,
                senderClientId = "sender",
                status = SENT,
                editStatus = MessageEntity.EditStatus.NotEdited
            )
        val TEST_CONVERSATION_ID = ConversationId("value", "domain")
        val TEST_CLIENT_ID = ClientId("clientId")
        val TEST_USER_ID = UserId("userId", "domain")
        val TEST_CONTENT = MessageContent.Text("Ciao!")
        const val TEST_DATETIME = "2022-04-21T20:56:22.393Z"
        val TEST_MESSAGE = Message.Regular(
            id = "uid",
            content = TEST_CONTENT,
            conversationId = TEST_CONVERSATION_ID,
            date = TEST_DATETIME,
            senderUserId = TEST_USER_ID,
            senderClientId = TEST_CLIENT_ID,
            status = Message.Status.SENT,
            editStatus = Message.EditStatus.NotEdited
        )
    }
}
