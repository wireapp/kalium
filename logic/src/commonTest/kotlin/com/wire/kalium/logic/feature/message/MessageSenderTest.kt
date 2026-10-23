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

import com.wire.kalium.logic.feature.asset.upload.AssetUploadCompletion
import com.wire.kalium.logic.feature.asset.upload.TrackedAssetUploadImpl
import com.wire.kalium.logic.feature.asset.upload.UploadAssetUseCaseImpl
import com.wire.kalium.logic.feature.asset.upload.UploadAssetMessageMetadata
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderMock
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import kotlinx.coroutines.awaitCancellation
import okio.Path.Companion.toPath
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Recipient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageSent
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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.feature.mls.FakeMLSMissingUsersRejectionHandler
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.logic.util.shouldSucceed

import com.wire.kalium.messaging.sending.BroadcastMessage
import com.wire.kalium.messaging.sending.BroadcastMessageTarget
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSenderImpl
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.every
import dev.mokkery.matcher.matching
import dev.mokkery.answering.returns
import dev.mokkery.answering.calls
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.mock
import dev.mokkery.verify
import com.wire.kalium.cryptography.CryptoTransactionContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runCurrent
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration
import com.wire.kalium.messaging.sending.MessageEnvelopeCreator
import com.wire.kalium.messaging.sending.MLSMessageCreator

class MessageSenderTest {
    @Test
    fun givenOfflineEditOfSentMessage_whenCancellingPending_thenDoNotRemoveItLocally() {
        val (arrangement, sender) = arrange { }
        arrangement.testScope.runTest {
            val edited = TestMessage.TEXT_MESSAGE.copy(
                isSelfMessage = true,
                status = Message.Status.Pending,
                editStatus = Message.EditStatus.Edited(Instant.UNIX_FIRST_DATE)
            )
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } returns Either.Right(edited)
            assertEquals(Either.Right(false), sender.cancelPendingMessage(edited.conversationId, edited.id))
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageRepository.deleteMessage(any(), any()) }
        }
    }

    @Test
    fun givenCancellationQueuedFirst_whenStaleSendWaitsForTransaction_thenItDoesNotSend() {
        val (arrangement, sender) = arrange { }
        arrangement.testScope.runTest {
            val pending = TestMessage.assetMessage().copy(isSelfMessage = true)
            var stored: Message? = pending
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls {
                stored?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
            }
            val deleting = CompletableDeferred<Unit>()
            val releaseDeletion = CompletableDeferred<Unit>()
            everySuspend { arrangement.messageRepository.deleteMessage(any(), any()) } calls {
                deleting.complete(Unit)
                releaseDeletion.await()
                stored = null
                Either.Right(Unit)
            }
            arrangement.serializeTransactions()
            val cancel = async { sender.cancelPendingMessage(pending.conversationId, pending.id) }
            deleting.await()
            val send = async { sender.sendMessage(pending) }
            runCurrent()
            assertFalse(send.isCompleted)
            releaseDeletion.complete(Unit)
            assertEquals(Either.Right(true), cancel.await())
            send.await().shouldSucceed()
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageRepository.sendEnvelope(any(), any(), any()) }
            verifySuspend(VerifyMode.exactly(0)) { arrangement.selfDeleteMessageSenderHandler.enqueueSelfDeletion(any(), any()) }
        }
    }

    @Test
    fun givenSendAlreadyInTransaction_whenCancellationWaits_thenSentMessageIsNotRemovedFromQueue() {
        val (arrangement, sender) = arrange { withSendProteusMessage() }
        arrangement.testScope.runTest {
            val pending = TestMessage.TEXT_MESSAGE.copy(isSelfMessage = true)
            var stored = pending
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { Either.Right(stored) }
            val promoting = CompletableDeferred<Unit>()
            val releaseSend = CompletableDeferred<Unit>()
            everySuspend { arrangement.messageRepository.promoteMessageToSentUpdatingServerTime(any(), any(), any(), any()) } calls {
                promoting.complete(Unit)
                releaseSend.await()
                stored = pending.copy(status = Message.Status.Sent)
                Either.Right(Unit)
            }
            arrangement.serializeTransactions()
            val send = async { sender.sendMessage(pending) }
            promoting.await()
            val cancel = async { sender.cancelPendingMessage(pending.conversationId, pending.id) }
            runCurrent()
            assertFalse(cancel.isCompleted)
            releaseSend.complete(Unit)
            send.await().shouldSucceed()
            assertEquals(Either.Right(false), cancel.await())
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageRepository.deleteMessage(any(), any()) }
        }
    }

    @Test
    fun givenMissingPendingMessage_whenCancelling_thenCancellationIsIdempotent() {
        val (arrangement, sender) = arrange { withGetMessageById(failing = true) }
        arrangement.testScope.runTest {
            assertEquals(Either.Right(true), sender.cancelPendingMessage(TestMessage.TEXT_MESSAGE.conversationId, TestMessage.TEXT_MESSAGE.id))
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageRepository.deleteMessage(any(), any()) }
        }
    }

    @Test
    fun givenForeignPendingMessage_whenCancelling_thenDoNotDeleteIt() {
        val (arrangement, sender) = arrange { }
        arrangement.testScope.runTest {
            val foreign = TestMessage.TEXT_MESSAGE.copy(isSelfMessage = false)
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } returns Either.Right(foreign)
            assertEquals(Either.Right(false), sender.cancelPendingMessage(foreign.conversationId, foreign.id))
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageRepository.deleteMessage(any(), any()) }
        }
    }

    @Test
    fun givenStorageFailure_whenCancelling_thenPropagateFailure() {
        val (arrangement, sender) = arrange { }
        arrangement.testScope.runTest {
            val failure = StorageFailure.Generic(IllegalStateException("database unavailable"))
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } returns Either.Left(failure)
            assertEquals(Either.Left(failure), sender.cancelPendingMessage(TestMessage.TEXT_MESSAGE.conversationId, TestMessage.TEXT_MESSAGE.id))
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageRepository.deleteMessage(any(), any()) }
        }
    }

    @Test
    fun givenLocallyDeletedPendingMessage_whenStaleSnapshotIsSent_thenNoEnvelopeIsSent() {
        val (arrangement, sender) = arrange { }
        arrangement.testScope.runTest {
            val pending = TestMessage.TEXT_MESSAGE.copy(isSelfMessage = true)
            var stored: Message? = pending
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls {
                stored?.let { Either.Right(it) } ?: Either.Left(StorageFailure.DataNotFound)
            }
            everySuspend { arrangement.messageRepository.deleteMessage(any(), any()) } calls {
                stored = null
                Either.Right(Unit)
            }
            val slowSync = mock<com.wire.kalium.logic.data.sync.SlowSyncRepository>()
            every { slowSync.slowSyncStatus } returns kotlinx.coroutines.flow.MutableStateFlow(
                com.wire.kalium.logic.data.sync.SlowSyncStatus.Complete
            )
            val delete = DeleteMessageUseCase(
                arrangement.messageRepository,
                mock(mode = MockMode.autoUnit),
                slowSync,
                sender,
                pending.senderUserId,
                mock(mode = MockMode.autoUnit),
                mock(mode = MockMode.autoUnit),
                mock(mode = MockMode.autoUnit),
                testKaliumDispatcher
            )

            delete(pending.conversationId, pending.id, false).toEither().shouldSucceed()
            assertEquals(Either.Right(false), sender.sendMessageWithOutcome(pending))
            sender.sendMessage(pending).shouldSucceed()
            val updateTransfer = mock<UpdateAssetMessageTransferStatusUseCase>()
            everySuspend { updateTransfer(any(), any(), any()) } returns UpdateTransferStatusResult.Success
            val upload = UploadAssetUseCaseImpl(
                mock(), sender, mock(), updateTransfer, mock(), mock(), AudioNormalizedLoudnessBuilderMock(),
                dispatcher = testKaliumDispatcher,
            )
            val metadata = UploadAssetMessageMetadata(
                conversationId = pending.conversationId,
                mimeType = "image/jpeg",
                assetId = UploadedAssetId("uploaded", "domain"),
                assetDataPath = "/unused".toPath(),
                assetDataSize = 1L,
                assetName = "image.jpg",
                assetWidth = null,
                assetHeight = null,
                otrKey = AES256Key(byteArrayOf()),
                sha256Key = SHA256Key(byteArrayOf(1)),
                audioLengthInMs = 0L,
                audioNormalizedLoudness = null,
            )
            val operation = TrackedAssetUploadImpl(
                pending.id,
                backgroundScope,
                retryOperation = { error("Deleted upload must not automatically retry") },
                // Deliberately hold the visibility monitor: only the sender observes the committed deletion.
                monitor = { awaitCancellation() },
                upload = { upload(pending, metadata) },
                onCancelled = { error("Deleted message must not be marked failed") },
            )
            assertEquals(AssetUploadCompletion.Cancelled, operation.awaitCompletion())
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.messageRepository.sendEnvelope(any(), any(), any())
            }
        }
    }

    @Test
    fun givenCancelledPendingSnapshot_whenSending_thenDoNotSendEnvelope() {
        val (arrangement, sender) = arrange { withGetMessageById(failing = true) }
        arrangement.testScope.runTest {
            sender.sendMessage(TestMessage.TEXT_MESSAGE).shouldSucceed()
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.messageRepository.sendEnvelope(any(), any(), any())
            }
        }
    }

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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    eq(TEST_PROTOCOL_INFO_FAILURE),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    eq(Arrangement.TEST_CORE_FAILURE),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    eq(Arrangement.TEST_CORE_FAILURE),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    eq(Arrangement.TEST_CORE_FAILURE),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(eq(failure), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun givenSendMlsMessageFails_whenSendingMlsMessage_thenReturnFailureAndHandleFailureProperly() {

        // given
        val failure = CoreFailure.Unknown(Throwable("some exception"))
        val (arrangement, messageSender) = arrange {
            withSendMlsMessage(sendMlsMessageWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(eq(failure), any(), any(), any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.promoteMessageToSentUpdatingServerTime(any(), any(), any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(eq(failure), any(), any(), any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.staleEpochVerifier.verifyEpoch(
                    any(),
                    eq(Arrangement.TEST_CONVERSATION_ID),
                    any(),
                    any()
                )
            }
        }
    }

    @Test
    fun givenGroupOutOfSyncResponse_whenSendingMlsMessage_thenCallFailureHandlerAndSendSuccessfullyAfterwards() {
        // given
        val missingUsers = listOf(
            TestUser.OTHER_USER_ID,
            TestUser.OTHER_USER_ID.copy(value = "value2")
        )
        val response = MLSFailure.MessageRejected(NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync(missingUsers))
        val (arrangement, messageSender) = arrange {
            withSendMlsMessage()
            withSendOutgoingMlsMessage(Either.Left(response), times = 1)
            withWaitUntilLiveOrFailure()
            withPromoteMessageToSentUpdatingServerTime()
            withVerifyEpoch(Either.Right(Unit))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            assertEquals(1, arrangement.mlsMissingUsersMessageRejectionHandler.callCount)
            result.shouldSucceed()
        }
    }

    @Test
    fun givenNoFailures_whenSendingMlsMessage_thenShouldNotCallMLSOutOfSyncHandler() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendMlsMessage()
            withWaitUntilLiveOrFailure()
            withPromoteMessageToSentUpdatingServerTime()
            withVerifyEpoch(Either.Right(Unit))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldSucceed()
            assertEquals(0, arrangement.mlsMissingUsersMessageRejectionHandler.callCount)
        }
    }

    @Test
    fun givenCallingMessageWhileMLSResetIsPending_whenSending_thenSkipMLSMessageCreationAndSending() {
        val pendingResetProtocolInfo = Arrangement.MLS_PROTOCOL_INFO.copy(
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET
        )
        val message = Message.Signaling(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling("calling-message"),
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE,
            senderUserId = TestUser.SELF.id,
            senderClientId = ClientId("clientId"),
            status = Message.Status.Pending,
            isSelfMessage = true,
            expirationData = null
        )
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(pendingResetProtocolInfo)
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(message)

            result.shouldSucceed()
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingMLSMessage(any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSMessage(any())
            }
        }
    }

    @Test
    fun givenMLSConversationAndClientTarget_whenSendingMessage_thenSendTargetedBatchInsteadOfApplicationMessage() {
        val messageTarget = MessageTarget.Client(listOf(Arrangement.TEST_RECIPIENT_1))
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTargetedMlsMessage()
            withSendOutgoingTargetedMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, messageTarget)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                    any(),
                    eq(Arrangement.GROUP_ID),
                    any(),
                    eq(messageTarget),
                    eq(false)
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSTargetedMessage(eq(Arrangement.TEST_MLS_TARGETED_MESSAGE_BATCH))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSMessage(any())
            }
        }
    }

    @Test
    fun givenMLSConversationAndConversationTargetIgnoringUsers_whenSendingMessage_thenSendTargetedBatch() {
        val messageTarget = MessageTarget.Conversation(usersToIgnore = setOf(TEST_MEMBER_2))
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTargetedMlsMessage()
            withSendOutgoingTargetedMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, messageTarget)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                    any(),
                    eq(Arrangement.GROUP_ID),
                    any(),
                    eq(messageTarget),
                    eq(false)
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSTargetedMessage(eq(Arrangement.TEST_MLS_TARGETED_MESSAGE_BATCH))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingMLSMessage(any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSMessage(any())
            }
        }
    }

    @Test
    fun givenMLSConversationAndConversationTargetIgnoringNoUsers_whenSendingMessage_thenSendApplicationMessage() {
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, MessageTarget.Conversation())

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun givenTargetedAndTransientMLSMessagesDisabled_whenSendingTransientTargetedMessage_thenSendApplicationMessageToWholeGroup() {
        val (arrangement, messageSender) = arrange {
            targetedAndTransientMLSMessagesEnabled = false
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, MessageTarget.Users(TEST_MEMBER_2), transient = true)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(any(), any(), any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTransientMLSMessage(any(), any(), any())
            }
        }
    }

    @Test
    fun givenTargetedAndTransientMLSMessagesDisabled_whenSendingToConversationIgnoringUsers_thenSendApplicationMessageToWholeGroup() {
        val (arrangement, messageSender) = arrange {
            targetedAndTransientMLSMessagesEnabled = false
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(
                Arrangement.TEST_SIGNALING_MESSAGE,
                MessageTarget.Conversation(usersToIgnore = setOf(TEST_MEMBER_2))
            )

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(any(), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun givenTargetedAndTransientMLSMessagesDisabled_whenSendingTransientMessage_thenSendApplicationMessageToWholeGroup() {
        val (arrangement, messageSender) = arrange {
            targetedAndTransientMLSMessagesEnabled = false
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, transient = true)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSTransientMessage(any())
            }
        }
    }

    @Test
    fun givenMLSConversationAndTargetWithoutGroupMembers_whenSendingMessage_thenSucceedWithoutSending() {
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTargetedMlsMessage(MLSMessageApi.TargetedMessageBatch(ByteArray(0)))
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, MessageTarget.Users(TEST_MEMBER_2))

            result.shouldSucceed()
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSTargetedMessage(any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSMessage(any())
            }
        }
    }

    @Test
    fun givenBackendWithoutTargetedMessages_whenSendingTargetedMLSMessage_thenFallBackToApplicationMessage() {
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTargetedMlsMessage()
            withSendOutgoingTargetedMlsMessage(Either.Left(NetworkFailure.FeatureNotSupported))
            withCreateOutgoingMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, MessageTarget.Users(TEST_MEMBER_2))

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSTargetedMessage(eq(Arrangement.TEST_MLS_TARGETED_MESSAGE_BATCH))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingTargetedMLSMessage_thenRetryWithSameTarget() {
        val messageTarget = MessageTarget.Users(TEST_MEMBER_2)
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTargetedMlsMessage()
            withSendOutgoingTargetedMlsMessage(
                Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE),
                Either.Right(MessageSent(MESSAGE_SENT_TIME))
            )
            withWaitUntilLiveOrFailure()
            withVerifyEpoch(Either.Right(Unit))
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, messageTarget)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                    any(),
                    any(),
                    any(),
                    eq(messageTarget),
                    eq(false)
                )
            }
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.messageRepository.sendMLSTargetedMessage(any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSMessage(any())
            }
        }
    }

    @Test
    fun givenMLSConversation_whenSendingTransientMessage_thenSendTransientMessageInsteadOfApplicationMessage() {
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTransientMlsMessage()
            withSendOutgoingTransientMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, transient = true)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSTransientMessage(eq(Arrangement.TEST_MLS_TRANSIENT_MESSAGE))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingMLSMessage(any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSMessage(any())
            }
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingTransientTargetedMLSMessage_thenRetryAsTransientTargetedMessage() {
        val messageTarget = MessageTarget.Users(TEST_MEMBER_2)
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTransientTargetedMlsMessage()
            withSendOutgoingTransientTargetedMlsMessage(
                Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE),
                Either.Right(MessageSent(MESSAGE_SENT_TIME))
            )
            withWaitUntilLiveOrFailure()
            withVerifyEpoch(Either.Right(Unit))
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, messageTarget, transient = true)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(
                    any(),
                    eq(Arrangement.GROUP_ID),
                    any(),
                    eq(messageTarget),
                    eq(true)
                )
            }
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.messageRepository.sendMLSTransientTargetedMessage(eq(Arrangement.TEST_MLS_TRANSIENT_TARGETED_MESSAGE_BATCH))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSTargetedMessage(any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSTransientMessage(any())
            }
        }
    }

    @Test
    fun givenBackendWithoutTransientMessages_whenSendingTransientMLSMessage_thenFallBackToApplicationMessage() {
        val (arrangement, messageSender) = arrange {
            withGetProtocolInfo(Arrangement.MLS_PROTOCOL_INFO)
            withCreateOutgoingTransientMlsMessage()
            withSendOutgoingTransientMlsMessage(Either.Left(NetworkFailure.FeatureNotSupported))
            withCreateOutgoingMlsMessage()
            withSendOutgoingMlsMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, transient = true)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSTransientMessage(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
        }
    }

    @Test
    fun givenProteusConversation_whenSendingTransientMessage_thenIgnoreTransientAndSendEnvelope() {
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
        }

        arrangement.testScope.runTest {
            val result = messageSender.sendMessage(Arrangement.TEST_SIGNALING_MESSAGE, transient = true)

            result.shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendEnvelope(any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTransientMLSMessage(any(), any(), any())
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.sendMLSTransientMessage(any())
            }
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingMlsMessage_thenRetryAfterSyncIsLive() {
        // given
        val (arrangement, messageSender) = arrange {
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
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.messageRepository.sendMLSMessage(eq(Arrangement.TEST_MLS_MESSAGE))
            }
        }
    }

    @Test
    fun givenReceivingStaleMessageError_whenSendingMlsMessage_thenGiveUpIfSyncIsPending() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendMlsMessage(sendMlsMessageWithResult = Either.Left(Arrangement.MLS_STALE_MESSAGE_FAILURE))
            withWaitUntilLiveOrFailure(failing = true)
            withVerifyEpoch(Either.Right(Unit))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                    eq(Arrangement.TEST_CORE_FAILURE),
                    any(),
                    any(),
                    any(),
                    any()
                )
            }
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
            date = TestMessage.TEST_DATE,
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
            verifySuspend(VerifyMode.not) {
                arrangement.conversationRepository.getConversationRecipients(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sessionEstablisher.prepareRecipientsForNewOutgoingMessage(
                    any(),
                    eq(
                        listOf(
                            Arrangement.TEST_RECIPIENT_1,
                            Arrangement.TEST_RECIPIENT_2
                        )
                    )
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendEnvelope(eq(message.conversationId), any(), eq(messageTarget))
            }
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
            date = TestMessage.TEST_DATE,
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.getConversationRecipients(any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sessionEstablisher.prepareRecipientsForNewOutgoingMessage(any(), eq(listOf(Arrangement.TEST_RECIPIENT_1)))
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendEnvelope(eq(message.conversationId), any(), eq(messageTarget))
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(eq(failure), any(), any(), any(), any())
            }
        }
    }

    @Test
    fun givenARemoteMLSConversationFails_WhenSendingOutgoingMessage_ThenReturnFailureAndHandleFailureProperly() {
        // given
        val failure = FEDERATION_MESSAGE_FAILURE
        val (arrangement, messageSender) = arrange {
            withWaitUntilLiveOrFailure()
            withSendMlsMessage(sendMlsMessageWithResult = Either.Left(failure))
        }

        arrangement.testScope.runTest {
            // when
            val result = messageSender.sendPendingMessage(Arrangement.TEST_CONVERSATION_ID, Arrangement.TEST_MESSAGE_UUID)

            // then
            result.shouldFail()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(eq(failure), any(), any(), any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.persistRecipientsDeliveryFailure(any(), any(), eq(listOf(Arrangement.TEST_MEMBER_1)))
            }
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
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.messageRepository.persistNoClientsToDeliverFailure(any(), any(), eq(listOf(TEST_MEMBER_2)))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.persistRecipientsDeliveryFailure(any(), any(), eq(listOf(TEST_MEMBER_2)))
            }
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
            withProteusTransactionReturning(Either.Right(Unit))
        }

        val message = BroadcastMessage(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            date = TestMessage.TEST_DATE,
            senderUserId = UserId("userValue", "userDomain"),
            senderClientId = ClientId("clientId"),
            status = Message.Status.Sent,
            isSelfMessage = false
        )

        val option = BroadcastMessageOption.ReportSome(listOf(
            Arrangement.TEST_RECIPIENT_1.id,
            Arrangement.TEST_RECIPIENT_2.id
        ))

        arrangement.testScope.runTest {
            // when
            val result = messageSender.broadcastMessage(
                message = message,
                target = BroadcastMessageTarget.AllUsers(100)
            )

            // then
            result.shouldSucceed()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sessionEstablisher.prepareRecipientsForNewOutgoingMessage(
                    any(),
                    eq(
                        listOf(
                            Arrangement.TEST_RECIPIENT_1,
                            Arrangement.TEST_RECIPIENT_2
                        )
                    )
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.broadcastEnvelope(any(), eq(option))
            }
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
            withProteusTransactionReturning(Either.Right(Unit))
        }

        val message = BroadcastMessage(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            date = TestMessage.TEST_DATE,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = false
        )

        val option = BroadcastMessageOption.ReportSome(listOf(Arrangement.TEST_MEMBER_2, Arrangement.TEST_MEMBER_3))

        arrangement.testScope.runTest {
            // when
            messageSender.broadcastMessage(
                message = message,
                target = BroadcastMessageTarget.AllUsers(2)
            )

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sessionEstablisher.prepareRecipientsForNewOutgoingMessage(any(), matching { it.size == 2 })
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.broadcastEnvelope(any(), eq(option))
            }
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
            withProteusTransactionReturning(Either.Right(Unit))
        }

        val message = BroadcastMessage(
            id = Arrangement.TEST_MESSAGE_UUID,
            content = MessageContent.Calling(""),
            date = TestMessage.TEST_DATE,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = Message.Status.Sent,
            isSelfMessage = false
        )

        val option = BroadcastMessageOption.ReportSome(listOf(Arrangement.TEST_RECIPIENT_2.id, senderUserId))

        arrangement.testScope.runTest {
            // when
            messageSender.broadcastMessage(
                message = message,
                target = BroadcastMessageTarget.OnlyTeam(100)
            )

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.sessionEstablisher.prepareRecipientsForNewOutgoingMessage(any(), matching { it.size == 2 })
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.broadcastEnvelope(any(), eq(option))
            }
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
            date = TestMessage.TEST_DATE,
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.updateTextMessage(any(), eq(content), eq(editedMessageId), any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.promoteMessageToSentUpdatingServerTime(any(), eq(editedMessageId), eq<Instant?>(null), any())
            }
        }
    }

    @Test
    fun givenASuccess_WhenSendingMultipartEditMessage_ThenUpdateMessageIdButDoNotUpdateCreationDate() {
        // given
        val (arrangement, messageSender) = arrange {
            withSendProteusMessage()
            withPromoteMessageToSentUpdatingServerTime()
            withUpdateMultipartMessage()
        }

        val originalMessageId = "original_id"
        val editedMessageId = "edited_id"
        val content = MessageContent.MultipartEdited(originalMessageId, "", listOf())
        val message = Message.Signaling(
            id = editedMessageId,
            content = content,
            conversationId = Arrangement.TEST_CONVERSATION_ID,
            date = TestMessage.TEST_DATE,
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.updateMultipartMessage(any(), eq(content), eq(editedMessageId), any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.promoteMessageToSentUpdatingServerTime(any(), eq(editedMessageId), eq<Instant?>(null), any())
            }
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
            verifySuspend(VerifyMode.not) {
                arrangement.messageRepository.updateTextMessage(any(), any(), any(), any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.promoteMessageToSentUpdatingServerTime(
                    any(),
                    eq(TestMessage.TEXT_MESSAGE.id),
                    matching { it != null },
                    any()
                )
            }
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
            verify(VerifyMode.exactly(1)) {
                arrangement.selfDeleteMessageSenderHandler.enqueueSelfDeletion(eq(message), any())
            }
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
            verify(VerifyMode.not) {
                arrangement.selfDeleteMessageSenderHandler.enqueueSelfDeletion(eq(message), any())
            }
        }
    }

    @Test
    fun givenARemoteMlsConversationPartiallyFails_whenSendingAMessage_ThenReturnSuccessAndPersistFailedToSendUsers() {
        // given
        val (arrangement, messageSender) = arrange {
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.persistRecipientsDeliveryFailure(any(), any(), eq(listOf(TEST_MEMBER_2)))
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleClientsHaveChangedFailure(any(), eq(failure), eq(message.conversationId))
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.legalHoldHandler.handleMessageSendFailure(eq(message.conversationId), eq(message.date), any())
            }
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
            withBroadcastEnvelope(Either.Left(failure), Either.Right(TestMessage.TEST_DATE)) // to avoid loop - fail then succeed
            withHandleLegalHoldMessageSendFailure(Either.Right(false))
            withHandleClientsHaveChangedFailure()
            withProteusTransactionReturning(Either.Right(Unit))
        }
        arrangement.testScope.runTest {
            // when
            messageSender.broadcastMessage(message, BroadcastMessageTarget.AllUsers(100))
            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSendFailureHandler.handleClientsHaveChangedFailure(any(), eq(failure), eq<ConversationId?>(null))
            }
            verifySuspend(VerifyMode.not) {
                arrangement.legalHoldHandler.handleMessageSendFailure(any(), any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.sendEnvelope(eq(message.conversationId), any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(2)) {
                arrangement.messageRepository.sendEnvelope(eq(message.conversationId), any(), any())
            }
        }
    }

    private class Arrangement(private val block: suspend Arrangement.() -> Unit) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {

        // Most MLS tests exercise targeted/transient sending; the production default (disabled) is tested explicitly.
        var targetedAndTransientMLSMessagesEnabled = true

        val messageRepository: MessageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val messageSendFailureHandler: MessageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)
        val conversationRepository: ConversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val sessionEstablisher = mock<SessionEstablisher>(mode = MockMode.autoUnit)
        val messageEnvelopeCreator: MessageEnvelopeCreator = mock<MessageEnvelopeCreator>(mode = MockMode.autoUnit)
        val mlsMessageCreator: MLSMessageCreator = mock<MLSMessageCreator>(mode = MockMode.autoUnit)
        val syncManager = mock<SyncManager>(mode = MockMode.autoUnit)
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val mlsMissingUsersMessageRejectionHandler = FakeMLSMissingUsersRejectionHandler()
        val selfDeleteMessageSenderHandler = mock<EphemeralMessageDeletionHandler>(mode = MockMode.autoUnit)
        val legalHoldHandler = mock<LegalHoldHandler>(mode = MockMode.autoUnit)
        val staleEpochVerifier = mock<StaleEpochVerifier>(mode = MockMode.autoUnit)

        val testScope = TestScope()

        private val messageSendingInterceptor = object : MessageSendingInterceptor {
            override suspend fun prepareMessage(message: Message.Sendable): Either<CoreFailure, Message.Sendable> {
                return Either.Right(message)
            }
        }

        fun arrange() = run {
            runBlocking {
                withGetMessageById()
                block()
                withTransactionReturning(Either.Right(Unit))
            }
            this@Arrangement to MessageSenderImpl(
                messages = messageRepository,
                transport = messageRepository,
                conversations = conversationRepository,
                session = SyncedSendingSession(syncManager, cryptoTransactionProvider),
                messageSendFailureHandler = messageSendFailureHandler,
                sessionEstablisher = sessionEstablisher,
                messageEnvelopeCreator = messageEnvelopeCreator,
                mlsMessageCreator = mlsMessageCreator,
                prepareMessage = messageSendingInterceptor::prepareMessage,
                legalHoldEnabledAfter = legalHoldHandler::handleMessageSendFailure,
                verifyEpoch = { context, conversationId -> staleEpochVerifier.verifyEpoch(context, conversationId) },
                handleGroupOutOfSync = mlsMissingUsersMessageRejectionHandler::handle,
                broadcastRecipients = userRepository::getAllRecipients,
                enqueueSelfDeletion = { message, expirationData ->
                    selfDeleteMessageSenderHandler.enqueueSelfDeletion(
                        message,
                        expirationData
                    )
                },
                scope = testScope,
                targetedAndTransientMLSMessagesEnabled = targetedAndTransientMLSMessagesEnabled
            )
        }

        @Suppress("UNCHECKED_CAST")
        suspend fun serializeTransactions() {
            // Model the existing CoreCrypto transaction queue, not a message-specific lock.
            val queue = Mutex()
            everySuspend { cryptoTransactionProvider.transaction<Any?>(any(), any()) } calls {
                val operation = it.args[1] as suspend (CryptoTransactionContext) -> Either<CoreFailure, Any?>
                queue.withLock { operation(transactionContext) }
            }
        }

        suspend fun withGetMessageById(failing: Boolean = false) = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns if (failing) Either.Left(StorageFailure.DataNotFound) else Either.Right(TestMessage.TEXT_MESSAGE)
        }

        suspend fun withGetProtocolInfo(protocolInfo: Conversation.ProtocolInfo = Conversation.ProtocolInfo.Proteus) = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(any())
            } returns Either.Right(protocolInfo)
        }

        suspend fun withGetProtocolInfoFailing() = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(any())
            } returns Either.Left(TEST_PROTOCOL_INFO_FAILURE)
        }

        suspend fun withVerifyEpoch(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                staleEpochVerifier.verifyEpoch(any(), any(), any(), any())
            } returns result
        }

        suspend fun withGetConversationRecipients(failing: Boolean = false) = apply {
            everySuspend {
                conversationRepository.getConversationRecipients(any())
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(listOf(TEST_RECIPIENT_1))
        }

        suspend fun withPrepareRecipientsForNewOutgoingMessage(
            failing: Boolean = false,
            usersFailing: UsersWithoutSessions = UsersWithoutSessions.EMPTY // only relevant if failing true
        ) = apply {
            everySuspend {
                sessionEstablisher.prepareRecipientsForNewOutgoingMessage(any(), any())
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(usersFailing)
        }

        suspend fun withCreateOutgoingEnvelope(failing: Boolean = false) = apply {
            everySuspend {
                messageEnvelopeCreator.createOutgoingEnvelope(any(), any(), any())
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(TEST_MESSAGE_ENVELOPE)
        }

        suspend fun withCreateOutgoingBroadcastEnvelope(failing: Boolean = false) = apply {
            everySuspend {
                messageEnvelopeCreator.createOutgoingBroadcastEnvelope(any(), any(), any())
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(TEST_MESSAGE_ENVELOPE)
        }

        suspend fun withBroadcastEnvelope(result: Either<CoreFailure, Instant> = Either.Right(TestMessage.TEST_DATE)) = apply {
            everySuspend {
                messageRepository.broadcastEnvelope(any(), any())
            } returns result
        }

        suspend fun withBroadcastEnvelope(vararg result: Either<CoreFailure, Instant>) = apply {
            everySuspend {
                messageRepository.broadcastEnvelope(any(), any())
            } sequentiallyReturns listOf(*result)
        }

        suspend fun withCreateOutgoingMlsMessage(failing: Boolean = false) = apply {
            everySuspend {
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingMLSMessage(any(), any(), any())
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(TEST_MLS_MESSAGE)
        }

        suspend fun withCreateOutgoingTargetedMlsMessage(
            batch: MLSMessageApi.TargetedMessageBatch = TEST_MLS_TARGETED_MESSAGE_BATCH
        ) = apply {
            everySuspend {
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(any(), any(), any(), any(), eq(false))
            } returns Either.Right(batch)
        }

        suspend fun withSendOutgoingTargetedMlsMessage(
            vararg result: Either<CoreFailure, MessageSent> = arrayOf(Either.Right(MessageSent(MESSAGE_SENT_TIME)))
        ) = apply {
            everySuspend {
                messageRepository.sendMLSTargetedMessage(any())
            } sequentiallyReturns listOf(*result)
        }

        suspend fun withCreateOutgoingTransientMlsMessage() = apply {
            everySuspend {
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTransientMLSMessage(any(), any(), any())
            } returns Either.Right(TEST_MLS_TRANSIENT_MESSAGE)
        }

        suspend fun withSendOutgoingTransientMlsMessage(
            vararg result: Either<CoreFailure, MessageSent> = arrayOf(Either.Right(MessageSent(MESSAGE_SENT_TIME)))
        ) = apply {
            everySuspend {
                messageRepository.sendMLSTransientMessage(any())
            } sequentiallyReturns listOf(*result)
        }

        suspend fun withCreateOutgoingTransientTargetedMlsMessage() = apply {
            everySuspend {
                mlsMessageCreator.prepareMLSGroupAndCreateOutgoingTargetedMLSMessage(any(), any(), any(), any(), eq(true))
            } returns Either.Right(TEST_MLS_TRANSIENT_TARGETED_MESSAGE_BATCH)
        }

        suspend fun withSendOutgoingTransientTargetedMlsMessage(
            vararg result: Either<CoreFailure, MessageSent> = arrayOf(Either.Right(MessageSent(MESSAGE_SENT_TIME)))
        ) = apply {
            everySuspend {
                messageRepository.sendMLSTransientTargetedMessage(any())
            } sequentiallyReturns listOf(*result)
        }

        suspend fun withSendEnvelope(result: Either<CoreFailure, MessageSent> = Either.Right(TestMessage.TEST_MESSAGE_SENT)) = apply {
            everySuspend {
                messageRepository.sendEnvelope(any(), any(), any())
            } returns result
        }

        suspend fun withSendEnvelope(vararg result: Either<CoreFailure, MessageSent>) = apply {
            everySuspend {
                messageRepository.sendEnvelope(any(), any(), any())
            } sequentiallyReturns listOf(*result)
        }

        suspend fun withSendOutgoingMlsMessage(
            result: Either<CoreFailure, MessageSent> = Either.Right(MessageSent(MESSAGE_SENT_TIME)),
            times: Int = Int.MAX_VALUE
        ) = apply {
            var invocationCounter = 0
            everySuspend {
                messageRepository.sendMLSMessage(matching { invocationCounter += 1; invocationCounter <= times })
            } returns result
        }

        suspend fun withUpdateMessageStatus(failing: Boolean = false) = apply {
            everySuspend {
                messageRepository.updateMessageStatus(any(), any(), any())
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(Unit)
        }

        suspend fun withWaitUntilLiveOrFailure(failing: Boolean = false) = apply {
            everySuspend {
                syncManager.waitUntilLiveOrFailure()
            } returns if (failing) Either.Left(TEST_CORE_FAILURE) else Either.Right(Unit)
        }

        suspend fun withPromoteMessageToSentUpdatingServerTime() = apply {
            everySuspend {
                messageRepository.promoteMessageToSentUpdatingServerTime(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateTextMessage() = apply {
            everySuspend {
                messageRepository.updateTextMessage(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withUpdateMultipartMessage() = apply {
            everySuspend {
                messageRepository.updateMultipartMessage(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withAllRecipients(recipients: Pair<List<Recipient>, List<Recipient>>) = apply {
            everySuspend {
                userRepository.getAllRecipients()
            } returns Either.Right(recipients)
        }

        @Suppress("LongParameterList")
        suspend fun withSendProteusMessage(
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
            every {
                selfDeleteMessageSenderHandler.enqueueSelfDeletion(any(), any())
            } returns Unit
        }

        suspend fun withSendMlsMessage(
            sendMlsMessageWithResult: Either<CoreFailure, MessageSent>? = null,
        ) = apply {
            withGetMessageById()
            withGetProtocolInfo(protocolInfo = MLS_PROTOCOL_INFO)
            withCreateOutgoingMlsMessage()
            if (sendMlsMessageWithResult != null) withSendOutgoingMlsMessage(sendMlsMessageWithResult) else withSendOutgoingMlsMessage()
            withUpdateMessageStatus()
        }

        suspend fun withSendMessagePartialSuccess(
            result: Either<CoreFailure, Unit> = Either.Right(Unit),
        ) = apply {
            everySuspend {
                messageRepository.persistRecipientsDeliveryFailure(any(), any(), any())
            } returns result
        }

        suspend fun withFailedClientsPartialSuccess() = apply {
            everySuspend {
                messageRepository.persistNoClientsToDeliverFailure(any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun withHandleLegalHoldMessageSendFailure(result: Either<CoreFailure, Boolean> = Either.Right(false)) = apply {
            everySuspend { legalHoldHandler.handleMessageSendFailure(any(), any(), any()) } calls { callArgs ->
                    @Suppress("UNCHECKED_CAST")
                    val handleFailure = callArgs.args[2] as (suspend () -> Either<CoreFailure, Unit>)
                    handleFailure() // simulate the handler calling the handleFailure function
                    result
                }
        }

        suspend fun withHandleClientsHaveChangedFailure(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            everySuspend {
                messageSendFailureHandler.handleClientsHaveChangedFailure(any(), any(), any())
            } returns result
        }

        companion object {
            fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

            val TEST_CONVERSATION_ID = TestConversation.ID
            const val TEST_MESSAGE_UUID = "messageUuid"
            val MESSAGE_SENT_TIME = Instant.UNIX_FIRST_DATE
            val TEST_MLS_MESSAGE = MLSMessageApi.Message("message".encodeToByteArray())
            val TEST_MLS_TARGETED_MESSAGE_BATCH = MLSMessageApi.TargetedMessageBatch("targeted".encodeToByteArray())
            val TEST_MLS_TRANSIENT_MESSAGE = MLSMessageApi.TransientMessage("transient".encodeToByteArray())
            val TEST_MLS_TRANSIENT_TARGETED_MESSAGE_BATCH =
                MLSMessageApi.TargetedMessageBatch("transient-targeted".encodeToByteArray())
            val TEST_SIGNALING_MESSAGE = Message.Signaling(
                id = TEST_MESSAGE_UUID,
                content = MessageContent.Calling("calling-message"),
                conversationId = TEST_CONVERSATION_ID,
                date = TestMessage.TEST_DATE,
                senderUserId = TestUser.SELF.id,
                senderClientId = ClientId("clientId"),
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            )
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
            val MLS_STALE_MESSAGE_FAILURE = NetworkFailure.MlsMessageRejectedFailure.StaleMessage
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
