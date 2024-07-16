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

package com.wire.kalium.logic.data.message

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage.TEST_MESSAGE_ID
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.OTHER_USER_ID
import com.wire.kalium.logic.framework.TestUser.OTHER_USER_ID_2
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.Status.SENT
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import com.wire.kalium.persistence.dao.message.RecipientFailureTypeEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

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
    fun givenAConversationId_whenGettingAssetMessagesOfConversation_thenShouldUseIdMapperToMapTheConversationId() = runTest {
        // Given
        val mappedId: QualifiedIDEntity = TEST_QUALIFIED_ID_ENTITY
        val (arrangement, messageRepository) = Arrangement()
            .withAssetMessages(mappedId, listOf())
            .withMappedAssetMessageModel(TEST_ASSET_MESSAGE)
            .arrange()

        // When
        messageRepository.getImageAssetMessagesByConversationId(TEST_CONVERSATION_ID, 0, 0)

        // Then
        with(arrangement) {
            verify(messageDAO)
                .suspendFunction(messageDAO::getImageMessageAssets)
                .with(eq(mappedId), anything(), anything())
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
            .withFailedToSendMapping(emptyList())
            .arrange()

        messageRepository.sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Conversation())
            .shouldSucceed {
                assertSame(it.time, TEST_DATETIME)
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
            .withFailedToSendMapping(emptyList())
            .arrange()

        messageRepository.sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Conversation())
            .shouldSucceed {
                assertSame(it.time, TEST_DATETIME)
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
            .withFailedToSendMapping(emptyList())
            .arrange()

        messageRepository.sendEnvelope(
            TEST_CONVERSATION_ID,
            messageEnvelope,
            MessageTarget.Client(
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
            .withFailedToSendMapping(emptyList())
            .arrange()

        messageRepository
            .sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Conversation())
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
            .withFailedToSendMapping(emptyList())
            .arrange()

        messageRepository
            .sendEnvelope(TEST_CONVERSATION_ID, messageEnvelope, MessageTarget.Users(listOf(TEST_USER_ID)))
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

    @Test
    fun whenPersistingFailedDeliveryRecipients_thenDAOFunctionIsCalled() = runTest {
        val messageID = TEST_MESSAGE_ID
        val conversationID = TEST_CONVERSATION_ID
        val listOfUserIds = listOf(TEST_USER_ID, OTHER_USER_ID_2)
        val expectedFailedUsers = listOfUserIds.map { it.toDao() }

        val (arrangement, messageRepository) = Arrangement()
            .withInsertFailedRecipients()
            .arrange()

        messageRepository.persistRecipientsDeliveryFailure(conversationID, messageID, listOfUserIds).shouldSucceed()

        verify(arrangement.messageDAO)
            .suspendFunction(arrangement.messageDAO::insertFailedRecipientDelivery)
            .with(
                eq(messageID),
                eq(conversationID.toDao()),
                eq(expectedFailedUsers),
                eq(RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenPersistingFailedNoClientsRecipients_thenDAOFunctionIsCalled() = runTest {
        val messageID = TEST_MESSAGE_ID
        val conversationID = TEST_CONVERSATION_ID
        val listOfUserIds = listOf(TEST_USER_ID, OTHER_USER_ID_2)
        val expectedFailedUsers = listOfUserIds.map { it.toDao() }

        val (arrangement, messageRepository) = Arrangement()
            .withInsertFailedRecipients()
            .arrange()

        messageRepository.persistNoClientsToDeliverFailure(conversationID, messageID, listOfUserIds).shouldSucceed()

        verify(arrangement.messageDAO)
            .suspendFunction(arrangement.messageDAO::insertFailedRecipientDelivery)
            .with(
                eq(messageID),
                eq(conversationID.toDao()),
                eq(expectedFailedUsers),
                eq(RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER)
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun whenCallingSendMlsMessage_AndFailedUsers_thenAPIFunctionIsCalledAndPartialFailureMapped() = runTest {
        val conversationID = TEST_CONVERSATION_ID
        val listOfUserIds = listOf(TEST_USER_ID, OTHER_USER_ID_2)
        val expectedFailedUsers = listOfUserIds.map { it.toApi() }

        val (arrangement, messageRepository) = Arrangement()
            .withMlsSendMessageResponse(SendMLSMessageResponse(TEST_DATETIME, listOf(), expectedFailedUsers))
            .withFailedToSendMlsMapping(listOfUserIds)
            .arrange()

        val result = messageRepository.sendMLSMessage(conversationID, MLSMessageApi.Message(ByteArray(0)))
        result.shouldSucceed()

        assertTrue {
            (result as Either.Right).value.failedToConfirmClients.isNotEmpty()
        }

        verify(arrangement.mlsMessageApi)
            .suspendFunction(arrangement.mlsMessageApi::sendMessage)
            .with(
                matching {
                    it.value.contentToString() == ByteArray(0).contentToString()
                },
            )
            .wasInvoked(exactly = once)

        verify(arrangement.sendMessagePartialFailureMapper)
            .function(arrangement.sendMessagePartialFailureMapper::fromMlsDTO)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationIds_whenMovingMessages_thenShouldCallDAOWithCorrectParameters() = runTest {
        val sourceConversationId = TEST_CONVERSATION_ID.copy(value = "source")
        val targetConversationId = TEST_CONVERSATION_ID.copy(value = "target")

        val (arrangement, messageRepository) = Arrangement()
            .withMovingToAnotherConversationSucceeding()
            .arrange()

        messageRepository.moveMessagesToAnotherConversation(
            sourceConversationId,
            targetConversationId
        ).shouldSucceed()

        verify(arrangement.messageDAO)
            .suspendFunction(arrangement.messageDAO::moveMessages)
            .with(
                eq(sourceConversationId.toDao()),
                eq(targetConversationId.toDao())
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDAOFails_whenMovingMessages_thenShouldPropagateFailure() = runTest {
        val exception = IllegalArgumentException("Oopsie doopsie!")
        val (_, messageRepository) = Arrangement()
            .withMovingToAnotherConversationFailingWith(exception)
            .arrange()
        val sourceConversationId = TEST_CONVERSATION_ID.copy(value = "source")
        val targetConversationId = TEST_CONVERSATION_ID.copy(value = "target")

        messageRepository.moveMessagesToAnotherConversation(
            sourceConversationId,
            targetConversationId
        ).shouldFail {
            assertIs<StorageFailure.Generic>(it)
            assertEquals(exception, it.rootCause)
        }
    }

    @Test
    fun givenSearchedMessages_whenMessageIsSelected_thenReturnMessagePosition() = runTest {
        // given
        val qualifiedIdEntity = TEST_QUALIFIED_ID_ENTITY
        val conversationId = TEST_CONVERSATION_ID
        val message = TEST_MESSAGE_ENTITY.copy(
            id = "msg1",
            conversationId = qualifiedIdEntity,
            content = MessageEntityContent.Text("message 1")
        )
        val expectedMessagePosition = 113
        val (_, messageRepository) = Arrangement()
            .withSelectedMessagePosition(
                conversationId = conversationId.toDao(),
                messageId = message.id,
                result = expectedMessagePosition
            )
            .arrange()

        // when
        val result = messageRepository.getSearchedConversationMessagePosition(
            conversationId = conversationId,
            messageId = message.id
        )

        // then
        assertEquals(
            expectedMessagePosition,
            (result as Either.Right).value
        )
    }

    @Test
    fun givenLegalHoldForMembersMessage_whenUpdatingMembers_thenTheDAOShouldBeCalledWithProperValues() = runTest {
        // given
        val newUsersList = listOf(OTHER_USER_ID, OTHER_USER_ID_2)
        val (arrangement, messageRepository) = Arrangement().arrange()
        // when
        messageRepository.updateLegalHoldMessageMembers(TEST_MESSAGE_ID, TEST_CONVERSATION_ID, newUsersList)
        // then
        verify(arrangement.messageDAO)
            .suspendFunction(arrangement.messageDAO::updateLegalHoldMessageMembers)
            .with(eq(TEST_CONVERSATION_ID.toDao()), eq(TEST_MESSAGE_ID), eq(newUsersList.map { it.toDao() }))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenConversationIds_whenGettingLastMessagesForConversationIds_thenTheDAOShouldBeCalledWithProperValues() = runTest {
        // given
        val conversationIds = listOf(TEST_CONVERSATION_ID.copy("id1"), TEST_CONVERSATION_ID.copy("id2"))
        val (arrangement, messageRepository) = Arrangement()
            .withGetLastMessagesByConversations(emptyMap())
            .arrange()
        // when
        messageRepository.getLastMessagesForConversationIds(conversationIds)
        // then
        verify(arrangement.messageDAO)
            .suspendFunction(arrangement.messageDAO::getLastMessagesByConversations)
            .with(eq(conversationIds.map { it.toDao() }))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccessOnNotificationMessage_whenGettingNotificationMessage_thenTheDAOShouldBeCalled() = runTest {
        // given
        val (arrangement, messageRepository) = Arrangement()
            .withNotificationMessage(listOf())
            .arrange()

        // when
        messageRepository.getNotificationMessage().test {
            val result = awaitItem()
            // then
            result.shouldSucceed { it.isEmpty() }

            verify(arrangement.messageDAO)
                .suspendFunction(arrangement.messageDAO::getNotificationMessage)
                .wasInvoked(exactly = once)

            awaitComplete()
        }
    }

    @Test
    fun givenSuccessOnNotificationMessage2_whenGettingNotificationMessage_thenProperNotificationReturned() = runTest {
        // given
        val (arrangement, messageRepository) = Arrangement()
            .withNotificationMessage(listOf(NOTIFICATION_ENTITY))
            .withMappedEntitiesToLocalNotifications(NOTIFICATION_MESSAGE)
            .arrange()

        // when
        messageRepository.getNotificationMessage().test {
            val result = awaitItem()

            // then
            result.shouldSucceed {
                assertEquals(1, it.size)
                assertEquals(NOTIFICATION_CONVERSATION, it.first())
            }

            awaitComplete()
        }
    }

    private class Arrangement {

        @Mock
        val messageApi = mock(MessageApi::class)

        @Mock
        val mlsMessageApi = mock(MLSMessageApi::class)

        @Mock
        val messageDAO = configure(mock(MessageDAO::class)) { stubsUnitByDefault = true }

        @Mock
        val sendMessageFailureMapper = mock(SendMessageFailureMapper::class)

        @Mock
        val sendMessagePartialFailureMapper = mock(SendMessagePartialFailureMapper::class)

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

        fun withMappedAssetMessageModel(message: AssetMessage): Arrangement {
            given(messageMapper)
                .function(messageMapper::fromAssetEntityToAssetMessage)
                .whenInvokedWith(anything())
                .then { message }
            return this
        }

        fun withMappedMessageModel(result: Message.Regular, param: MessageEntity.Regular): Arrangement {
            given(messageMapper)
                .function(messageMapper::fromEntityToMessage)
                .whenInvokedWith(eq(param))
                .then { result }
            return this
        }

        fun withMappedMessageEntity(message: MessageEntity.Regular): Arrangement {
            given(messageMapper)
                .function(messageMapper::fromMessageToEntity)
                .whenInvokedWith(anything())
                .then { message }
            return this
        }

        fun withMappedEntitiesToLocalNotifications(message: LocalNotificationMessage): Arrangement {
            given(messageMapper)
                .function(messageMapper::fromMessageToLocalNotificationMessage)
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

        fun withFailedToSendMlsMapping(failedToSend: List<UserId>) = apply {
            given(sendMessagePartialFailureMapper)
                .function(sendMessagePartialFailureMapper::fromMlsDTO)
                .whenInvokedWith(anything())
                .then { MessageSent(TEST_DATETIME, failedToSend) }
        }

        fun withFailedToSendMapping(failedToSend: List<UserId>) = apply {
            given(sendMessagePartialFailureMapper)
                .function(sendMessagePartialFailureMapper::fromDTO)
                .whenInvokedWith(anything())
                .then { MessageSent(TEST_DATETIME, failedToSend) }
        }

        fun withMlsSendMessageResponse(
            timestamp: SendMLSMessageResponse = SendMLSMessageResponse(
                TEST_DATETIME,
                listOf(),
                listOf()
            )
        ): Arrangement {
            given(mlsMessageApi)
                .suspendFunction(mlsMessageApi::sendMessage)
                .whenInvokedWith(anything())
                .then {
                    NetworkResponse.Success(
                        timestamp,
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

        fun withMovingToAnotherConversationSucceeding() = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::moveMessages)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withMovingToAnotherConversationFailingWith(throwable: Throwable) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::moveMessages)
                .whenInvokedWith(any())
                .thenThrow(throwable)
        }

        fun withSelectedMessagePosition(
            conversationId: QualifiedIDEntity,
            messageId: String,
            result: Int
        ) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::getSearchedConversationMessagePosition)
                .whenInvokedWith(eq(conversationId), eq(messageId))
                .thenReturn(result)
        }

        fun withAssetMessages(
            conversationId: QualifiedIDEntity,
            result: List<AssetMessageEntity>
        ) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::getImageMessageAssets)
                .whenInvokedWith(eq(conversationId))
                .thenReturn(result)
        }

        fun withGetLastMessagesByConversations(result: Map<QualifiedIDEntity, MessageEntity>) = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::getLastMessagesByConversations)
                .whenInvokedWith(anything())
                .thenReturn(result)
        }

        fun withNotificationMessage(notificationEntities: List<NotificationMessageEntity>): Arrangement {
            given(messageDAO)
                .suspendFunction(messageDAO::getNotificationMessage)
                .whenInvoked()
                .then { flowOf(notificationEntities) }
            return this
        }

        fun arrange() = this to MessageDataSource(
            messageApi = messageApi,
            mlsMessageApi = mlsMessageApi,
            messageDAO = messageDAO,
            messageMapper = messageMapper,
            selfUserId = SELF_USER_ID,
            sendMessageFailureMapper = sendMessageFailureMapper,
            sendMessagePartialFailureMapper = sendMessagePartialFailureMapper
        )

        fun withInsertFailedRecipients() = apply {
            given(messageDAO)
                .suspendFunction(messageDAO::insertFailedRecipientDelivery)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .then { _, _, _, _ -> Unit }
        }
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
                senderName = "senderName",
                readCount = 0L
            )
        val TEST_CONVERSATION_ID = ConversationId("value", "domain")
        val TEST_CLIENT_ID = ClientId("clientId")
        val TEST_USER_ID = UserId("userId", "domain")
        val TEST_CONTENT = MessageContent.Text("Ciao!")
        const val TEST_DATETIME = "2022-04-21T20:56:22.393Z"
        val INSTANT_TEST_DATETIME = Instant.parse(TEST_DATETIME)
        val TEST_MESSAGE = Message.Regular(
            id = "uid",
            content = TEST_CONTENT,
            conversationId = TEST_CONVERSATION_ID,
            date = TEST_DATETIME,
            senderUserId = TEST_USER_ID,
            senderClientId = TEST_CLIENT_ID,
            status = Message.Status.Sent,
            editStatus = Message.EditStatus.NotEdited,
            isSelfMessage = false
        )
        val TEST_ASSET_MESSAGE = AssetMessage(
            time = INSTANT_TEST_DATETIME,
            conversationId = TEST_CONVERSATION_ID,
            username = "username",
            messageId = "messageId",
            assetId = "assetId",
            width = 640,
            height = 480,
            downloadStatus = Message.DownloadStatus.SAVED_INTERNALLY,
            assetPath = "asset/path".toPath(),
            isSelfAsset = false
        )

        val TEST_FAILED_DELIVERY_USERS: Map<String, Map<String, List<String>>> = mapOf(
            TEST_USER_ID.domain to mapOf(
                TEST_USER_ID.value to listOf(TEST_CLIENT_ID.value, ClientId("clientId2").value),
                OTHER_USER_ID_2.value to listOf(
                    TEST_CLIENT_ID.value, ClientId("clientId2").value
                )
            )
        )

        val NOTIFICATION_MESSAGE = LocalNotificationMessage.Text(
            messageId = "message_id",
            author = LocalNotificationMessageAuthor("Sender", null),
            time = Instant.DISTANT_PAST,
            text = "Some text in message",
            isQuotingSelfUser = false
        )

        val NOTIFICATION_CONVERSATION = LocalNotification.Conversation(
            TestConversation.ID, "", listOf(NOTIFICATION_MESSAGE), true, true
        )

        val NOTIFICATION_ENTITY = NotificationMessageEntity(
            id = "message_id",
            contentType = MessageEntity.ContentType.TEXT,
            isSelfDelete = false,
            senderUserId = TestUser.ENTITY_ID,
            senderImage = null,
            date = Instant.DISTANT_PAST,
            senderName = "Sender",
            text = "Some text in message",
            assetMimeType = null,
            isQuotingSelf = false,
            conversationId = TestConversation.ENTITY_ID,
            conversationName = null,
            mutedStatus = ConversationEntity.MutedStatus.ALL_ALLOWED,
            conversationType = ConversationEntity.Type.ONE_ON_ONE,
            degradedConversationNotified = true,
            legalHoldStatus = ConversationEntity.LegalHoldStatus.ENABLED,
            legalHoldStatusChangeNotified = true
        )
    }
}
