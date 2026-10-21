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

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageDataSource
import com.wire.kalium.logic.data.message.MessageMapperImpl
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.framework.TestAsset.mockedLongAssetData
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestMessage.ASSET_CONTENT
import com.wire.kalium.logic.framework.TestMessage.TEST_DATE
import com.wire.kalium.logic.framework.TestMessage.TEXT_MESSAGE
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.message.MessageEntity
import dev.mokkery.MockMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import dev.mokkery.matcher.matching
import dev.mokkery.answering.returns
import dev.mokkery.answering.calls
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class RetryFailedMessageUseCaseTest {

    @Test
    fun givenUnknownContent_whenRetrying_thenShouldRejectWithoutChangingStatusOrSending() =
        testUnsupportedRegularContent(MessageContent.Unknown())

    @Test
    fun givenFailedDecryptionContent_whenRetrying_thenShouldRejectWithoutChangingStatusOrSending() =
        testUnsupportedRegularContent(
            MessageContent.FailedDecryption(isDecryptionResolved = false, senderUserId = TEXT_MESSAGE.senderUserId)
        )

    @Test
    fun givenRestrictedAssetContent_whenRetrying_thenShouldRejectWithoutChangingStatusOrSending() =
        testUnsupportedRegularContent(MessageContent.RestrictedAsset("application/pdf", 42L, "test.pdf"))

    @Test
    fun givenCompositeEditedContent_whenRetrying_thenShouldRejectWithoutChangingStatusOrSending() =
        testUnsupportedSignalingContent(MessageContent.CompositeEdited(editMessageId = TEXT_MESSAGE.id))

    @Test
    fun givenIgnoredContent_whenRetrying_thenShouldRejectWithoutChangingStatusOrSending() =
        testUnsupportedSignalingContent(MessageContent.Ignored)

    private fun testUnsupportedRegularContent(content: MessageContent.Regular) {
        for (status in listOf(Message.Status.Failed, Message.Status.FailedRemotely)) {
            for (visibility in listOf(Message.Visibility.VISIBLE, Message.Visibility.DELETED)) {
                for (editStatus in listOf(Message.EditStatus.NotEdited, Message.EditStatus.Edited(TEST_DATE))) {
                    assertUnsupportedRetry(
                        TEXT_MESSAGE.copy(content = content, status = status, visibility = visibility, editStatus = editStatus)
                    )
                }
            }
        }
    }

    // Signaling content is not currently loaded from storage; cover the defensive guard at the repository boundary.
    private fun testUnsupportedSignalingContent(content: MessageContent.Signaling) {
        for (status in listOf(Message.Status.Failed, Message.Status.FailedRemotely)) {
            assertUnsupportedRetry(
                Message.Signaling(
                    id = TEXT_MESSAGE.id,
                    content = content,
                    conversationId = TEXT_MESSAGE.conversationId,
                    date = TEST_DATE,
                    senderUserId = TEXT_MESSAGE.senderUserId,
                    senderClientId = TEXT_MESSAGE.senderClientId,
                    status = status,
                    isSelfMessage = true,
                    expirationData = null
                )
            )
        }
    }

    private fun assertUnsupportedRetry(message: Message.Sendable) = runTest(testDispatcher.default) {
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()

        val result = useCase(message.id, message.conversationId)
        advanceUntilIdle()

        val failure = assertIs<MessageOperationResult.Failure>(result)
        val error = assertIs<CoreFailure.Unknown>(failure.error)
        val cause = assertIs<IllegalStateException>(error.rootCause)
        assertEquals("Message with ${message.content.getType()} content cannot be retried", cause.message)
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.messageRepository.updateMessageStatus(any(), any(), any())
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenDeletedFailedMessageInStorage_whenRetryingRepeatedly_thenShouldKeepDeletedContentAndStatus() =
        runTest(testDispatcher.default) {
            val database = TestUserDatabase(TestUser.ENTITY_ID, testDispatcher.default)
            try {
                val dao = database.builder.messageDAO
                val mapper = MessageMapperImpl(TEXT_MESSAGE.senderUserId)
                database.builder.userDAO.upsertUser(TestUser.ENTITY.copy(id = TEXT_MESSAGE.senderUserId.toDao()))
                database.builder.conversationDAO.insertConversation(TestConversation.ENTITY.copy(id = TEXT_MESSAGE.conversationId.toDao()))
                val repository = MessageDataSource(TEXT_MESSAGE.senderUserId, mock(), mock(), dao)
                val (arrangement, useCase) = Arrangement(repository)
                    .withSendMessage(Either.Right(Unit))
                    .arrange()

                for (status in listOf(Message.Status.Failed, Message.Status.FailedRemotely)) {
                    val message = TEXT_MESSAGE.copy(id = "deleted-$status", status = status)
                    val entity = mapper.fromMessageToEntity(message)
                    dao.insertOrIgnoreMessages(listOf(entity))
                    // Cancellation removes content but retains the failed status in storage.
                    dao.markMessageAsDeleted(message.id, entity.conversationId)
                    val deleted = assertIs<Message.Regular>(mapper.fromEntityToMessage(assertNotNull(
                        dao.getMessageById(message.id, entity.conversationId)
                    )))
                    assertIs<MessageContent.Unknown>(deleted.content)
                    assertEquals(Message.Visibility.DELETED, deleted.visibility)

                    repeat(2) {
                        assertIs<MessageOperationResult.Failure>(useCase(message.id, message.conversationId))
                        advanceUntilIdle()
                        val stored = assertNotNull(dao.getMessageById(message.id, entity.conversationId))
                        assertEquals(entity.status, stored.status)
                        assertEquals(MessageEntity.Visibility.DELETED, stored.visibility)
                        assertEquals(deleted.content, mapper.fromEntityToMessage(stored).content)
                    }
                }
                verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
            } finally {
                database.builder.close()
                database.delete()
            }
        }

    private fun testResendingWithGivenMessageStatus(
        status: Message.Status,
        shouldSucceed: Boolean
    ) =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = status)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            if (shouldSucceed) assertIs<MessageOperationResult.Success>(result)
            else assertIs<MessageOperationResult.Failure>(result)
        }

    @Test
    fun givenAFailedMessage_whenRetryingFailedMessage_thenShouldReturnSuccess() =
        testResendingWithGivenMessageStatus(Message.Status.Failed, true)

    @Test
    fun givenAFailedRemotelyMessage_whenRetryingFailedMessage_thenShouldReturnSuccess() =
        testResendingWithGivenMessageStatus(Message.Status.FailedRemotely, true)

    @Test
    fun givenASentMessage_whenRetryingFailedMessage_thenShouldReturnFailure() =
        testResendingWithGivenMessageStatus(Message.Status.Sent, false)

    @Test
    fun givenAReadMessage_whenRetryingFailedMessage_thenShouldReturnFailure() =
        testResendingWithGivenMessageStatus(Message.Status.Read(1), false)

    @Test
    fun givenAPendingMessage_whenRetryingFailedMessage_thenShouldReturnFailure() =
        testResendingWithGivenMessageStatus(Message.Status.Pending, false)

    @Test
    fun givenAnOldFailedMessage_whenRetryingFailedMessage_thenShouldSendAMessage() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.Failed, date = TEST_DATE - 365.days)
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.claimFailedMessageForRetry(message.conversationId, message.id)
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any())
            }
        }

    @Test
    fun givenAValidFailedEditedMessage_whenRetryingFailedMessage_thenShouldSendAsSignalingWithNewId() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.Failed, editStatus = Message.EditStatus.Edited(TEST_DATE))
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSender.sendMessage(
                    matching {
                        it is Message.Signaling // message edits are sent as signaling messages
                                && it.id != message.id // when editing we need to generate and set a new id
                                && it.content is MessageContent.TextEdited
                                && (it.content as MessageContent.TextEdited).editMessageId == message.id // original id in edited content
                    }, any()
                )
            }
        }

    @Test
    fun givenAValidFailedAndNotUploadedAssetMessage_whenSuccessfullyUploadedAsset_thenAssetTransferShouldBeChangedToUploaded() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
            val uploadedAssetSha = SHA256Key(byteArrayOf())
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
                .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedAssetSha))
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.updateAssetMessageTransferStatus.invoke(AssetTransferStatus.UPLOADED, message.conversationId, message.id)
            }
        }

    @Test
    fun givenAValidFailedAndNotUploadedAssetMessage_whenRetryingFailedMessage_thenUploadAssetAndSendAMessageWithProperAssetRemoteData() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
            val uploadedAssetSha = SHA256Key(byteArrayOf())
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
                .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedAssetSha))
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.assetRepository.uploadAndPersistPrivateAsset(
                    mimeType = any(),
                    assetDataPath = path,
                    otrKey = any(),
                    extension = name.fileExtension(),
                    conversationId = any(),
                    filename = any(),
                    filetype = any(),
                    retainSource = true
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSender.sendMessage(matching {
                    it.id == message.id && it.content is MessageContent.Asset
                            && (it.content as MessageContent.Asset).value.remoteData.assetId == uploadedAssetId.key
                            && (it.content as MessageContent.Asset).value.remoteData.assetDomain == uploadedAssetId.domain
                            && (it.content as MessageContent.Asset).value.remoteData.assetToken == uploadedAssetId.assetToken
                }, any())
            }
        }

    @Test
    fun givenAValidFailedAssetMessageWithAssetAlreadyUploaded_whenRetryingFailedMessage_thenDoNotUploadAssetAndSendAMessage() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
                .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Right(Unit))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verifySuspend(VerifyMode.not) {
                arrangement.assetRepository.uploadAndPersistPrivateAsset(
                    mimeType = any(),
                    assetDataPath = any(),
                    otrKey = any(),
                    extension = any(),
                    conversationId = any(),
                    filename = any(),
                    filetype = any(),
                    retainSource = true
                )
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any())
            }
        }

    @Test
    fun givenSendingMessageReturnsFailure_whenRetryingFailedMessage_thenShouldStillReturnSuccess() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.Failed)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            assertIs<MessageOperationResult.Success>(result)
        }

    @Test
    fun givenSendingMessageReturnsFailure_whenRetryingFailedRemotelyMessage_thenShouldStillReturnSuccess() =
        runTest(testDispatcher.default) {
            // given
            val message = TEXT_MESSAGE.copy(status = Message.Status.FailedRemotely)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withSendMessage(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            assertIs<MessageOperationResult.Success>(result)
        }

    @Test
    fun givenUploadingAssetReturnsFailure_whenRetryingFailedMessage_thenShouldStillReturnSuccess() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val (_, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withUploadAndPersistPrivateAsset(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            val result = useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            assertIs<MessageOperationResult.Success>(result)
        }

    @Test
    fun givenUploadingAssetIsSuccessfulButSendingAssetMessageReturnsFailure_whenRetryingFailedMessage_thenPersistTheAssetRemoteData() =
        runTest(testDispatcher.default) {
            // given
            val name = "some_asset.txt"
            val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
            val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
            val message = assetMessage().copy(content = content, status = Message.Status.Failed)
            val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
            val uploadedAssetSha = SHA256Key(byteArrayOf())
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .withStoredData(mockedLongAssetData(), path)
                .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
                .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedAssetSha))
                .withPersistMessage(Either.Right(Unit))
                .withSendMessage(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
                .arrange()

            // when
            useCase.invoke(message.id, message.conversationId)
            advanceUntilIdle()

            // then
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.persistMessage.invoke(
                    matching {
                        it.id == message.id && it.content is MessageContent.Asset
                                && (it.content as MessageContent.Asset).value.remoteData.assetId == uploadedAssetId.key
                                && (it.content as MessageContent.Asset).value.remoteData.assetDomain == uploadedAssetId.domain
                                && (it.content as MessageContent.Asset).value.remoteData.assetToken == uploadedAssetId.assetToken
                    }
                )
            }
        }

    @Test
    fun givenUploadedAsset_whenAwaitingRetry_thenWaitForSendAndPropagateItsFailureWithoutReupload() =
        runTest(testDispatcher.default) {
            val message = assetMessage().copy(status = Message.Status.Failed)
            val failure = NetworkFailure.NoNetworkConnection(null)
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
                .arrange()
            val entered = CompletableDeferred<Unit>()
            val sendGate = CompletableDeferred<Unit>()
            everySuspend { arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any()) } calls {
                entered.complete(Unit)
                sendGate.await()
                Either.Left(failure)
            }

            val retry = async { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
            runCurrent()
            assertTrue(entered.isCompleted)
            assertFalse(retry.isCompleted)
            sendGate.complete(Unit)

            assertEquals(failure, assertIs<MessageOperationResult.Failure>(retry.await()).error)
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, message.conversationId, message.id)
            }
        }

    @Test
    fun givenAssetUploadFails_whenAwaitingRetry_thenReturnFailureWithoutSending() = runTest(testDispatcher.default) {
        val message = assetMessage().copy(status = Message.Status.Failed)
        val failure = NetworkFailure.NoNetworkConnection(null)
        val path = fakeKaliumFileSystem.providePersistentAssetPath("awaited-retry")
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withUploadAndPersistPrivateAsset(Either.Left(failure))
            .arrange()

        val result = RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId)

        assertEquals(failure, assertIs<MessageOperationResult.Failure>(result).error)
        verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, message.conversationId, message.id)
        }
    }

    @Test
    fun givenRetryCancelledDuringUpload_whenIoCleanupWaits_thenOwnerWaitsBeforeMarkingOriginalFailed() =
        runTest(testDispatcher.default) {
            val message = assetMessage().copy(status = Message.Status.Failed)
            val path = fakeKaliumFileSystem.providePersistentAssetPath("cancelled-retry")
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
                .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
                .withFetchPrivateDecodedAsset(Either.Right(path))
                .arrange()
            val entered = CompletableDeferred<Unit>()
            val cleanupEntered = CompletableDeferred<Unit>()
            val cleanupGate = CompletableDeferred<Unit>()
            everySuspend {
                arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } calls {
                entered.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupEntered.complete(Unit)
                        cleanupGate.await()
                    }
                }
            }
            val retry = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
            runCurrent()
            assertTrue(entered.isCompleted)

            retry.cancel()
            runCurrent()
            assertTrue(cleanupEntered.isCompleted)
            assertFalse(retry.isCompleted)
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
            }
            cleanupGate.complete(Unit)
            retry.join()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, message.conversationId, message.id)
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, message.conversationId, message.id)
            }
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
        }

    @Test
    fun givenRetryCancelledDuringSend_whenAssetUploaded_thenKeepRemoteDataForNextRetry() = runTest(testDispatcher.default) {
        val message = assetMessage().copy(status = Message.Status.Failed)
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
            .arrange()
        val entered = CompletableDeferred<Unit>()
        everySuspend { arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any()) } calls {
            entered.complete(Unit)
            awaitCancellation()
        }
        val retry = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
        runCurrent()
        assertTrue(entered.isCompleted)

        retry.cancel()
        retry.join()

        verifySuspend(VerifyMode.exactly(0)) { arrangement.updateAssetMessageTransferStatus(any(), any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, message.conversationId, message.id)
        }
    }

    @Test
    fun givenSendSucceeded_whenCancellationWinsReturningToOwner_thenDoNotMarkSentMessageFailed() =
        runTest(testDispatcher.default) {
            val message = assetMessage().copy(status = Message.Status.Failed)
            val (arrangement, useCase) = Arrangement()
                .withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
                .arrange()
            lateinit var retry: Job
            var sendSucceeded = false
            everySuspend { arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any()) } calls {
                // The real sender has persisted SENT before reporting successful completion.
                sendSucceeded = true
                retry.cancel()
                Either.Right(Unit)
            }
            retry = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
            retry.join()

            assertTrue(sendSucceeded)
            assertTrue(retry.isCancelled)
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
                arrangement.updateAssetMessageTransferStatus(any(), any(), any())
            }
        }

    @Test
    fun givenSentPersisted_whenSenderThrowsCancellationBeforeReturning_thenKeepSentStatus() = runTest(testDispatcher.default) {
        val message = assetMessage().copy(status = Message.Status.Failed)
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
            .arrange()
        var persisted: Message = message
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { Either.Right(persisted) }
        everySuspend { arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any()) } calls {
            persisted = message.copy(status = Message.Status.Sent)
            throw CancellationException("Transaction result cancelled after persisting SENT")
        }
        val retry = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
        retry.join()

        assertTrue(retry.isCancelled)
        assertEquals(Message.Status.Sent, persisted.status)
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
            arrangement.updateAssetMessageTransferStatus(any(), any(), any())
        }
    }

    @Test
    fun deliveryAndReadReceiptsBeforeCancellationCleanupRemainSuccessful() = runTest(testDispatcher.default) {
        for (status in listOf(Message.Status.Delivered, Message.Status.Read(1))) {
            val message = assetMessage().copy(status = Message.Status.Failed)
            val (arrangement, useCase) = Arrangement()
                .withUpdateMessageStatus(Either.Right(Unit))
                .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
                .arrange()
            var persisted: Message = message
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { Either.Right(persisted) }
            everySuspend { arrangement.messageSender.sendMessage(any(), any()) } calls {
                // Receipt commits while the caller is still waiting for the send result.
                persisted = message.copy(status = status)
                throw CancellationException("Receipt committed before cancelled send returned")
            }
            val retry = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
            retry.join()
            assertTrue(retry.isCancelled)
            assertEquals(status, persisted.status)
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
                arrangement.updateAssetMessageTransferStatus(any(), any(), any())
            }
        }
    }

    @Test
    fun cancellationAfterRemoteDataCommitBeforeUploadedStatusReusesUploadOnNextRetry() = runTest(testDispatcher.default) {
        val message = assetMessage().copy(status = Message.Status.Failed)
        val path = fakeKaliumFileSystem.providePersistentAssetPath("remote-commit-race")
        val (arrangement, useCase) = Arrangement()
            .withUpdateMessageStatus(Either.Right(Unit))
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withUploadAndPersistPrivateAsset(Either.Right(
                UploadedAssetId("uploaded-once", "remote-domain", "token") to SHA256Key(byteArrayOf(1, 2, 3))
            ))
            .withSendMessage(Either.Right(Unit))
            .arrange()
        var persisted = message
        var transfer = AssetTransferStatus.FAILED_UPLOAD
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { Either.Right(persisted) }
        everySuspend { arrangement.getAssetMessageTransferStatus(any(), any()) } calls { transfer }
        everySuspend { arrangement.updateAssetMessageTransferStatus(any(), any(), any()) } calls {
            transfer = it.args[0] as AssetTransferStatus
            UpdateTransferStatusResult.Success
        }
        everySuspend { arrangement.persistMessage.invoke(any()) } calls {
            persisted = it.args[0] as Message.Regular
            // Emulate cancellation on return from the completed persistence transaction.
            throw CancellationException("Remote data committed; UPLOADED not yet written")
        }
        val retry = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
        retry.join()
        assertTrue(retry.isCancelled)
        assertEquals("uploaded-once", (persisted.content as MessageContent.Asset).value.remoteData.assetId)
        assertEquals(AssetTransferStatus.UPLOADED, transfer)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(
                MessageEntity.Status.FAILED, message.conversationId, message.id
            )
        }
        persisted = persisted.copy(status = Message.Status.Failed)
        assertIs<MessageOperationResult.Success>(RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId))
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
            arrangement.messageSender.sendMessage(persisted.copy(status = Message.Status.Pending), any())
        }
    }

    @Test
    fun givenRetryStatusCannotBePersisted_whenAwaiting_thenDoNotSend() = runTest(testDispatcher.default) {
        val message = TEXT_MESSAGE.copy(status = Message.Status.Failed)
        val failure = CoreFailure.Unknown(IllegalStateException("status write failed"))
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Left(failure))
            .arrange()

        val result = RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId)

        assertEquals(failure, assertIs<MessageOperationResult.Failure>(result).error)
        verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenMultipartPublishFails_whenAwaitingRetry_thenPropagateFailureWithoutSending() = runTest(testDispatcher.default) {
        val message = TEXT_MESSAGE.copy(content = MessageContent.Multipart("attachment"), status = Message.Status.Failed)
        val failure = NetworkFailure.NoNetworkConnection(null)
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .arrange()
        everySuspend { arrangement.conversationRepository.isCellEnabled(message.conversationId) } returns Either.Right(true)
        everySuspend { arrangement.attachmentsRepository.getAll(message.conversationId) } returns Either.Right(emptyList())
        everySuspend { arrangement.publishAttachments(any()) } returns Either.Left(failure)

        val result = RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId)

        assertEquals(failure, assertIs<MessageOperationResult.Failure>(result).error)
        verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenMultipartPublishSucceeds_whenAwaitingRetry_thenSendExactlyOnce() = runTest(testDispatcher.default) {
        val message = TEXT_MESSAGE.copy(content = MessageContent.Multipart("attachment"), status = Message.Status.Failed)
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()
        everySuspend { arrangement.conversationRepository.isCellEnabled(message.conversationId) } returns Either.Right(true)
        everySuspend { arrangement.attachmentsRepository.getAll(message.conversationId) } returns Either.Right(emptyList())
        everySuspend { arrangement.publishAttachments(any()) } returns Either.Right(Unit)

        assertIs<MessageOperationResult.Success>(RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.publishAttachments(any())
            arrangement.messageSender.sendMessage(message.copy(status = Message.Status.Pending), any())
        }
    }

    @Test
    fun scheduledAndAwaitedRetriesCannotBothClaimTheSameFailedMessage() = runTest(testDispatcher.default) {
        val message = TEXT_MESSAGE.copy(status = Message.Status.Failed)
        val (arrangement, useCase) = Arrangement()
            .withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit))
            .arrange()
        var claimed = false
        everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
            val won = !claimed
            claimed = true
            Either.Right(won)
        }
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        everySuspend { arrangement.messageSender.sendMessage(any(), any()) } calls {
            entered.complete(Unit)
            finish.await()
            Either.Right(Unit)
        }
        assertIs<MessageOperationResult.Success>(useCase(message.id, message.conversationId))
        entered.await()
        // The second caller still reads FAILED, but loses the database claim.
        assertIs<MessageOperationResult.Failure>(RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId))
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
        }
        finish.complete(Unit)
        advanceUntilIdle()
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun awaitedFailurePublishesOnceEvenWhenAnotherRetryClaimsBeforeFinalizerReturns() = runTest(testDispatcher.default) {
        val message = TEXT_MESSAGE.copy(status = Message.Status.Failed)
        val (arrangement, useCase) = Arrangement().withUpdateMessageStatus(Either.Right(Unit)).arrange()
        var stored = message
        var writes = 0
        var sends = 0
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { Either.Right(stored) }
        everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
            val claimed = stored.status == Message.Status.Failed
            if (claimed) stored = stored.copy(status = Message.Status.Pending)
            Either.Right(claimed)
        }
        val firstFinalized = CompletableDeferred<Unit>()
        val finishFinalizer = CompletableDeferred<Unit>()
        val secondSending = CompletableDeferred<Unit>()
        val finishSend = CompletableDeferred<Unit>()
        everySuspend { arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any()) } calls {
            writes++
            stored = stored.copy(status = Message.Status.Failed)
            firstFinalized.complete(Unit)
            finishFinalizer.await()
            Either.Right(Unit)
        }
        everySuspend { arrangement.messageSender.sendMessage(any(), any()) } calls {
            sends++
            if (sends == 1) Either.Left(NetworkFailure.NoNetworkConnection(null)) else {
                secondSending.complete(Unit)
                finishSend.await()
                Either.Right(Unit)
            }
        }
        val first = launch { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
        firstFinalized.await()
        val second = async { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
        secondSending.await()
        first.cancel()
        finishFinalizer.complete(Unit)
        first.join()
        assertEquals(1, writes)
        assertEquals(Message.Status.Pending, stored.status)
        finishSend.complete(Unit)
        assertIs<MessageOperationResult.Success>(second.await())
    }

    @Test
    fun scheduledPublicationFailureLeavesMessageRetryableWithoutSending() = runTest(testDispatcher.default) {
        val message = TEXT_MESSAGE.copy(content = MessageContent.Multipart("attachment"), status = Message.Status.Failed)
        val (arrangement, useCase) = Arrangement().withGetMessageById(Either.Right(message))
            .withUpdateMessageStatus(Either.Right(Unit)).arrange()
        everySuspend { arrangement.conversationRepository.isCellEnabled(message.conversationId) } returns Either.Right(true)
        everySuspend { arrangement.attachmentsRepository.getAll(message.conversationId) } returns Either.Right(emptyList())
        everySuspend { arrangement.publishAttachments(any()) } returns Either.Left(NetworkFailure.NoNetworkConnection(null))
        assertIs<MessageOperationResult.Success>(useCase(message.id, message.conversationId))
        advanceUntilIdle()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, message.conversationId, message.id)
        }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun awaitedRetryReloadsRemoteAssetAfterDelayedClaim() = runTest(testDispatcher.default) {
        val message = assetMessage().copy(status = Message.Status.Failed)
        val content = message.content as MessageContent.Asset
        val uploaded = message.copy(content = content.copy(value = content.value.copy(
            remoteData = content.value.remoteData.copy(assetId = "uploaded-by-other-retry", sha256 = byteArrayOf(1))
        )), status = Message.Status.Pending)
        val (arrangement, useCase) = Arrangement().withUpdateMessageStatus(Either.Right(Unit))
            .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED).withSendMessage(Either.Right(Unit)).arrange()
        var stored = message
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { Either.Right(stored) }
        everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
            stored = uploaded
            Either.Right(true)
        }
        assertIs<MessageOperationResult.Success>(RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId))
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageSender.sendMessage(uploaded, any()) }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun deletionWinningAwaitedRetrySendCancelsWithoutFailureCleanup() = runTest(testDispatcher.default) {
        for (asset in listOf(false, true)) {
            val message = (if (asset) assetMessage() else TEXT_MESSAGE).copy(status = Message.Status.Failed)
            val (arrangement, useCase) = Arrangement().withGetMessageById(Either.Right(message))
                .withUpdateMessageStatus(Either.Right(Unit))
                .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED).arrange()
            val entered = CompletableDeferred<Unit>()
            val deleted = CompletableDeferred<Unit>()
            everySuspend { arrangement.messageSender.sendMessageWithOutcome(any(), any()) } calls {
                entered.complete(Unit)
                deleted.await()
                Either.Right(false)
            }
            everySuspend { arrangement.messageSender.sendMessage(any(), any()) } calls {
                entered.complete(Unit)
                deleted.await()
                Either.Right(Unit)
            }
            val retry = async { RetryFailedMessageAndAwaitUseCase(useCase)(message.id, message.conversationId) }
            entered.await()
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } returns Either.Left(StorageFailure.DataNotFound)
            deleted.complete(Unit)
            retry.join()
            assertTrue(retry.isCancelled)
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
            }
            verifySuspend(VerifyMode.exactly(0)) { arrangement.updateAssetMessageTransferStatus(any(), any(), any()) }
            verifySuspend(VerifyMode.exactly(0)) { arrangement.messageSender.sendMessage(any(), any()) }
        }
    }

    private class Arrangement(
        val messageRepository: MessageRepository = mock(mode = MockMode.autoUnit)
    ) {
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)
        init {
            everySuspend { assetRepository.deleteAssetLocally(any()) } returns Either.Right(Unit)
        }

        val attachmentsRepository = mock<MessageAttachmentDraftRepository>(mode = MockMode.autoUnit)
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val updateAssetMessageTransferStatus = mock<UpdateAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)
        val getAssetMessageTransferStatus = mock<GetAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)
        val publishAttachments = mock<PublishAttachmentsUseCase>(mode = MockMode.autoUnit)

        private val testScope = TestScope(testDispatcher.default)

        // Reuse existing send gates/failures for acknowledged sends; skipped-send tests override the outcome explicitly.
        private suspend fun configureSendOutcome() {
            everySuspend { messageSender.sendMessageWithOutcome(any(), any()) } calls { call ->
                messageSender.sendMessage(call.args[0] as Message.Sendable, call.args[1] as com.wire.kalium.messaging.sending.MessageTarget)
                    .map { true }
            }
        }

        suspend fun withGetMessageById(result: Either<StorageFailure, Message>): Arrangement = apply {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns result
        }

        suspend fun withUpdateMessageStatus(result: Either<CoreFailure, Unit>): Arrangement = apply {
            everySuspend {
                messageRepository.updateMessageStatus(any(), any(), any())
            } returns result
            everySuspend { assetRepository.deleteAssetLocally(any()) } returns Either.Right(Unit)
            everySuspend { messageRepository.claimFailedMessageForRetry(any(), any()) } returns result.map { true }
            everySuspend { messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any()) } returns result
        }

        suspend fun withUpdateAssetMessageTransferStatus(result: UpdateTransferStatusResult): Arrangement = apply {
            everySuspend {
                updateAssetMessageTransferStatus.invoke(any(), any(), any())
            } returns result
        }

        suspend fun withFetchPrivateDecodedAsset(result: Either<CoreFailure, Path>): Arrangement = apply {
            everySuspend {
                assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns result.map { FetchedAssetData(it, true) }
        }

        suspend fun withUploadAndPersistPrivateAsset(result: Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>>): Arrangement = apply {
            everySuspend {
                assetRepository.uploadAndPersistPrivateAsset(
                    mimeType = any(),
                    assetDataPath = any(),
                    otrKey = any(),
                    extension = any(),
                    conversationId = any(),
                    filename = any(),
                    filetype = any(),
                    retainSource = true
                )
            } returns result
        }

        suspend fun withPersistMessage(result: Either<CoreFailure, Unit>): Arrangement = apply {
            everySuspend {
                persistMessage.invoke(any())
            } returns result
        }

        suspend fun withSendMessage(result: Either<CoreFailure, Unit>): Arrangement = apply {
            everySuspend {
                messageSender.sendMessage(any(), any())
            } returns result
        }

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement = apply {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
        }

        suspend fun withGetAssetMessageTransferStatus(result: AssetTransferStatus) = apply {
            everySuspend {
                getAssetMessageTransferStatus.invoke(any(), any())
            } returns result
        }

        suspend fun arrange() = apply { configureSendOutcome() } to RetryFailedMessageUseCase(
            messageRepository,
            assetRepository,
            conversationRepository,
            attachmentsRepository,
            persistMessage,
            publishAttachments,
            testScope,
            testDispatcher,
            messageSender,
            updateAssetMessageTransferStatus,
            getAssetMessageTransferStatus
        )
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
        val testDispatcher = TestKaliumDispatcher
    }
}
