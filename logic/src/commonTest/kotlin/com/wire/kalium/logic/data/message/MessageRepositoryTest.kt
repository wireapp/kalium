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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage.TEST_MESSAGE_ID
import com.wire.kalium.logic.framework.TestUser.OTHER_USER_ID
import com.wire.kalium.logic.framework.TestUser.OTHER_USER_ID_2
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.QualifiedMessageOption
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.Status.SENT
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.RecipientFailureTypeEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
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
import kotlin.test.assertContentEquals
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
            coVerify {
                messageDAO.getMessagesByConversationAndVisibility(eq(mappedId), any(), any(), any())
            }.wasInvoked(exactly = once)
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
            coVerify {
                messageDAO.getImageMessageAssets(eq(mappedId), any(), any(), any())
            }.wasInvoked(exactly = once)
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
            verify {
                messageMapper.fromEntityToMessage(eq(entity))
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAMessage_whenPersisting_thenTheDAOShouldBeUsedWithMappedValues() = runTest {
        val message = TEST_MESSAGE
        val mappedEntity = TEST_MESSAGE_ENTITY
        val insertOrIgnoreMessage = InsertMessageResult.INSERTED_INTO_MUTED_CONVERSATION
        val (arrangement, messageRepository) = Arrangement()
            .withMappedMessageEntity(mappedEntity)
            .withInsertOrIgnoreMessage(insertOrIgnoreMessage)
            .arrange()

        assertEquals(insertOrIgnoreMessage.right(), messageRepository.persistMessage(message))

        with(arrangement) {
            verify {
                messageMapper.fromMessageToEntity(eq(message))
            }.wasInvoked(exactly = once)

            coVerify {
                messageDAO.insertOrIgnoreMessage(eq(mappedEntity), any(), any())
            }.wasInvoked(exactly = once)
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
            coVerify {
                messageApi.qualifiedSendMessage(matches { it.externalBlob!!.contentEquals(dataBlob.data) }, any())
            }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.messageApi.qualifiedSendMessage(
                matches {
                    it.recipients.isEmpty() && it.messageOption == QualifiedMessageOption.IgnoreAll
                }, any()
            )
        }.wasInvoked()
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

        coVerify {
            arrangement.messageApi.qualifiedSendMessage(
                matches {
                    it.recipients.isEmpty() && it.messageOption == QualifiedMessageOption.ReportAll
                }, any()
            )
        }.wasInvoked()
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
            coVerify {
                messageApi.qualifiedBroadcastMessage(matches { it.externalBlob!!.contentEquals(dataBlob.data) })
            }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.messageApi.qualifiedBroadcastMessage(
                matches { it.recipients.isEmpty() && it.messageOption == QualifiedMessageOption.IgnoreSome(listOf()) }
            )
        }.wasInvoked()
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

        coVerify {
            arrangement.messageDAO.promoteMessageToSentUpdatingServerTime(
                eq(conversationID.toDao()),
                eq(messageID),
                eq(newServerData),
                eq(millis)
            )
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.messageApi.qualifiedSendMessage(
                matches {
                    val messageOption = it.messageOption
                    assertIs<QualifiedMessageOption.ReportSome>(messageOption)
                    val expected = recipient.map { recipient -> recipient.id.toApi() }
                    assertContentEquals(expected, messageOption.userIDs)
                    true
                },
                any()
            )
        }.wasInvoked()
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

        coVerify {
            arrangement.messageDAO.insertFailedRecipientDelivery(
                eq(messageID),
                eq(conversationID.toDao()),
                eq(expectedFailedUsers),
                eq(RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED)
            )
        }.wasInvoked(exactly = once)
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

        coVerify {

            arrangement.messageDAO.insertFailedRecipientDelivery(
                eq(messageID),
                eq(conversationID.toDao()),
                eq(expectedFailedUsers),
                eq(RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER)
            )

        }
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

        coVerify {
            arrangement.mlsMessageApi.sendMessage(
                matches {
                    it.value.contentToString() == ByteArray(0).contentToString()
                },
            )
        }.wasInvoked(exactly = once)

        verify {
            arrangement.sendMessagePartialFailureMapper.fromMlsDTO(any())
        }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.messageDAO.moveMessages(
                eq(sourceConversationId.toDao()),
                eq(targetConversationId.toDao())
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.messageDAO.updateLegalHoldMessageMembers(
                eq(TEST_CONVERSATION_ID.toDao()),
                eq(TEST_MESSAGE_ID),
                eq(newUsersList.map { it.toDao() })
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.messageDAO.getLastMessagesByConversations(eq(conversationIds.map { it.toDao() }))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val messageApi = mock(MessageApi::class)

        @Mock
        val mlsMessageApi = mock(MLSMessageApi::class)

        @Mock
        val messageDAO = mock(MessageDAO::class)

        @Mock
        val sendMessageFailureMapper = mock(SendMessageFailureMapper::class)

        @Mock
        val sendMessagePartialFailureMapper = mock(SendMessagePartialFailureMapper::class)

        @Mock
        val messageMapper = mock(MessageMapper::class)
        suspend fun withMockedMessages(messages: List<MessageEntity>): Arrangement {
            coEvery {
                messageDAO.getMessagesByConversationAndVisibility(any(), any(), any(), any())
            }.returns(flowOf(messages))
            coEvery {
                messageDAO.getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(any(), any())
            }.returns(messages.map { it.id })
            return this
        }

        fun withMappedMessageModel(message: Message.Regular): Arrangement {
            every {
                messageMapper.fromEntityToMessage(any())
            }.returns(message)
            return this
        }

        fun withMappedAssetMessageModel(message: AssetMessage): Arrangement {
            every {
                messageMapper.fromAssetEntityToAssetMessage(any())
            }.returns(message)
            return this
        }

        fun withMappedMessageModel(result: Message.Regular, param: MessageEntity.Regular): Arrangement {
            every {
                messageMapper.fromEntityToMessage(eq(param))
            }.returns(result)
            return this
        }

        fun withMappedMessageEntity(message: MessageEntity.Regular): Arrangement {
            every {
                messageMapper.fromMessageToEntity(any())
            }.returns(message)
            return this
        }

        suspend fun withSuccessfulMessageDelivery(timestamp: String): Arrangement {
            coEvery { messageApi.qualifiedSendMessage(any(), any()) }
                .returns(
                    NetworkResponse.Success(
                        QualifiedSendMessageResponse.MessageSent(timestamp, mapOf(), mapOf(), mapOf()),
                        emptyMap(),
                        201
                    )
                )
            return this
        }

        fun withFailedToSendMlsMapping(failedToSend: List<UserId>) = apply {
            every {
                sendMessagePartialFailureMapper.fromMlsDTO(any())
            }.returns(MessageSent(TEST_DATETIME, failedToSend))
        }

        fun withFailedToSendMapping(failedToSend: List<UserId>) = apply {
            every {
                sendMessagePartialFailureMapper.fromDTO(any())
            }.returns(
                MessageSent(TEST_DATETIME, failedToSend)
            )
        }

        suspend fun withMlsSendMessageResponse(
            timestamp: SendMLSMessageResponse = SendMLSMessageResponse(
                TEST_DATETIME,
                listOf(),
                listOf()
            )
        ): Arrangement {
            coEvery { mlsMessageApi.sendMessage(any()) }
                .returns(
                    NetworkResponse.Success(
                        timestamp,
                        emptyMap(),
                        201
                    )
                )
            return this
        }

        suspend fun withSuccessfulMessageBroadcasting(timestamp: String): Arrangement {
            coEvery { messageApi.qualifiedBroadcastMessage(any()) }
                .returns(
                    NetworkResponse.Success(
                        QualifiedSendMessageResponse.MessageSent(timestamp, mapOf(), mapOf(), mapOf()),
                        emptyMap(),
                        201
                    )
                )
            return this
        }

        suspend fun withUpdateMessageAfterSend() = apply {
            coEvery {
                messageDAO.promoteMessageToSentUpdatingServerTime(any(), any(), any(), any())
            }.returns(Unit)
        }

        suspend fun withMovingToAnotherConversationSucceeding() = apply {
            coEvery {
                messageDAO.moveMessages(any(), any())
            }.returns(Unit)
        }

        suspend fun withMovingToAnotherConversationFailingWith(throwable: Throwable) = apply {
            coEvery {
                messageDAO.moveMessages(any(), any())
            }.throws(throwable)
        }

        suspend fun withSelectedMessagePosition(
            conversationId: QualifiedIDEntity,
            messageId: String,
            result: Int
        ) = apply {
            coEvery {
                messageDAO.getSearchedConversationMessagePosition(eq(conversationId), eq(messageId))
            }.returns(result)
        }

        suspend fun withAssetMessages(
            conversationId: QualifiedIDEntity,
            result: List<AssetMessageEntity>
        ) = apply {
            coEvery {
                messageDAO.getImageMessageAssets(eq(conversationId), any(), any(), any())
            }.returns(result)
        }

        suspend fun withGetLastMessagesByConversations(result: Map<QualifiedIDEntity, MessageEntity>) = apply {
            coEvery {
                messageDAO.getLastMessagesByConversations(any())
            }.returns(result)
        }

        suspend fun withInsertOrIgnoreMessage(result: InsertMessageResult) = apply {
            coEvery {
                messageDAO.insertOrIgnoreMessage(any(), any(), any())
            }.returns(result)
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

        suspend fun withInsertFailedRecipients() = apply {
            coEvery {
                messageDAO.insertFailedRecipientDelivery(any(), any(), any(), any())
            }.returns(Unit)
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
    }
}
