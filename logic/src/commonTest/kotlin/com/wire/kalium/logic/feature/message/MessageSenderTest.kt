package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Mock
    private val messageSendingScheduler = configure(mock(MessageSendingScheduler::class)) { stubsUnitByDefault = true }

    @Mock
    private val timeParser = mock(TimeParser::class)

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
            mlsMessageCreator = mlsMessageCreator,
            timeParser = timeParser,
            messageSendingScheduler = messageSendingScheduler
        )

        given(timeParser)
            .function(timeParser::calculateMillisDifference)
            .whenInvokedWith(anything(), anything())
            .thenReturn(20L)
    }

    @Test
    fun givenAllStepsSucceed_WhenSendingOutgoingMessage_ThenReturnSuccess() = runTest {
        //given
        setupGivenSuccessResults()
        //when
        val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
        //then
        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenGettingConversationProtocolFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() = runTest {
        //given
        setupGivenSuccessResults(
            getConversationProtocol = false
        )
        //when
        val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
        //then
        verify(messageRepository)
            .suspendFunction(messageRepository::updateMessageStatus)
            .with(eq(MessageEntity.Status.FAILED), anything(), anything())
            .wasInvoked(exactly = once)

        assertIs<Either.Left<Unit>>(result)
    }

    @Test
    fun givenGettingConversationRecipientsFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() = runTest {
        //given
        setupGivenSuccessResults(
            getConversationsRecipient = false
        )
        //when
        val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
        //then
        verify(messageRepository)
            .suspendFunction(messageRepository::updateMessageStatus)
            .with(eq(MessageEntity.Status.FAILED), anything(), anything())
            .wasInvoked(exactly = once)

        assertIs<Either.Left<Unit>>(result)
    }

    @Test
    fun givenPreparingRecipentsForNewOutgoingMessageFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() =
        runTest {
            //given
            setupGivenSuccessResults(
                prepareRecipientsForNewOutGoingMessage = false
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)

            assertIs<Either.Left<Unit>>(result)
        }

    @Test
    fun givenCreatingOutgoingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() =
        runTest {
            //given
            setupGivenSuccessResults(
                createOutgoingEnvelope = false
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)

            assertIs<Either.Left<Unit>>(result)
        }

    @Test
    fun givenSendingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() =
        runTest {
            //given
            setupGivenSuccessResults(
                sendEnvelope = Either.Left(CoreFailure.Unknown(Throwable("some exception")))
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)

            assertIs<Either.Left<Unit>>(result)
        }

    @Test
    fun givenUpdatingMessageStatusToSuccessFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() =
        runTest {
            //given
            setupGivenSuccessResults(
                updateMessageStatus = false
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)

            assertIs<Either.Left<Unit>>(result)
        }

    @Test
    fun givenUpdatingMessageDateFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() =
        runTest {
            //given
            setupGivenSuccessResults(
                updateMessageDate = false
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)

            assertIs<Either.Left<Unit>>(result)
        }

    @Test
    fun givenUpdatePendingMessagesAddMillisToDate_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() =
        runTest {
            //given
            setupGivenSuccessResults(
                updateMessageDate = false
            )
            //when
            val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)
            //then
            verify(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)

            assertIs<Either.Left<Unit>>(result)
        }

    @Test
    fun givenSendingOfEnvelopeFailsDueToLackOfConnection_whenSendingOutgoingMessage_thenItShouldScheduleRetry() = runTest {
        setupGivenSuccessResults(
            sendEnvelope = Either.Left(NetworkFailure.NoNetworkConnection(null))
        )

        messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)

        verify(messageSendingScheduler)
            .suspendFunction(messageSendingScheduler::scheduleSendingOfPendingMessages)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSendingOfEnvelopeFailsDueToLackOfConnection_whenSendingOutgoingMessage_thenFailureShouldBePropagated() = runTest {
        val failure = Either.Left(NetworkFailure.NoNetworkConnection(null))
        setupGivenSuccessResults(
            sendEnvelope = failure
        )

        val result = messageSender.trySendingOutgoingMessageById(TEST_CONVERSATION_ID, TEST_MESSAGE_UUID)

        assertEquals(failure, result)
    }

    private fun setupGivenSuccessResults(
        getMessageById: Boolean = true,
        getConversationProtocol: Boolean = true,
        getConversationsRecipient: Boolean = true,
        prepareRecipientsForNewOutGoingMessage: Boolean = true,
        createOutgoingEnvelope: Boolean = true,
        updateMessageDate: Boolean = true,
        updatePendingMessagesAddMillisToDate: Boolean = true,
        sendEnvelope: Either<CoreFailure, String> = Either.Right("date"),
        updateMessageStatus: Boolean = true,
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
            .thenReturn(sendEnvelope)

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessageDate)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(if (updateMessageDate) Either.Right(Unit) else TEST_CORE_FAILURE)

        given(messageRepository)
            .suspendFunction(messageRepository::updatePendingMessagesAddMillisToDate)
            .whenInvokedWith(anything(), anything())
            .thenReturn(if (updatePendingMessagesAddMillisToDate) Either.Right(Unit) else TEST_CORE_FAILURE)

        given(messageRepository)
            .suspendFunction(messageRepository::updateMessageStatus)
            .whenInvokedWith(anything(), anything(), anything())
            .thenReturn(if (updateMessageStatus) Either.Right(Unit) else TEST_CORE_FAILURE)
    }

    private companion object {
        val TEST_CONVERSATION_ID = TestConversation.ID
        val TEST_MESSAGE_UUID = "messageUuid"
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
