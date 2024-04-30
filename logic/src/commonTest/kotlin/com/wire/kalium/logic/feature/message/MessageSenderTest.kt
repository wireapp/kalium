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
import com.wire.kalium.logic.data.message.BroadcastMessage
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.BroadcastMessageTarget
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.message.MessageTarget
import com.wire.kalium.logic.data.message.RecipientEntry
import com.wire.kalium.logic.data.message.SessionEstablisher
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.prekey.UsersWithoutSessions
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.failure.LegalHoldEnabledForConversationFailure
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.FEDERATION_MESSAGE_FAILURE
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.MESSAGE_SENT_TIME
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.TEST_MEMBER_2
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.TEST_PROTOCOL_INFO_FAILURE
import com.wire.kalium.logic.feature.message.MessageSenderTest.Arrangement.Companion.arrange
import com.wire.kalium.logic.feature.message.ephemeral.EphemeralMessageDeletionHandler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.mls.StaleEpochVerifierArrangement
import com.wire.kalium.logic.util.arrangement.mls.StaleEpochVerifierArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.util.thenReturnSequentially
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration

class MessageSenderTest {
    @Test
    fun givenAllStepsSucceed_WhenSendingOutgoingMessage_ThenReturnSuccess() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
        }
    }

    @Test
    fun givenGettingConversationProtocolFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(getConversationProtocolFailing = true)
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(TEST_PROTOCOL_INFO_FAILURE), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenGettingConversationRecipientsFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(getConversationsRecipientFailing = true)
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(Arrangement.TEST_CORE_FAILURE), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenPreparingRecipientsForNewOutgoingMessageFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(prepareRecipientsForNewOutGoingMessageFailing = true)
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(Arrangement.TEST_CORE_FAILURE), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenCreatingOutgoingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(createOutgoingEnvelopeFailing = true)
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(Arrangement.TEST_CORE_FAILURE), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendingEnvelopeFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val failure = CoreFailure.Unknown(Throwable("some exception"))
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(sendEnvelopeWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(failure), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendMlsMessageFails_whenSendingMlsMessage_thenReturnFailureAndHandleFailureProperly() {

        // given
        val failure = CoreFailure.Unknown(Throwable("some exception"))
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withSendMlsMessage(sendMlsMessageWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(failure), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    // Message was sent, better to keep it as pending, than wrongfully marking it as failed
    @Test
    fun givenUpdatingMessageStatusToSuccessFails_WhenSendingOutgoingMessage_ThenReturnSuccess() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(updateMessageStatusFailing = true)
            withPromoteMessageToSentUpdatingServerTime()
        }

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
    fun givenSendingOfEnvelopeFailsDueToLackOfConnection_whenSendingOutgoingMessage_thenFailureShouldBeHandledProperly() {
        // given
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(sendEnvelopeWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(failure), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenSendingOfEnvelopeFailsDueToLackOfConnection_whenSendingOutgoingMessage_thenFailureShouldBePropagated() {
        // given
        val failure = Either.Left(NetworkFailure.NoNetworkConnection(null))
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(sendEnvelopeWithResult = failure)
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            assertEquals(failure, result)
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingMlsMessage_thenVerifyStaleEpoch() {
        // given
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withSendMlsMessage()
            withSendOutgoingMlsMessage(Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE), times = 1)
            withWaitUntilLiveOrFailure()
            withPromoteMessageToSentUpdatingServerTime()
            withVerifyEpoch(Either.Right(Unit))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.staleEpochVerifier)
                .suspendFunction(arrangement.staleEpochVerifier::verifyEpoch)
                .with(eq(Arrangement.TEST_CONVERSATION_ID))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingMlsMessage_thenRetryAfterSyncIsLive() {
        // given
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withSendMlsMessage()
            withSendOutgoingMlsMessage(Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE), times = 1)
            withWaitUntilLiveOrFailure()
            withPromoteMessageToSentUpdatingServerTime()
            withVerifyEpoch(Either.Right(Unit))
        }

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
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withSendMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

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
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withSendMlsMessage(sendMlsMessageWithResult = Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE))
            withWaitUntilLiveOrFailure(failing = true)
            withVerifyEpoch(Either.Right(Unit))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(Arrangement.TEST_CORE_FAILURE), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenClientTargets_WhenSendingOutgoingMessage_ThenCallSendEnvelopeWithCorrectTargets() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        val message = Message.Signaling(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null
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
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        val message = Message.Signaling(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.Sent,
            isSelfMessage = true,
            expirationData = null
        )

        val messageTarget = MessageTarget.Conversation()

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
    fun givenARemoteProteusConversationFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val failure = FEDERATION_MESSAGE_FAILURE
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(sendEnvelopeWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(failure), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenARemoteMLSConversationFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val failure = FEDERATION_MESSAGE_FAILURE
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withWaitUntilLiveOrFailure()
            withSendMlsMessage(sendMlsMessageWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(eq(failure), anything(), anything(), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenARemoteProteusConversationPartiallyFails_WhenSendingOutgoingMessage_ThenReturnSuccessAndPersistFailedRecipients() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(
                sendEnvelopeWithResult = Either.Right(
                    MessageSent(
                        time = MESSAGE_SENT_TIME,
                        failedToConfirmClients = listOf(Arrangement.TEST_MEMBER_1)
                    )
                )
            )
            withFailedClientsPartialSuccess()
            withPromoteMessageToSentUpdatingServerTime()
            withSendMessagePartialSuccess()
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistRecipientsDeliveryFailure)
                .with(anything(), anything(), eq(listOf(Arrangement.TEST_MEMBER_1)))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenARemoteProteusConversationPartiallyFails_WithNoClientsWhenSendingAMessage_ThenReturnSuccessAndPersistFailedClientsAndFailedToSend() {
        // given
        val failedRecipient = UsersWithoutSessions(listOf(TEST_MEMBER_2))
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(
                sendEnvelopeWithResult = Either.Right(
                    MessageSent(
                        time = MESSAGE_SENT_TIME,
                        failedToConfirmClients = failedRecipient.users
                    )
                )
            )
            withFailedClientsPartialSuccess()
            withPrepareRecipientsForNewOutgoingMessage(false, failedRecipient)
            withPromoteMessageToSentUpdatingServerTime()
            withSendMessagePartialSuccess()
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistNoClientsToDeliverFailure)
                .with(anything(), anything(), eq(listOf(TEST_MEMBER_2)))
                .wasInvoked(exactly = twice)
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistRecipientsDeliveryFailure)
                .with(anything(), anything(), eq(listOf(TEST_MEMBER_2)))
                .wasNotInvoked()
        }
    }

    @Test
    fun givenAllTargets_WhenBroadcastOutgoingMessage_ThenCallBroadcastEnvelopeWithCorrectTargets() {
        // given
        val recipients = listOf(
            Arrangement.TEST_RECIPIENT_1,
            Arrangement.TEST_RECIPIENT_2
        )
        val (arrangement, messageSender) = arrange {
            withPrepareRecipientsForNewOutgoingMessage()
            withPromoteMessageToSentUpdatingServerTime()
            withCreateOutgoingBroadcastEnvelope()
            withAllRecipients(recipients to listOf())
            withBroadcastEnvelope()
        }

        val message = BroadcastMessage(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.Sent,
            isSelfMessage = false
        )

        val option = BroadcastMessageOption.ReportSome(listOf())

        arrangement.testScope.runTest {
            // when
            val result = messageSender.broadcastMessage(
                message = message,
                target = BroadcastMessageTarget.AllUsers(100)
            )

            // then
            result.shouldSucceed()

            verify(arrangement.sessionEstablisher)
                .suspendFunction(arrangement.sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .with(eq(listOf(Arrangement.TEST_RECIPIENT_1, Arrangement.TEST_RECIPIENT_2)))
                .wasInvoked(exactly = once)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::broadcastEnvelope)
                .with(anything(), eq(option))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAllTargets_WhenBroadcastOutgoingMessageWithLimit_ThenCallBroadcastEnvelopeWithCorrectTargets() {
        // given
        val senderUserId = UserId("userValue", "userDomain")
        val senderClientId = ClientId("clientId")
        val recipients = listOf(
            Arrangement.TEST_RECIPIENT_2,
            Arrangement.TEST_RECIPIENT_3,
            Arrangement.TEST_RECIPIENT_1,
            Recipient(senderUserId, listOf(senderClientId, ClientId("mySecondClientId")))
        )
        val (arrangement, messageSender) = arrange {
            withPrepareRecipientsForNewOutgoingMessage()
            withPromoteMessageToSentUpdatingServerTime()
            withCreateOutgoingBroadcastEnvelope()
            withAllRecipients(recipients to listOf())
            withBroadcastEnvelope()
        }

        val message = BroadcastMessage(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = false
        )

        val option = BroadcastMessageOption.ReportSome(listOf(Arrangement.TEST_MEMBER_3, Arrangement.TEST_MEMBER_1))

        arrangement.testScope.runTest {
            // when
            messageSender.broadcastMessage(
                message = message,
                target = BroadcastMessageTarget.AllUsers(2)
            )

            // then
            verify(arrangement.sessionEstablisher)
                .suspendFunction(arrangement.sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .with(matching { it.size == 2 })
                .wasInvoked(exactly = once)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::broadcastEnvelope)
                .with(anything(), eq(option))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenOnlyTeamTargets_WhenBroadcastOutgoingMessage_ThenCallBroadcastEnvelopeWithCorrectTargets() {
        // given
        val senderUserId = UserId("userValue", "userDomain")
        val senderClientId = ClientId("clientId")
        val teamRecipients = listOf(
            Arrangement.TEST_RECIPIENT_2,
            Recipient(senderUserId, listOf(senderClientId, ClientId("mySecondClientId")))
        )
        val otherRecipients = listOf(
            Arrangement.TEST_RECIPIENT_1,
            Arrangement.TEST_RECIPIENT_3,
        )
        val (arrangement, messageSender) = arrange {
            withPrepareRecipientsForNewOutgoingMessage()
            withPromoteMessageToSentUpdatingServerTime()
            withCreateOutgoingBroadcastEnvelope()
            withAllRecipients(teamRecipients to otherRecipients)
            withBroadcastEnvelope()
        }

        val message = BroadcastMessage(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = false
        )

        val option = BroadcastMessageOption.ReportSome(listOf(Arrangement.TEST_MEMBER_1, Arrangement.TEST_MEMBER_3))

        arrangement.testScope.runTest {
            // when
            messageSender.broadcastMessage(
                message = message,
                target = BroadcastMessageTarget.OnlyTeam(100)
            )

            // then
            verify(arrangement.sessionEstablisher)
                .suspendFunction(arrangement.sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .with(matching { it.size == 2 })
                .wasInvoked(exactly = once)

            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::broadcastEnvelope)
                .with(anything(), eq(option))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenASuccess_WhenSendingEditMessage_ThenUpdateMessageIdButDoNotUpdateCreationDate() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
            withUpdateTextMessage()
        }

        val originalMessageId = "original_id"
        val editedMessageId = "edited_id"
        val content = MessageContent.TextEdited(originalMessageId, "", listOf())
        val message = Message.Signaling(
            id = editedMessageId,
            content = content,
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE_STRING,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.Pending,
            isSelfMessage = false,
            expirationData = null
        )

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(message = message)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateTextMessage)
                .with(anything(), eq(content), eq(editedMessageId), anything())
                .wasInvoked(exactly = once)
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::promoteMessageToSentUpdatingServerTime)
                .with(anything(), eq(editedMessageId), eq(null), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenASuccess_WhenSendingRegularMessage_ThenDoNotUpdateMessageIdButUpdateCreationDateToServerDate() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }
        val message = TestMessage.TEXT_MESSAGE

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(message = message)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::updateTextMessage)
                .with(anything(), anything(), anything(), anything())
                .wasNotInvoked()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::promoteMessageToSentUpdatingServerTime)
                .with(anything(), eq(TestMessage.TEXT_MESSAGE.id), matching { it != null }, anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAllStepsSucceed_WhenSendingOutgoingSelfDeleteMessage_ThenTheTimerShouldStart() {
        val duration = Duration.parse("PT1M")
        val message = TestMessage.TEXT_MESSAGE.copy(
            expirationData = Message.ExpirationData(duration)
        )

        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(message)

            // then
            result.shouldSucceed()
            verify(arrangement.selfDeleteMessageSenderHandler)
                .function(arrangement.selfDeleteMessageSenderHandler::enqueueSelfDeletion)
                .with(eq(message))
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenError_WhenSendingOutgoingSelfDeleteMessage_ThenTheTimerShouldNotStart() {
        val duration = Duration.parse("PT1M")
        val message = TestMessage.TEXT_MESSAGE.copy(
            expirationData = Message.ExpirationData(duration)
        )

        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage(true, true)
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(message)

            // then
            result.shouldFail()
            verify(arrangement.selfDeleteMessageSenderHandler)
                .function(arrangement.selfDeleteMessageSenderHandler::enqueueSelfDeletion)
                .with(eq(message))
                .wasNotInvoked()
        }
    }

    @Test
    fun givenARemoteMlsConversationPartiallyFails_whenSendingAMessage_ThenReturnSuccessAndPersistFailedToSendUsers() {
        // given
        val (arrangement, messageSender) = arrange {
            withCommitPendingProposals()
            withSendMlsMessage(
                sendMlsMessageWithResult = Either.Right(MessageSent(MESSAGE_SENT_TIME, listOf(TEST_MEMBER_2))),
            )
            withWaitUntilLiveOrFailure()
            withPromoteMessageToSentUpdatingServerTime()
            withSendMessagePartialSuccess()
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistRecipientsDeliveryFailure)
                .with(anything(), anything(), eq(listOf(TEST_MEMBER_2)))
                .wasInvoked(once)
        }
    }

    @Test
    fun givenProteusSendMessageFailure_WhenSendingMessage_ThenHandleFailureProperly() {
        // given
        val failure = ProteusSendMessageFailure(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        val message = TestMessage.TEXT_MESSAGE
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withSendEnvelope(Either.Left(failure), Either.Right(MessageSent(MESSAGE_SENT_TIME))) // to avoid loop - fail then succeed
            withPromoteMessageToSentUpdatingServerTime()
            withHandleLegalHoldMessageSendFailure(Either.Right(false))
            withHandleClientsHaveChangedFailure()
        }
        arrangement.testScope.runTest {
            // when
            messageSender.sendMessage(message)
            // then
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleClientsHaveChangedFailure)
                .with(eq(failure), eq(message.conversationId))
                .wasInvoked()
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleMessageSendFailure)
                .with(eq(message.conversationId), eq(message.date), anything())
                .wasInvoked()
        }
    }

    @Test
    fun givenProteusSendMessageFailure_WhenBroadcastingMessage_ThenHandleFailureProperly() {
        // given
        val failure = ProteusSendMessageFailure(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        val message = TestMessage.BROADCAST_MESSAGE
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withAllRecipients(listOf(Arrangement.TEST_RECIPIENT_1) to listOf())
            withCreateOutgoingBroadcastEnvelope()
            withBroadcastEnvelope(Either.Left(failure), Either.Right(TestMessage.TEST_DATE_STRING)) // to avoid loop - fail then succeed
            withHandleLegalHoldMessageSendFailure(Either.Right(false))
            withHandleClientsHaveChangedFailure()
        }
        arrangement.testScope.runTest {
            // when
            messageSender.broadcastMessage(message, BroadcastMessageTarget.AllUsers(100))
            // then
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleClientsHaveChangedFailure)
                .with(eq(failure), eq(null))
                .wasInvoked()
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleMessageSendFailure)
                .with(anything(), anything(), anything())
                .wasNotInvoked()
        }
    }

    @Test
    fun givenProteusSendMessageFailureAndLegalHoldEnabledForConversation_WhenSendingMessage_ThenDoNotRetrySendingAfterHandlingFailure() {
        // given
        val failure = ProteusSendMessageFailure(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        val message = TestMessage.TEXT_MESSAGE
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withSendEnvelope(Either.Left(failure), Either.Right(MessageSent(MESSAGE_SENT_TIME))) // to avoid loop - fail then succeed
            withHandleLegalHoldMessageSendFailure(Either.Right(true))
            withHandleClientsHaveChangedFailure()
        }
        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(message)
            // then
            result.shouldFail() {
                assertIs<LegalHoldEnabledForConversationFailure>(it)
                assertEquals(message.id, it.messageId)
            }
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::sendEnvelope)
                .with(eq(message.conversationId), anything(), anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenProteusSendMessageFailureAndLegalHoldNotEnabledForConversation_WhenSendingMessage_ThenRetrySendingAfterHandlingFailure() {
        // given
        val failure = ProteusSendMessageFailure(emptyMap(), emptyMap(), emptyMap(), emptyMap())
        val message = TestMessage.TEXT_MESSAGE
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withSendEnvelope(Either.Left(failure), Either.Right(MessageSent(MESSAGE_SENT_TIME))) // to avoid loop - fail then succeed
            withPromoteMessageToSentUpdatingServerTime()
            withHandleLegalHoldMessageSendFailure(Either.Right(false))
            withHandleClientsHaveChangedFailure()
        }
        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendMessage(message)
            // then
            result.shouldSucceed()
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::sendEnvelope)
                .with(eq(message.conversationId), anything(), anything())
                .wasInvoked(exactly = twice)
        }
    }

    private class Arrangement(private val block: Arrangement.() -> Unit):
        StaleEpochVerifierArrangement by StaleEpochVerifierArrangementImpl()
    {
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
        val userRepository = configure(mock(UserRepository::class)) { stubsUnitByDefault = true }

        @Mock
        val selfDeleteMessageSenderHandler = mock(EphemeralMessageDeletionHandler::class)

        @Mock
        val legalHoldHandler = mock(LegalHoldHandler::class)

        val testScope = TestScope()

        private val messageSendingInterceptor = object : MessageSendingInterceptor {
            override suspend fun prepareMessage(message: Message.Sendable): Either<CoreFailure, Message.Sendable> {
                return Either.Right(message)
            }
        }

        fun arrange() = run {
            block()
            this@Arrangement to MessageSenderImpl(
                messageRepository = messageRepository,
                conversationRepository = conversationRepository,
                mlsConversationRepository = mlsConversationRepository,
                syncManager = syncManager,
                messageSendFailureHandler = messageSendFailureHandler,
                legalHoldHandler = legalHoldHandler,
                sessionEstablisher = sessionEstablisher,
                messageEnvelopeCreator = messageEnvelopeCreator,
                mlsMessageCreator = mlsMessageCreator,
                messageSendingInterceptor = messageSendingInterceptor,
                userRepository = userRepository,
                enqueueSelfDeletion = { message, expirationData ->
                    selfDeleteMessageSenderHandler.enqueueSelfDeletion(
                        message,
                        expirationData
                    )
                },
                staleEpochVerifier = staleEpochVerifier,
                scope = testScope
            )
        }

        fun withGetMessageById(failing: Boolean = false) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) Either.Left(StorageFailure.DataNotFound) else Either.Right(TestMessage.TEXT_MESSAGE))
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
                .thenReturn(Either.Left(TEST_PROTOCOL_INFO_FAILURE))
        }

        fun withGetConversationRecipients(failing: Boolean = false) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationRecipients)
                .whenInvokedWith(anything())
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(listOf(TEST_RECIPIENT_1)))
        }

        fun withPrepareRecipientsForNewOutgoingMessage(
            failing: Boolean = false,
            usersFailing: UsersWithoutSessions = UsersWithoutSessions.EMPTY // only relevant if failing true
        ) = apply {
            given(sessionEstablisher)
                .suspendFunction(sessionEstablisher::prepareRecipientsForNewOutgoingMessage)
                .whenInvokedWith(anything())
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(usersFailing))
        }

        fun withCommitPendingProposals(failing: Boolean = false) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::commitPendingProposals)
                .whenInvokedWith(anything())
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(Unit))
        }

        fun withCreateOutgoingEnvelope(failing: Boolean = false) = apply {
            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(TEST_MESSAGE_ENVELOPE))
        }

        fun withCreateOutgoingBroadcastEnvelope(failing: Boolean = false) = apply {
            given(messageEnvelopeCreator)
                .suspendFunction(messageEnvelopeCreator::createOutgoingBroadcastEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(TEST_MESSAGE_ENVELOPE))
        }

        fun withBroadcastEnvelope(result: Either<CoreFailure, String> = Either.Right(TestMessage.TEST_DATE_STRING)) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::broadcastEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        fun withBroadcastEnvelope(vararg result: Either<CoreFailure, String>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::broadcastEnvelope)
                .whenInvokedWith(anything(), anything())
                .thenReturnSequentially(*result)
        }

        fun withCreateOutgoingMlsMessage(failing: Boolean = false) = apply {
            given(mlsMessageCreator)
                .suspendFunction(mlsMessageCreator::createOutgoingMLSMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(TEST_MLS_MESSAGE))
        }

        fun withSendEnvelope(result: Either<CoreFailure, MessageSent> = Either.Right(TestMessage.TEST_MESSAGE_SENT)) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(result)
        }

        fun withSendEnvelope(vararg result: Either<CoreFailure, MessageSent>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::sendEnvelope)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturnSequentially(*result)
        }

        fun withSendOutgoingMlsMessage(
            result: Either<CoreFailure, MessageSent> = Either.Right(MessageSent(MESSAGE_SENT_TIME)),
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
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(Unit))
        }

        fun withWaitUntilLiveOrFailure(failing: Boolean = false) = apply {
            given(syncManager)
                .suspendFunction(syncManager::waitUntilLiveOrFailure)
                .whenInvoked()
                .thenReturn(if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(Unit))
        }

        fun withPromoteMessageToSentUpdatingServerTime() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::promoteMessageToSentUpdatingServerTime)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateTextMessage() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::updateTextMessage)
                .whenInvokedWith(anything(), anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withAllRecipients(recipients: Pair<List<Recipient>, List<Recipient>>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::getAllRecipients)
                .whenInvoked()
                .thenReturn(Either.Right(recipients))
        }

        @Suppress("LongParameterList")
        fun withSendProteusMessage(
            getConversationProtocolFailing: Boolean = false,
            getConversationsRecipientFailing: Boolean = false,
            prepareRecipientsForNewOutGoingMessageFailing: Boolean = false,
            createOutgoingEnvelopeFailing: Boolean = false,
            sendEnvelopeWithResult: Either<CoreFailure, MessageSent>? = null,
            updateMessageStatusFailing: Boolean = false
        ) = apply {
            withGetMessageById()
            if (getConversationProtocolFailing) withGetProtocolInfoFailing() else withGetProtocolInfo()
            withGetConversationRecipients(getConversationsRecipientFailing)
            withPrepareRecipientsForNewOutgoingMessage(prepareRecipientsForNewOutGoingMessageFailing)
            withCreateOutgoingEnvelope(createOutgoingEnvelopeFailing)
            if (sendEnvelopeWithResult != null) withSendEnvelope(sendEnvelopeWithResult) else withSendEnvelope()
            withUpdateMessageStatus(updateMessageStatusFailing)
        }

        fun withEnqueueSelfDeleteMessage() = apply {
            given(selfDeleteMessageSenderHandler)
                .function(selfDeleteMessageSenderHandler::enqueueSelfDeletion)
                .whenInvokedWith(anything())
                .thenReturn(Unit)
        }

        fun withSendMlsMessage(
            sendMlsMessageWithResult: Either<CoreFailure, MessageSent>? = null,
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

        fun withFailedClientsPartialSuccess() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::persistNoClientsToDeliverFailure)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(Either.Right(Unit))
        }

        fun withHandleLegalHoldMessageSendFailure(result: Either<CoreFailure, Boolean> = Either.Right(false)) = apply {
            given(legalHoldHandler)
                .suspendFunction(legalHoldHandler::handleMessageSendFailure)
                .whenInvokedWith(anything(), anything(), anything())
                .then { _, _, handleFailure ->
                    handleFailure() // simulate the handler calling the handleFailure function
                    result
                }
        }

        fun withHandleClientsHaveChangedFailure(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            given(messageSendFailureHandler)
                .suspendFunction(messageSendFailureHandler::handleClientsHaveChangedFailure)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }

        companion object {
            fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

            val TEST_CONVERSATION_ID = TestConversation.ID
            const val TEST_MESSAGE_UUID = "messageUuid"
            val MESSAGE_SENT_TIME = DateTimeUtil.currentIsoDateTimeString()
            val TEST_MLS_MESSAGE = MLSMessageApi.Message("message".toByteArray())
            val TEST_CORE_FAILURE = CoreFailure.Unknown(Throwable("an error"))
            val TEST_PROTOCOL_INFO_FAILURE = StorageFailure.DataNotFound
            val GROUP_ID = GroupID("groupId")
            val MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
                GROUP_ID,
                Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                0UL,
                Instant.DISTANT_PAST,
                CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
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
            val FEDERATION_MESSAGE_FAILURE = NetworkFailure.FederatedBackendFailure.General("error")
            val TEST_CONTACT_CLIENT_1 = ClientId("clientId1")
            val TEST_CONTACT_CLIENT_2 = ClientId("clientId2")
            val TEST_CONTACT_CLIENT_3 = ClientId("clientId3")
            val TEST_CONTACT_CLIENT_4 = ClientId("clientId4")
            val TEST_MEMBER_1 = UserId("value1", "domain1")
            val TEST_RECIPIENT_1 = Recipient(TEST_MEMBER_1, listOf(TEST_CONTACT_CLIENT_1, TEST_CONTACT_CLIENT_2))
            val TEST_MEMBER_2 = UserId("value2", "domain2")
            val TEST_MEMBER_3 = UserId("value3", "domain3")
            val TEST_RECIPIENT_2 = Recipient(TEST_MEMBER_2, listOf(TEST_CONTACT_CLIENT_3))
            val TEST_RECIPIENT_3 = Recipient(TEST_MEMBER_3, listOf(TEST_CONTACT_CLIENT_4))
            val TEST_RECIPIENT_ENTRY = RecipientEntry(TEST_MEMBER_1, listOf())
            val TEST_MESSAGE_ENVELOPE = MessageEnvelope(
                senderClientId = ClientId(
                    value = "testValue",
                ),
                recipients = listOf(TEST_RECIPIENT_ENTRY),
                dataBlob = null
            )
        }
    }
}
