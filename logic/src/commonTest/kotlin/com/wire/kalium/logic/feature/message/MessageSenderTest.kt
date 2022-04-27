package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.failure.SendMessageFailure
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.verify
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
    fun givenAllStepsSucceed_WhenSendingOutgoingMessage_ThenReturnSuccess() {
        runTest {
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = true,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = true,
                sendEnvelope = true,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            assertIs<Either.Right<Unit>>(result)
        }
    }

    @Test
    fun givenGettingConversationProtocolFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        runTest {
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = false,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = true,
                sendEnvelope = true,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
        }
    }

    @Test
    fun givenGettingConversationRecipientsFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        runTest {
            //given
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = false,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = true,
                sendEnvelope = true,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
            //when
        }
    }

    @Test
    fun testing3() {
        runTest {
            //given
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = true,
                getConversationsRecipient = false,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = true,
                sendEnvelope = true,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
        }
    }

    @Test
    fun givenPreparingRecipentsForNewOutgoingMessageFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        runTest {
            //given
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = true,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = false,
                createOutgoingEnvelope = true,
                sendEnvelope = true,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
        }
    }

    @Test
    fun givenCreatingOutgoingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        runTest {
            //given
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = true,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = false,
                sendEnvelope = true,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
        }
    }

    @Test
    fun givenSendingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        runTest {
            //given
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = true,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = true,
                sendEnvelope = false,
                updateMessageStatus = true
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
        }
    }

    @Test
    fun givenUpdatingMessageStatusToSuccessFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        runTest {
            //given
            //given
            setupGivenSuccessResults(
                getMessageById = true,
                getConversationProtocol = true,
                getConversationsRecipient = true,
                prepareRecipientsForNewOutGoingMessage = true,
                createOutgoingEnvelope = true,
                sendEnvelope = true,
                updateMessageStatus = false
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(ConversationId("testId", "testDomain"), "testId")
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(Times(1))

            assertIs<Either.Left<Unit>>(result)
        }
    }

    private fun setupGivenSuccessResults(
        getMessageById: Boolean,
        getConversationProtocol: Boolean,
        getConversationsRecipient: Boolean,
        prepareRecipientsForNewOutGoingMessage: Boolean,
        createOutgoingEnvelope: Boolean,
        sendEnvelope: Boolean,
        updateMessageStatus: Boolean,
    ) {
        given(messageRepository)
            .suspendFunction(messageRepository::getMessageById)
            .whenInvokedWith(anything(), anything())
            .thenReturn(if (getMessageById) Either.Right(TestMessage.TEXT_MESSAGE) else TEST_CORE_FAILURE)

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationProtocolInfo)
            .whenInvokedWith(anything())
            .thenReturn(if (getConversationProtocol) Either.Right(ConversationEntity.ProtocolInfo.Proteus) else Either.Left(StorageFailure.DataNotFound))

        given(conversationRepository)
            .suspendFunction(conversationRepository::getConversationRecipients)
            .whenInvokedWith(anything())
            .thenReturn(if (getConversationsRecipient) Either.Right(listOf(TEST_RECIPIENT_1)) else TEST_CORE_FAILURE)

        given(sessionEstablisher)
            .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
            .whenInvokedWith(anything())
            .thenReturn(if (prepareRecipientsForNewOutGoingMessage) Either.Right(Unit) else TEST_CORE_FAILURE)

        given(messageEnvelopeCreator)
            .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
            .whenInvokedWith(anything(), anything())
            .thenReturn(if (createOutgoingEnvelope) Either.Right(TEST_MESSAGE_ENVELOPE) else TEST_CORE_FAILURE)

        given(messageRepository)
            .suspendFunction(messageRepository::sendEnvelope)
            .whenInvokedWith(anything(), anything())
            .thenReturn(if (sendEnvelope) Either.Right(Unit) else Either.Left(SendMessageFailure.Unknown(Throwable("some exception"))))

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessageStatus)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(if (updateMessageStatus) Either.Right(Unit) else TEST_CORE_FAILURE)
    }

    private companion object {
        val TEST_MESSAGE_ENVELOPE = MessageEnvelope(
            senderClientId = ClientId(
                value = "testValue",
            ), recipients = listOf(), null
        )

        val TEST_CORE_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
        val TEST_CONTACT_CLIENT_1 = ClientId("clientId1")
        val TEST_CONTACT_CLIENT_2 = ClientId("clientId2")
        val TEST_MEMBER_1 = Member(UserId("value1", "domain1"))
        val TEST_RECIPIENT_1 = Recipient(TEST_MEMBER_1, listOf(TEST_CONTACT_CLIENT_1, TEST_CONTACT_CLIENT_2))
    }

}
