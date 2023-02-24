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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.FEDERATION_MESSAGE_FAILURE
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.MESSAGE_SENT_TIME
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.TEST_MEMBER_2
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DateTimeUtil
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.Times
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MessageSenderTest {
    @Test
    fun givenAllStepsSucceed_WhenSendingOutgoingMessage_ThenReturnSuccess() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage()
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
        }
    }

    @Test
    fun givenGettingConversationProtocolFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(getConversationProtocolFailing = true)
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenGettingConversationRecipientsFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(getConversationsRecipientFailing = true)
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenPreparingRecipientsForNewOutgoingMessageFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(prepareRecipientsForNewOutGoingMessageFailing = true)
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenCreatingOutgoingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(createOutgoingEnvelopeFailing = true)
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailed() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(sendEnvelopeWithResult = Either.Left(CoreFailure.Unknown(Throwable("some exception"))))
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendMlsMessageFails_whenSendingMlsMessage_thenReturnFailureAndSetMessageStatusToFailed() {

        // given
        val (arrangement, messageSender) = Arrangement()
            .withCommitPendingProposals()
            .withSendMlsMessage(sendMlsMessageWithResult = Either.Left(CoreFailure.Unknown(Throwable("some exception"))))
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    // Message was sent, better to keep it as pending, than wrongfully marking it as failed
    @Test
    fun givenUpdatingMessageStatusToSuccessFails_WhenSendingOutgoingMessage_ThenReturnSuccess() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(updateMessageStatusFailing = true)
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::promoteMessageToSentUpdatingServerTime)
                .with(anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    // Message was sent, better to keep it as pending, than wrongfully marking it as failed
    @Test
    fun givenUpdatingMessageDateFails_WhenSendingOutgoingMessage_ThenMarkMessageAsSentAndReturnSuccess() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(updateMessageDateFailing = true)
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::promoteMessageToSentUpdatingServerTime)
                .with(anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendingOfEnvelopeFailsDueToLackOfConnection_whenSendingOutgoingMessage_thenItShouldScheduleRetry() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(sendEnvelopeWithResult = Either.Left(NetworkFailure.NoNetworkConnection(null)))
            .arrange()

        arrangement.testScope.runTest {
            // when
            messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            verify(arrangement.messageSendingScheduler)
                .function(arrangement.messageSendingScheduler::scheduleSendingOfPendingMessages)
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendingOfEnvelopeFailsDueToLackOfConnection_whenSendingOutgoingMessage_thenFailureShouldBePropagated() {
        // given
        val failure = Either.Left(NetworkFailure.NoNetworkConnection(null))
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(sendEnvelopeWithResult = failure)
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            assertEquals(failure, result)
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingMlsMessage_thenRetryAfterSyncIsLive() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withCommitPendingProposals()
            .withSendMlsMessage()
            .withSendOutgoingMlsMessage(Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE), times = 1)
            .withWaitUntilLiveOrFailure()
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::sendMLSMessage)
                .with(eq(Arrangement.TEST_CONVERSATION_ID), eq(Arrangement.TEST_MLS_MESSAGE))
                .wasInvoked(twice)
        }
    }

    @Test
    fun givenPendingProposals_whenSendingMlsMessage_thenProposalsAreCommitted() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withCommitPendingProposals()
            .withSendMlsMessage()
            .withSendOutgoingMlsMessage()
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.mlsConversationRepository)
                .suspendFunction(arrangement.mlsConversationRepository::commitPendingProposals)
                .with(eq(Arrangement.GROUP_ID))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingMlsMessage_thenGiveUpIfSyncIsPending() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withCommitPendingProposals()
            .withSendMlsMessage(sendMlsMessageWithResult = Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE))
            .withWaitUntilLiveOrFailure(failing = true)
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenClientTargets_WhenSendingOutgoingMessage_ThenCallSendEnvelopeWithCorrectTargets() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage()
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        val message = Message.Signaling(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.SENT,
        )

        val messageTarget = MessageTarget.Client(
            recipients = listOf(
                Arrangement.TEST_RECIPIENT_1,
                Arrangement.TEST_RECIPIENT_2
            )
        )

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(
                message = message,
                messageTarget = messageTarget
            )

            // then
            result.shouldSucceed()
            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::getConversationRecipients)
                .with(anything())
                .wasInvoked(exactly = Times(0))

            verify(arrangement.sessionEstablisher)
                .suspendFunction(arrangement.sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .with(eq(listOf(Arrangement.TEST_RECIPIENT_1, Arrangement.TEST_RECIPIENT_2)))
                .wasInvoked(exactly = once)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::sendEnvelope)
                .with(eq(message.conversationId), anything(), eq(messageTarget))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenConversationTarget_WhenSendingOutgoingMessage_ThenCallSendEnvelopeWithCorrectTargets() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage()
            .withPromoteMessageToSentUpdatingServerTime()
            .arrange()

        val message = Message.Signaling(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.SENT,
        )

        val messageTarget = MessageTarget.Conversation

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(
                message = message,
                messageTarget = messageTarget
            )

            // then
            result.shouldSucceed()
            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::getConversationRecipients)
                .with(anything())
                .wasInvoked(exactly = once)

            verify(arrangement.sessionEstablisher)
                .suspendFunction(arrangement.sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .with(eq(listOf(Arrangement.TEST_RECIPIENT_1)))
                .wasInvoked(exactly = once)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::sendEnvelope)
                .with(eq(message.conversationId), anything(), eq(messageTarget))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenARemoteProteusConversationFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailedRemotely() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(sendEnvelopeWithResult = Either.Left(FEDERATION_MESSAGE_FAILURE))
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED_REMOTELY), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenARemoteMLSConversationFails_WhenSendingOutgoingMessage_ThenReturnFailureAndSetMessageStatusToFailedRemotely() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withCommitPendingProposals()
            .withWaitUntilLiveOrFailure()
            .withSendMlsMessage(sendMlsMessageWithResult = Either.Left(FEDERATION_MESSAGE_FAILURE))
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateMessageStatus)
                .with(eq(MessageEntity.Status.FAILED_REMOTELY), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenARemoteProteusConversationPartiallyFails_WhenSendingOutgoingMessage_ThenReturnSuccessAndPersistFailedRecipients() {
        // given
        val (arrangement, messageSender) = Arrangement()
            .withSendProteusMessage(sendEnvelopeWithResult = Either.Right(MessageSent(MESSAGE_SENT_TIME, listOf(TEST_MEMBER_2))))
            .withPromoteMessageToSentUpdatingServerTime()
            .withSendMessagePartialSuccess()
            .arrange()

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistRecipientsDeliveryFailure)
                .with(anything(), anything(), eq(listOf(TEST_MEMBER_2)))
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {
        @Mock
        val messageRepository: MessageRepository = mock(MessageRepository::class)

        @Mock
        val messageSendFailureHandler: MessageSendFailureHandler = mock(MessageSendFailureHandler::class)

        @Mock
        val conversationRepository: ConversationRepository = mock(ConversationRepository::class)

        @Mock
        val mlsConversationRepository: MLSConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val sessionEstablisher = mock(SessionEstablisher::class)

        @Mock
        val messageEnvelopeCreator: MessageEnvelopeCreator = mock(MessageEnvelopeCreator::class)

        @Mock
        val mlsMessageCreator: MLSMessageCreator = mock(MLSMessageCreator::class)

        @Mock
        val syncManager = configure(mock(SyncManager::class)) { stubsUnitByDefault = true }

        @Mock
        val messageSendingScheduler = configure(mock(MessageSendingScheduler::class)) { stubsUnitByDefault = true }

        val testScope = TestScope()

        private val messageSendingInterceptor = object : MessageSendingInterceptor {
            override suspend fun prepareMessage(message: Message.Sendable): Either<CoreFailure, Message.Sendable> {
                return Either.Right(message)
            }
        }

        fun arrange() = this to MessageSenderImpl(
            messageRepository = messageRepository,
            conversationRepository = conversationRepository,
            mlsConversationRepository = mlsConversationRepository,
            syncManager = syncManager,
            messageSendFailureHandler = messageSendFailureHandler,
            sessionEstablisher = sessionEstablisher,
            messageEnvelopeCreator = messageEnvelopeCreator,
            mlsMessageCreator = mlsMessageCreator,
            messageSendingScheduler = messageSendingScheduler,
            messageSendingInterceptor = messageSendingInterceptor,
            scope = testScope
        )

        fun withGetMessageById(failing: Boolean = false) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(TestMessage.TEXT_MESSAGE))
        }

        fun withGetProtocolInfo(protocolInfo: Conversation.ProtocolInfo = Conversation.ProtocolInfo.Proteus) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(protocolInfo))
        }

        fun withGetProtocolInfoFailing() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
        }

        fun withGetConversationRecipients(failing: Boolean = false) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationRecipients)
                .whenInvokedWith(anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(listOf(TEST_RECIPIENT_1)))
        }

        fun withPrepareRecipientsForNewOutgoingMessage(failing: Boolean = false) = apply {
            given(sessionEstablisher)
                .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .whenInvokedWith(anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(Unit))
        }

        fun withCommitPendingProposals(failing: Boolean = false) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::commitPendingProposals)
                .whenInvokedWith(anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(Unit))
        }

        fun withCreateOutgoingEnvelope(failing: Boolean = false) = apply {
            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(TEST_MESSAGE_ENVELOPE))
        }

        fun withCreateOutgoingMlsMessage(failing: Boolean = false) = apply {
            given(mlsMessageCreator)
                .suspendFunction(mlsMessageCreator::createOutgoingMLSMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(TEST_MLS_MESSAGE))
        }

        fun withSendEnvelope(result: Either<CoreFailure, MessageSent> = Either.Right(TestMessage.TEST_MESSAGE_SENT)) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withSendOutgoingMlsMessage(
            result: Either<CoreFailure, String> = Either.Right(MESSAGE_SENT_TIME),
            times: Int = Int.MAX_VALUE
        ) = apply {
            var invocationCounter = 0
            given(messageRepository)
                .suspendFunction(messageRepository::sendMLSMessage)
                .whenInvokedWith(matching { invocationCounter += 1; invocationCounter <= times }, anything())
                .thenReturn(result)
        }

        fun withUpdateMessageStatus(failing: Boolean = false) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateMessageStatus)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(Unit))
        }

        fun withWaitUntilLiveOrFailure(failing: Boolean = false) = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .thenReturn(if (failing) TEST_CORE_FAILURE else Either.Right(Unit))
        }

        fun withPromoteMessageToSentUpdatingServerTime() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::promoteMessageToSentUpdatingServerTime)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        @Suppress("LongParameterList")
        fun withSendProteusMessage(
            getConversationProtocolFailing: Boolean = false,
            getConversationsRecipientFailing: Boolean = false,
            prepareRecipientsForNewOutGoingMessageFailing: Boolean = false,
            createOutgoingEnvelopeFailing: Boolean = false,
            sendEnvelopeWithResult: Either<CoreFailure, MessageSent>? = null,
            updateMessageStatusFailing: Boolean = false,
            updateMessageDateFailing: Boolean = false,
        ) =
            apply {
                withGetMessageById()
                if (getConversationProtocolFailing) withGetProtocolInfoFailing() else withGetProtocolInfo()
                withGetConversationRecipients(getConversationsRecipientFailing)
                withPrepareRecipientsForNewOutgoingMessage(prepareRecipientsForNewOutGoingMessageFailing)
                withCreateOutgoingEnvelope(createOutgoingEnvelopeFailing)
                if (sendEnvelopeWithResult != null) withSendEnvelope(sendEnvelopeWithResult) else withSendEnvelope()
                withUpdateMessageStatus(updateMessageStatusFailing)
            }

        fun withSendMlsMessage(
            sendMlsMessageWithResult: Either<CoreFailure, String>? = null,
        ) = apply {
            withGetMessageById()
            withGetProtocolInfo(protocolInfo = MLS_PROTOCOL_INFO)
            withCreateOutgoingMlsMessage()
            if (sendMlsMessageWithResult != null) withSendOutgoingMlsMessage(sendMlsMessageWithResult) else withSendOutgoingMlsMessage()
            withUpdateMessageStatus()
        }

        fun withSendMessagePartialSuccess(
            result: Either<CoreFailure, Unit> = Either.Right(Unit),
        ) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::persistRecipientsDeliveryFailure)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(result)
        }

        companion object {
            val TEST_CONVERSATION_ID = TestConversation.ID
            const val TEST_MESSAGE_UUID = "messageUuid"
            val TEST_MESSAGE_ENVELOPE = MessageEnvelope(
                senderClientId = ClientId(
                    value = "testValue",
                ),
                recipients = listOf(),
                dataBlob = null
            )
            val MESSAGE_SENT_TIME = DateTimeUtil.currentIsoDateTimeString()
            val TEST_MLS_MESSAGE = MLSMessageApi.Message("message".toByteArray())
            val TEST_CORE_FAILURE = Either.Left(CoreFailure.Unknown(Throwable("an error")))
            val GROUP_ID = GroupID("groupId")
            val MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
                GROUP_ID,
                Conversation.ProtocolInfo.MLS.GroupState.ESTABLISHED,
                0UL,
                Instant.DISTANT_PAST,
                Conversation.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
            val MLS_STALE_MESSAGE_FAILURE = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
                        409,
                        "The conversation epoch in a message is too old",
                        "mls-stale-message"
                    )
                )
            )
            val FEDERATION_MESSAGE_FAILURE = NetworkFailure.FederatedBackendFailure
            val TEST_CONTACT_CLIENT_1 = ClientId("clientId1")
            val TEST_CONTACT_CLIENT_2 = ClientId("clientId2")
            val TEST_CONTACT_CLIENT_3 = ClientId("clientId3")
            val TEST_MEMBER_1 = UserId("value1", "domain1")
            val TEST_RECIPIENT_1 = Recipient(TEST_MEMBER_1, listOf(TEST_CONTACT_CLIENT_1, TEST_CONTACT_CLIENT_2))
            val TEST_MEMBER_2 = UserId("value2", "domain2")
            val TEST_RECIPIENT_2 = Recipient(TEST_MEMBER_2, listOf(TEST_CONTACT_CLIENT_3))
        }
    }
}
