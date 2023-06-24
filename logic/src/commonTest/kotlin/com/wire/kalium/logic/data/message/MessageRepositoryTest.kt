/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.BroadcastMessageOption
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.framework.TestMessage.TEST_MESSAGE_ID
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.Status.SENT
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.time.UNIX_FIRST_DATE
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
import kotlinx.datetime.Instant
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
            .withMappedMessageEntity(mappedEntity)
            .arrange()

        messageRepository.persistMessage(message)

        with(arrangement) {
            verify(messageMapper)
                .function(messageMapper::fromMessageToEntity)
                .with(eq(message))
                .wasInvoked(exactly = once)

            verify(messageDAO)
                .suspendFunction(messageDAO::insertOrIgnoreMessage)
                .with(eq(mappedEntity), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAMessage_whenSendingReturnsSuccess_thenSuccessShouldBePropagatedWithServerTime() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val mappedId: NetworkQualifiedId = TEST_NETWORK_QUALIFIED_ID_ENTITY
        val timestamp = TEST_DATETIME

        val (_, messageRepository) = Arrangement()
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository.sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Conversation)
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
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository.sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Conversation)
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

    @Test
    fun givenAnEnvelopeTargetedToClients_whenSending_thenShouldCallTheAPIWithCorrectParameters() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val mappedId: NetworkQualifiedId = TEST_NETWORK_QUALIFIED_ID_ENTITY
        val timestamp = TEST_DATETIME

        val (arrangement, messageRepository) = Arrangement()
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository.sendEnvelope(
            TEST_CONVERSATION_ID,
            messageEnvelope,
            MessageTarget.Client.IgnoreIfMissing(
                recipients = listOf(
                    Recipient(
                        id = TEST_USER_ID,
                        clients = listOf(TEST_CLIENT_ID)
                    )
                )
            )
        ).shouldSucceed()

        verify(arrangement.messageApi)
            .suspendFunction(arrangement.messageApi::qualifiedSendMessage)
            .with(
                matching {
                    it.recipients.isEmpty() && it.messageOption == MessageApi.QualifiedMessageOption.IgnoreAll
                }, anything()
            )
    }

    @Test
    fun givenAnEnvelopeTargetedToAConversation_whenSending_thenShouldCallTheAPIWithCorrectParameters() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val mappedId: NetworkQualifiedId = TEST_NETWORK_QUALIFIED_ID_ENTITY
        val timestamp = TEST_DATETIME

        val (arrangement, messageRepository) = Arrangement()
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository
            .sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Conversation)
            .shouldSucceed()

        verify(arrangement.messageApi)
            .suspendFunction(arrangement.messageApi::qualifiedSendMessage)
            .with(
                matching {
                    it.recipients.isEmpty() && it.messageOption == MessageApi.QualifiedMessageOption.ReportAll
                }, anything()
            )
    }

    @Test
    fun givenABroadcastMessage_whenBroadcastingReturnsSuccess_thenSuccessShouldBePropagatedWithServerTime() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val timestamp = TEST_DATETIME

        val (_, messageRepository) = Arrangement()
            .withSuccessfulMessageBroadcasting(timestamp)
            .arrange()

        messageRepository.broadcastEnvelope(messageEnvelope, BroadcastMessageOption.IgnoreSome(listOf()))
            .shouldSucceed {
                assertSame(it, TEST_DATETIME)
            }
    }

    @Test
    fun givenABroadcastMessageWithExternalBlob_whenBroadcasting_thenApiShouldBeCalledWithBlob() = runTest {
        val dataBlob = EncryptedMessageBlob(byteArrayOf(0x42, 0x13, 0x69))
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf(), dataBlob)
        val timestamp = TEST_DATETIME

        val (arrangement, messageRepository) = Arrangement()
            .withSuccessfulMessageBroadcasting(timestamp)
            .arrange()

        messageRepository.broadcastEnvelope(messageEnvelope, BroadcastMessageOption.IgnoreSome(listOf()))
            .shouldSucceed {
                assertSame(it, TEST_DATETIME)
            }

        with(arrangement) {
            verify(messageApi)
                .suspendFunction(messageApi::qualifiedBroadcastMessage)
                .with(matching { it.externalBlob!!.contentEquals(dataBlob.data) })
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnEnvelopeTargetedToClients_whenBroadcasting_thenShouldCallTheAPIWithCorrectParameters() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val timestamp = TEST_DATETIME

        val (arrangement, messageRepository) = Arrangement()
            .withSuccessfulMessageBroadcasting(timestamp)
            .arrange()

        messageRepository.broadcastEnvelope(
            messageEnvelope,
            BroadcastMessageOption.IgnoreSome(listOf())
        ).shouldSucceed()

        verify(arrangement.messageApi)
            .suspendFunction(arrangement.messageApi::qualifiedBroadcastMessage)
            .with(
                matching {
                    it.recipients.isEmpty() && it.messageOption == MessageApi.QualifiedMessageOption.IgnoreSome(listOf())
                }
            )
    }

    @Test
    fun whenUpdatingMessageAfterSending_thenDAOFunctionIsCalled() = runTest {
        val messageID = TEST_MESSAGE_ID
        val conversationID = TEST_CONVERSATION_ID
        val millis = 500L
        val newServerData = Instant.DISTANT_FUTURE

        val (arrangement, messageRepository) = Arrangement()
            .withUpdateMessageAfterSend()
            .arrange()

        messageRepository.promoteMessageToSentUpdatingServerTime(conversationID, messageID, newServerData, millis).shouldSucceed()

        verify(arrangement.messageDAO)
            .suspendFunction(arrangement.messageDAO::promoteMessageToSentUpdatingServerTime)
            .with(eq(conversationID.toDao()), eq(messageID), eq(newServerData), eq(millis))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnEnvelopeTargetedToAClientsWithFailIfMissing_whenSending_thenSShouldSetReportSomeAsOption() = runTest {
        val messageEnvelope = MessageEnvelope(TEST_CLIENT_ID, listOf())
        val timestamp = TEST_DATETIME
        val recipient = listOf(
            Recipient(
                id = TEST_USER_ID,
                clients = listOf(TEST_CLIENT_ID)
            )
        )
        val (arrangement, messageRepository) = Arrangement()
            .withSuccessfulMessageDelivery(timestamp)
            .arrange()

        messageRepository
            .sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Client.ReportIfMissing(recipient))
            .shouldSucceed()

        verify(arrangement.messageApi)
            .suspendFunction(arrangement.messageApi::qualifiedSendMessage)
            .with(
                matching {
                    (it.messageOption is MessageApi.QualifiedMessageOption.ReportSome) &&
                            ((it.messageOption as MessageApi.QualifiedMessageOption.ReportSome)
                                .userIDs == recipient.map { it.id })
                }, anything()
            )
    }

    private class Arrangement {

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
            given(messageDAO)
                .suspendFunction(messageDAO::getPendingToConfirmMessagesByConversationAndVisibilityAfterDate)
                .whenInvokedWith(anything(), anything())
                .then { _, _ -> messages.map { it.id } }
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

        fun withSuccessfulMessageBroadcasting(timestamp: String): Arrangement {
            given(messageApi)
                .suspendFunction(messageApi::qualifiedBroadcastMessage)
                .whenInvokedWith(anything())
                .then { _ ->
                    NetworkResponse.Success(
                        QualifiedSendMessageResponse.MessageSent(timestamp, mapOf(), mapOf(), mapOf()),
                        emptyMap(),
                        201
                    )
                }
            return this
        }

        fun withUpdateMessageAfterSend() = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::promoteMessageToSentUpdatingServerTime)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .then { _, _, _, _ -> Unit }
        }

        fun arrange() = this to MessageDataSource(
            messageApi = messageApi,
            mlsMessageApi = mlsMessageApi,
            messageDAO = messageDAO,
            messageMapper = messageMapper,
            assetMapper = assetMapper,
            selfUserId = SELF_USER_ID,
            sendMessageFailureMapper = sendMessageFailureMapper
        )
    }

    private companion object {
        val TEST_QUALIFIED_ID_ENTITY = PersistenceQualifiedId("value", "domain")
        val TEST_NETWORK_QUALIFIED_ID_ENTITY = NetworkQualifiedId("value", "domain")
        val SELF_USER_ID = UserId("user-id", "domain")
        val TEST_MESSAGE_ENTITY =
            MessageEntity.Regular(
                id = "uid",
                content = MessageEntityContent.Text("content"),
                conversationId = TEST_QUALIFIED_ID_ENTITY,
                date = Instant.UNIX_FIRST_DATE,
                senderUserId = TEST_QUALIFIED_ID_ENTITY,
                senderClientId = "sender",
                status = SENT,
                editStatus = MessageEntity.EditStatus.NotEdited,
                senderName = "senderName"
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
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = false
        )
    }
}
