package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.persistence.dao.ConversationEntity
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs


class MessageSenderTest {

    @Mock
    private val messageRepository: MessageRepository = mock(MessageRepository::class)

    @Mock
    private val messageSendFailureHandler: MessageSendFailureHandler = mock(MessageSendFailureHandler::class)

    @Mock
    private val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

    @Mock
    private val sessionEstablisher = mock(SessionEstablisher::class)

    @Mock
    private val messageEnvelopeCreator: MessageEnvelopeCreator = mock(MessageEnvelopeCreator::class)

    @Mock
    private val mlsMessageCreator: MLSMessageCreator = mock(MLSMessageCreator::class)

    @Mock
    private val syncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

    private lateinit var messageSender: MessageSender

    @BeforeTest
    fun setup() {
        messageSender = MessageSenderImpl(
            messageRepository = messageRepository,
            conversationRepository = conversationRepository,
            syncManager = syncManager,
            messageSendFailureHandler = messageSendFailureHandler,
            sessionEstablisher = sessionEstablisher,
            messageEnvelopeCreator = messageEnvelopeCreator,
            mlsMessageCreator = mlsMessageCreator
        )
    }

    @Test
    fun testing() {
        runTest {
            //given
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(ConversationEntity.ProtocolInfo.Proteus))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationRecipients)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(listOf(TEST_RECIPIENT_1)))

            given(sessionEstablisher)
                .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))

            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TEST_MESSAGE_ENVELOPE))

            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))

            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            assertIs<Either.Right<Unit>>(result)
        }
    }

    @Test
    fun testing1() {
        runTest {
            //given
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(ConversationEntity.ProtocolInfo.Proteus))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationRecipients)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(listOf(TEST_RECIPIENT_1)))

            given(sessionEstablisher)
                .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))

            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TEST_MESSAGE_ENVELOPE))

            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))

            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            assertIs<Either.Right<Unit>>(result)
        }
    }


    @Test
    fun testing2() {
        runTest {
            //given
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(ConversationEntity.ProtocolInfo.Proteus))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationRecipients)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(listOf(TEST_RECIPIENT_1)))

            given(sessionEstablisher)
                .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))

            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TEST_MESSAGE_ENVELOPE))

            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))

            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            assertIs<Either.Right<Unit>>(result)
        }
    }

    @Test
    fun testing3() {
        runTest {
            //given
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TestMessage.TEXT_MESSAGE))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(ConversationEntity.ProtocolInfo.Proteus))

            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationRecipients)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(listOf(TEST_RECIPIENT_1)))

            given(sessionEstablisher)
                .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(Unit))

            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(TEST_MESSAGE_ENVELOPE))

            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))

            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            assertIs<Either.Right<Unit>>(result)
        }
    }

    private companion object {
        val TEST_MESSAGE_ENVELOPE = MessageEnvelope(
            senderClientId = ClientId(
                value = "testValue",
            ), recipients = listOf(), null
        )

        val TEST_CONTACT_CLIENT_1 = ClientId("clientId1")
        val TEST_CONTACT_CLIENT_2 = ClientId("clientId2")
        val TEST_MEMBER_1 = Member(UserId("value1", "domain1"))
        val TEST_RECIPIENT_1 = Recipient(TEST_MEMBER_1, listOf(TEST_CONTACT_CLIENT_1, TEST_CONTACT_CLIENT_2))
        val TEST_CONTACT_CLIENT_3 = ClientId("clientId3")
        val TEST_MEMBER_2 = Member(UserId("value2", "domain2"))
        val TEST_RECIPIENT_2 = Recipient(TEST_MEMBER_2, listOf(TEST_CONTACT_CLIENT_3))
        val TEST_RECIPIENTS = listOf(TEST_RECIPIENT_1, TEST_RECIPIENT_2)
    }

}
