/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.asset.upload

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderMock
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.sync.SendPendingAssetMessageUseCaseImpl
import com.wire.kalium.messaging.sending.MessageSendFailureHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.persistence.dao.message.MessageEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleNewAssetMessageUseCaseTest {

    @Test
    fun givenAValidSendAssetMessageRequest_whenSendingAssetMessage_thenShouldReturnASuccessResult() = runTest(testDispatcher.default) {
        // Given
        val (_, sendAssetUseCase) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(assetUploadParams)
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Success)
    }

    @Test
    fun givenAValidSendAssetMessageRequest_whenThereIsAnAssetUploadError_thenShouldStillReturnSuccessResult() =
        runTest(testDispatcher.default) {
            val (_, sendAssetUseCase) = Arrangement(this)
                .withAssetMessagePersistSuccess()
                .withUploadFailure()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            val result = sendAssetUseCase.invoke(assetUploadParams)
            advanceUntilIdle()

            // Then
            assertTrue(result is ScheduleNewAssetMessageResult.Success)
        }

//     @Test
//     fun givenAValidSendAssetMessageRequest_whenMessageIsDeleted_thenAssetIsSavedWithFailedStatus() =
//         runTest(testDispatcher.default) {
//             val (arrangement, sendAssetUseCase) = Arrangement(this)
//                 .withAssetMessagePersistSuccess()
//                 .withUploadFailure()
//                 .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
//                 .withMessageDeleted()
//                 .arrange()
//
//             val result = sendAssetUseCase.invoke(assetUploadParams)
//
//             advanceUntilIdle()
//
//             // Then
//             assertTrue(result is ScheduleNewAssetMessageResult.Success)
//
//             verifySuspend(VerifyMode.exactly(1)) {
//                 arrangement.updateTransferStatus(
//                     transferStatus = AssetTransferStatus.FAILED_UPLOAD,
//                     conversationId = conversationId,
//                     messageId = any(),
//                 )
//             }
//         }

    @Test
    fun givenFileSendingRestrictedByTeam_whenSending_thenReturnDisabledByTeam() = runTest {
        // Given
        val (_, sendAssetUseCase) = Arrangement(this)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.Disabled)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(assetUploadParams)
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Failure.DisabledByTeam)
    }

    @Test
    fun givenPersistAssetMessageFailsAndPendingMessagesDisabled_whenSending_thenDoesNotScheduleResend() = runTest(testDispatcher.default) {
        // Given
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withAssetMessagePersistFailure()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .withPendingMessagesDisabled()
            .arrange()

        // When
        sendAssetUseCase.invoke(assetUploadParams)

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), eq(false))
        }
    }

    @Test
    fun givenAseetMimeTypeRestricted_whenSending_thenReturnRestrictedFileType() = runTest {
        // Given
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledSome(listOf("png")))
            .withValidateAsseMimeTypeResult(false)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(assetUploadParams)
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Failure.RestrictedFileType)

        verify(VerifyMode.exactly(1)) {
            arrangement.validateAssetMimeTypeUseCase(
                fileName = "some-asset.txt",
                mimeType = "text/plain",
                allowedExtension = listOf("png")
            )
        }
    }

    @Test
    fun givenAssetMimeTypeRestrictedAndFileAllowed_whenSending_thenReturnSendTheFile() = runTest(testDispatcher.default) {
        // Given
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledSome(listOf("png")))
            .withValidateAsseMimeTypeResult(true)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            assetUploadParams.copy(
                assetName = "some-asset.png",
                assetMimeType = "image/png"
            )
        )

        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Success)

        verify(VerifyMode.exactly(1)) {
            arrangement.validateAssetMimeTypeUseCase(
                fileName = "some-asset.png",
                mimeType = "image/png",
                allowedExtension = listOf("png")
            )
        }
    }

    @Test
    fun givenUploadFails_whenTracking_thenCompletionReportsFailure() = runTest(testDispatcher.default) {
        val (_, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadFailure()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()

        val result = assertIs<StartTrackedAssetUploadResult.Started>(StartTrackedAssetUploadUseCase(scheduler)(assetUploadParams))
        assertIs<AssetUploadCompletion.Failure>(result.operation.awaitCompletion())
        assertEquals(AssetUploadProgress.FAILED, result.operation.progress.value)
    }

    @Test
    fun givenDeletedMessage_whenTracking_thenCancelAndMarkFailed() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()
        arrangement.withMessageDeleted()

        val result = assertIs<StartTrackedAssetUploadResult.Started>(StartTrackedAssetUploadUseCase(scheduler)(assetUploadParams))
        assertEquals(AssetUploadCompletion.Cancelled, result.operation.awaitCompletion())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateTransferStatus(AssetTransferStatus.FAILED_UPLOAD, any(), any())
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
        }
    }

    @Test
    fun givenFailedSend_whenRetryingTrackedUpload_thenReusePersistedMessageAndRemoteAsset() = runTest(testDispatcher.default) {
        val remoteMetadata = uploadMetadata.copy(
            assetId = UploadedAssetId("remote-asset", "remote-domain"),
            sha256Key = SHA256Key(byteArrayOf(1, 2, 3))
        )
        val persistedMessage = TestMessage.assetMessage().copy(content = MessageContent.Asset(remoteMetadata.toAssetContent()))
        val (arrangement, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadFailure()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .withPersistedMessage(persistedMessage)
            .arrange()
        val first = assertIs<StartTrackedAssetUploadResult.Started>(StartTrackedAssetUploadUseCase(scheduler)(assetUploadParams)).operation
        assertIs<AssetUploadCompletion.Failure>(first.awaitCompletion())
        arrangement.withUploadSuccess()

        val retry = first.retry()
        assertEquals(first.messageId, retry.messageId)
        assertEquals(AssetUploadCompletion.Success, retry.awaitCompletion())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.persistNewAssetMessage(any(), any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.uploadAsset(persistedMessage.copy(status = Message.Status.Pending), remoteMetadata) }
    }

    @Test
    fun givenCallerCancelledBeforeHandleReturns_whenStarting_thenAwaitOwnedUploadCleanup() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(backgroundScope)
            .withAssetMessagePersistSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()
        val cleanup = CompletableDeferred<Unit>()
        var uploadCleaned = false
        lateinit var caller: Job
        everySuspend { arrangement.uploadAsset(any(), any()) } calls {
            caller.cancel()
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleanup.await() }
                uploadCleaned = true
            }
        }
        // A different dispatcher forces withContext's prompt cancellation when returning the handle.
        caller = launch(StandardTestDispatcher(testScheduler)) { scheduler.startTracked(assetUploadParams, "message") }
        runCurrent()
        assertFalse(caller.isCompleted)
        cleanup.complete(Unit)
        caller.join()
        assertTrue(uploadCleaned)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
        }
    }

    @Test
    fun givenPersistenceCommitsBeforeCancelledReturn_whenNoHandleCreated_thenMarkMessageFailedWithoutResend() =
        runTest(testDispatcher.default) {
            val (arrangement, scheduler) = Arrangement(backgroundScope)
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()
            everySuspend { arrangement.persistNewAssetMessage(any(), any(), any()) } calls {
                throw CancellationException("Persistence committed before cancellation was observed")
            }
            val caller = launch { scheduler.startTracked(assetUploadParams, "persisted-message") }
            caller.join()

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.updateTransferStatus(AssetTransferStatus.FAILED_UPLOAD, conversationId, "persisted-message")
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(
                    MessageEntity.Status.FAILED, conversationId, "persisted-message"
                )
            }
            verifySuspend(VerifyMode.not) { arrangement.uploadAsset(any(), any()) }
        }

    @Test
    fun givenPolicyChangesAfterFailure_whenRetrying_thenBlockUploadWithoutCreatingAnotherMessage() = runTest(testDispatcher.default) {
        for (policy in listOf(FileSharingStatus.Value.Disabled, FileSharingStatus.Value.EnabledSome(listOf("png")))) {
            val (arrangement, scheduler) = Arrangement(this)
                .withAssetMessagePersistSuccess()
                .withUploadFailure()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .withValidateAsseMimeTypeResult(false)
                .arrange()
            val first = assertIs<StartTrackedAssetUploadResult.Started>(scheduler.startTracked(assetUploadParams, "message")).operation
            first.awaitCompletion()
            arrangement.withObserveFileSharingStatusResult(policy)

            val retry = first.retry()
            val result = assertIs<AssetUploadCompletion.Failure>(retry.awaitCompletion())
            assertIs<CoreFailure.Unknown>(result.coreFailure)
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.persistNewAssetMessage(any(), any(), any())
                arrangement.uploadAsset(any(), any())
            }
        }
    }

    @Test
    fun givenMessagePersistedSent_whenUploadReturnIsCancelled_thenDoNotDowngradeOrRetry() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .withPersistedMessage(TestMessage.assetMessage().copy(status = Message.Status.Sent))
            .arrange()
        everySuspend { arrangement.uploadAsset(any(), any()) } calls {
            throw CancellationException("Send result lost while returning from IO")
        }
        val operation = assertIs<StartTrackedAssetUploadResult.Started>(
            scheduler.startTracked(assetUploadParams, "already-sent-message")
        ).operation

        assertEquals(AssetUploadCompletion.Success, operation.awaitCompletion())
        assertSame(operation, operation.retry())
        verifySuspend(VerifyMode.not) {
            arrangement.updateTransferStatus(AssetTransferStatus.FAILED_UPLOAD, any(), any())
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
        }
    }

    @Test
    fun receiptArrivingDuringUploadBeforeCancellationCleanupIsNotDowngraded() = runTest(testDispatcher.default) {
        for (status in listOf(Message.Status.Delivered, Message.Status.Read(1))) {
            val (arrangement, scheduler) = Arrangement(this)
                .withAssetMessagePersistSuccess()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()
            everySuspend { arrangement.uploadAsset(any(), any()) } calls {
                arrangement.withPersistedMessage(TestMessage.assetMessage().copy(status = status))
                throw CancellationException("Receipt arrived before cancelled send result")
            }
            val operation = assertIs<StartTrackedAssetUploadResult.Started>(
                scheduler.startTracked(assetUploadParams, "receipt-race")
            ).operation
            assertEquals(AssetUploadCompletion.Success, operation.awaitCompletion())
            assertSame(operation, operation.retry())
            verifySuspend(VerifyMode.not) {
                arrangement.updateTransferStatus(any(), any(), any())
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
            }
        }
    }

    @Test
    fun receiptArrivingBeforeTrackedRetryPreventsAnotherSend() = runTest(testDispatcher.default) {
        for (status in listOf(Message.Status.Delivered, Message.Status.Read(1))) {
            val (arrangement, scheduler) = Arrangement(this)
                .withAssetMessagePersistSuccess()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()
            everySuspend { arrangement.uploadAsset(any(), any()) } calls { throw CancellationException("Send result lost") }
            val operation = assertIs<StartTrackedAssetUploadResult.Started>(
                scheduler.startTracked(assetUploadParams, "late-receipt")
            ).operation
            assertEquals(AssetUploadCompletion.Cancelled, operation.awaitCompletion())
            arrangement.withPersistedMessage(TestMessage.assetMessage().copy(status = status))
            assertEquals(AssetUploadCompletion.Success, operation.retry().awaitCompletion())
            verifySuspend(VerifyMode.exactly(1)) { arrangement.uploadAsset(any(), any()) }
        }
    }

    @Test
    fun givenAssetPersistedRemotely_whenFinalSendCancelled_thenKeepUploadedStateAndReuseOnRetry() = runTest(testDispatcher.default) {
        val remoteMetadata = uploadMetadata.copy(
            assetId = UploadedAssetId("already-uploaded", "asset-domain"),
            sha256Key = SHA256Key(byteArrayOf(1, 2, 3))
        )
        val persistedMessage = TestMessage.assetMessage().copy(content = MessageContent.Asset(remoteMetadata.toAssetContent()))
        val (arrangement, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .withPersistedMessage(persistedMessage)
            .arrange()
        everySuspend { arrangement.uploadAsset(any(), any()) } calls {
            throw CancellationException("Upload succeeded but final message send was cancelled")
        }
        val operation = assertIs<StartTrackedAssetUploadResult.Started>(
            scheduler.startTracked(assetUploadParams, "uploaded-message")
        ).operation
        assertEquals(AssetUploadCompletion.Cancelled, operation.awaitCompletion())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateTransferStatus(AssetTransferStatus.UPLOADED, any(), any())
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(MessageEntity.Status.FAILED, any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.updateTransferStatus(AssetTransferStatus.FAILED_UPLOAD, any(), any())
        }

        arrangement.withUploadSuccess()
        val retry = operation.retry()
        assertEquals(AssetUploadCompletion.Success, retry.awaitCompletion())
        assertEquals(operation.messageId, retry.messageId)
        verifySuspend(VerifyMode.exactly(1)) { arrangement.uploadAsset(persistedMessage.copy(status = Message.Status.Pending), remoteMetadata) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.persistNewAssetMessage(any(), any(), any()) }
    }

    @Test
    fun concurrentTrackedRetriesClaimPendingOnceAndPassPendingMessageToUpload() = runTest(testDispatcher.default) {
        for (failed in listOf(Message.Status.Failed, Message.Status.FailedRemotely)) {
            val (arrangement, scheduler) = Arrangement(this)
                .withAssetMessagePersistSuccess()
                .withUploadFailure()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()
            val attempts = List(2) {
                assertIs<StartTrackedAssetUploadResult.Started>(scheduler.startTracked(assetUploadParams, "same-message")).operation
            }
            attempts.forEach { assertIs<AssetUploadCompletion.Failure>(it.awaitCompletion()) }
            val stale = TestMessage.assetMessage().copy(status = failed)
            var stored = stale
            // Both reads deliberately return the pre-claim snapshot.
            everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } returns stale.right()
            everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
                val claimed = stored.status == failed
                if (claimed) stored = stored.copy(status = Message.Status.Pending)
                claimed.right()
            }
            val entered = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            everySuspend { arrangement.uploadAsset(any(), any()) } calls {
                assertEquals(Message.Status.Pending, (it.args[0] as Message.Regular).status)
                assertEquals(Message.Status.Pending, stored.status)
                entered.complete(Unit)
                finish.await()
                Unit.right()
            }
            val first = attempts[0].retry()
            entered.await()
            val second = attempts[1].retry()
            assertIs<AssetUploadCompletion.Failure>(second.awaitCompletion())
            assertEquals(Message.Status.Pending, stored.status)
            assertSame(first, attempts[0].retry())
            finish.complete(Unit)
            assertEquals(AssetUploadCompletion.Success, first.awaitCompletion())
            verifySuspend(VerifyMode.exactly(3)) { arrangement.uploadAsset(any(), any()) }
        }
    }

    @Test
    fun receiptBetweenRetryReadAndClaimPreventsAnotherUpload() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadFailure()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()
        val first = assertIs<StartTrackedAssetUploadResult.Started>(scheduler.startTracked(assetUploadParams, "receipt-race")).operation
        assertIs<AssetUploadCompletion.Failure>(first.awaitCompletion())
        arrangement.withPersistedMessage(TestMessage.assetMessage().copy(status = Message.Status.Failed))
        everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
            arrangement.withPersistedMessage(TestMessage.assetMessage().copy(status = Message.Status.Delivered))
            false.right()
        }
        assertIs<AssetUploadCompletion.Failure>(first.retry().awaitCompletion())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.uploadAsset(any(), any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
        }
    }

    @Test
    fun cancellingRetryBeforeClaimDoesNotCleanUpAnotherSend() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(this)
            .withAssetMessagePersistSuccess()
            .withUploadFailure()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()
        val first = assertIs<StartTrackedAssetUploadResult.Started>(scheduler.startTracked(assetUploadParams, "not-owned")).operation
        assertIs<AssetUploadCompletion.Failure>(first.awaitCompletion())
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls {
            throw CancellationException("Cancelled before acquiring ownership")
        }
        assertEquals(AssetUploadCompletion.Cancelled, first.retry().awaitCompletion())
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.messageRepository.claimFailedMessageForRetry(any(), any())
        }
        // Only the original failed attempt publishes a terminal failure.
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
        }
    }

    @Test
    fun trackedRetryReloadsRemoteAssetCommittedBetweenReadAndClaim() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(this).withAssetMessagePersistSuccess().withUploadFailure()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll).arrange()
        val operation = assertIs<StartTrackedAssetUploadResult.Started>(scheduler.startTracked(assetUploadParams, "delayed")).operation
        assertIs<AssetUploadCompletion.Failure>(operation.awaitCompletion())
        val latestMetadata = uploadMetadata.copy(assetId = UploadedAssetId("uploaded-by-B", "domain"), sha256Key = SHA256Key(byteArrayOf(1)))
        val stale = TestMessage.assetMessage().copy(status = Message.Status.Failed)
        var stored = stale
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { stored.right() }
        everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
            // B uploads and fails sending before delayed A claims the failed row.
            stored = stale.copy(content = MessageContent.Asset(latestMetadata.toAssetContent()), status = Message.Status.Pending)
            true.right()
        }
        arrangement.withUploadSuccess()
        assertEquals(AssetUploadCompletion.Success, operation.retry().awaitCompletion())
        verifySuspend(VerifyMode.exactly(1)) { arrangement.uploadAsset(stored, latestMetadata) }
    }

    @Test
    fun storageFailureBecomesRetryableBeforeTrackedHandleCompletes() = runTest(testDispatcher.default) {
        val (arrangement, scheduler) = Arrangement(this).withAssetMessagePersistSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll).arrange()
        var stored = TestMessage.assetMessage().copy(status = Message.Status.Pending)
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } calls { stored.right() }
        everySuspend { arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any()) } calls {
            stored = stored.copy(status = Message.Status.Failed)
            Unit.right()
        }
        everySuspend { arrangement.messageRepository.claimFailedMessageForRetry(any(), any()) } calls {
            val claimed = stored.status == Message.Status.Failed
            if (claimed) stored = stored.copy(status = Message.Status.Pending)
            claimed.right()
        }
        everySuspend { arrangement.uploadAsset(any(), any()) } returns CoreFailure.Unknown(IllegalStateException("metadata storage failed")).left()
        val operation = assertIs<StartTrackedAssetUploadResult.Started>(scheduler.startTracked(assetUploadParams, "storage")).operation
        assertIs<AssetUploadCompletion.Failure>(operation.awaitCompletion())
        assertEquals(Message.Status.Failed, stored.status)
        arrangement.withUploadSuccess()
        assertEquals(AssetUploadCompletion.Success, operation.retry().awaitCompletion())
    }

    @Test
    fun offlineLegacyUploadIsRetriedAfterReconnect() = runTest(testDispatcher.default) {
        val arrangement = Arrangement(this).withAssetMessagePersistSuccess()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
        val assets = mock<AssetRepository>()
        val sender = mock<MessageSender>()
        val persist = mock<PersistMessageUseCase>()
        val getStatus = mock<GetAssetMessageTransferStatusUseCase>()
        var transfer = AssetTransferStatus.NOT_DOWNLOADED
        var messageStatus = MessageEntity.Status.PENDING
        var resendRequests = 0
        val handler = MessageSendFailureHandlerImpl(
            recipients = mock(),
            updateMessageStatus = { status, _, _ -> messageStatus = status; Unit.right() },
            scheduleResend = { resendRequests++ },
        )
        val audio = AudioNormalizedLoudnessBuilderMock()
        val realUpload = UploadAssetUseCaseImpl(
            assets, sender, handler, arrangement.updateTransferStatus, mock(), persist, audio,
            pendingMessagesEnabled = true, dispatcher = testDispatcher,
        )
        val (_, scheduler) = arrangement.arrange(realUpload, handler)
        val message = TestMessage.assetMessage()
        val content = message.content as MessageContent.Asset
        everySuspend { arrangement.messageRepository.getMessageById(any(), any()) } returns message.copy(
            content = MessageContent.Asset(content.value.copy(remoteData = content.value.remoteData.copy(sha256 = byteArrayOf())))
        ).right()
        everySuspend { arrangement.updateTransferStatus(any(), any(), any()) } calls { (status: AssetTransferStatus) ->
            transfer = status
            UpdateTransferStatusResult.Success
        }
        everySuspend { assets.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            NetworkFailure.NoNetworkConnection(null).left()
        everySuspend { getStatus(any(), any()) } calls { transfer }
        assertIs<ScheduleNewAssetMessageResult.Success>(scheduler(assetUploadParams))
        advanceUntilIdle()
        assertEquals(1, resendRequests)
        assertEquals(MessageEntity.Status.PENDING, messageStatus)

        assertEquals(AssetTransferStatus.FAILED_UPLOAD, transfer)
        // Reconnect runs the real pending-asset dispatcher through upload, persistence and send.
        var retryEntered = false
        everySuspend { assets.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any()) } calls {
            retryEntered = true
            FetchedAssetData("/local/asset".toPath(), false).right()
        }
        everySuspend { assets.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            (UploadedAssetId("remote", "wire.com") to SHA256Key(byteArrayOf(1))).right()
        everySuspend { assets.deleteAssetLocally(any()) } returns Unit.right()
        everySuspend { persist(any()) } returns Unit.right()
        everySuspend { sender.sendMessage(any(), any()) } returns Unit.right()
        val pending = SendPendingAssetMessageUseCaseImpl(
            assets, persist, arrangement.updateTransferStatus, getStatus, sender, handler, audio,
            dispatchers = testDispatcher,
        )
        assertEquals(Unit.right(), pending(TestMessage.assetMessage().copy(status = Message.Status.Pending)))
        assertEquals(AssetTransferStatus.UPLOADED, transfer)
        verifySuspend(VerifyMode.exactly(1)) { sender.sendMessage(any(), any()) }
        assertTrue(retryEntered, "Reconnect must enter reupload; transfer=$transfer, message=$messageStatus")
    }

    @Test
    fun offlineSendRestoresUploadedBeforeSchedulingResend() = runTest(testDispatcher.default) {
        val message = TestMessage.assetMessage()
        val content = message.content as MessageContent.Asset
        val stored = message.copy(content = MessageContent.Asset(
            content.value.copy(remoteData = content.value.remoteData.copy(sha256 = byteArrayOf(1)))
        ))
        val (arrangement, scheduler) = Arrangement(this).withAssetMessagePersistSuccess()
            .withPersistedMessage(stored)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll).arrange()
        var transfer = AssetTransferStatus.UPLOAD_IN_PROGRESS
        var scheduled = false
        everySuspend { arrangement.uploadAsset(any(), any()) } returns NetworkFailure.NoNetworkConnection(null).left()
        everySuspend { arrangement.updateTransferStatus(any(), any(), any()) } calls { (status: AssetTransferStatus) ->
            transfer = status
            UpdateTransferStatusResult.Success
        }
        everySuspend {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), eq(true))
        } calls {
            assertEquals(AssetTransferStatus.UPLOADED, transfer)
            scheduled = true
        }
        scheduler(assetUploadParams)
        advanceUntilIdle()
        assertTrue(scheduled)
        verifySuspend(VerifyMode.not) { arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any()) }
    }

    @Test
    fun offlineFinalizerPreservesAcknowledgedMessagesWithoutRescheduling() = runTest(testDispatcher.default) {
        for (status in listOf(Message.Status.Sent, Message.Status.Delivered, Message.Status.Read(1))) {
            val (arrangement, scheduler) = Arrangement(this).withAssetMessagePersistSuccess()
                .withPersistedMessage(TestMessage.assetMessage().copy(status = status))
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll).arrange()
            everySuspend { arrangement.uploadAsset(any(), any()) } returns NetworkFailure.NoNetworkConnection(null).left()
            scheduler(assetUploadParams)
            advanceUntilIdle()
            verifySuspend(VerifyMode.not) {
                arrangement.updateTransferStatus(any(), any(), any())
                arrangement.messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any())
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
            }
        }
    }

    private class Arrangement(val coroutineScope: CoroutineScope) {
        val persistNewAssetMessage = mock<PersistNewAssetMessageUseCase>(mode = MockMode.autoUnit)
        val uploadAsset = mock<UploadAssetUseCase>(mode = MockMode.autoUnit)
        private val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val updateTransferStatus = mock<UpdateAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)
        val messageRepository: MessageRepository = mock(mode = MockMode.autoUnit)
        val validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase = mock(mode = MockMode.autoUnit)
        val observerFileSharingStatusUseCase: ObserveFileSharingStatusUseCase = mock(mode = MockMode.autoUnit)
        val messageSendFailureHandler: MessageSendFailureHandler = mock(mode = MockMode.autoUnit)

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

        suspend fun withAssetMessagePersistSuccess() = apply {
            everySuspend {
                persistNewAssetMessage.invoke(any(), any(), any())
            } returns Pair(uploadMetadata, TestMessage.assetMessage()).right()
        }

        private var persistedMessage = TestMessage.assetMessage()

        suspend fun withPersistedMessage(message: Message.Regular) = apply {
            persistedMessage = message
        }

        suspend fun withAssetMessagePersistFailure() = apply {
            everySuspend {
                persistNewAssetMessage.invoke(any(), any(), any())
            } returns CoreFailure.Unknown(null).left()
        }

        private var pendingMessages = true

        fun withPendingMessagesDisabled() = apply {
            pendingMessages = false
        }

        suspend fun withUploadSuccess() = apply {
            everySuspend {
                uploadAsset.invoke(any(), any())
            } returns Unit.right()
        }

        suspend fun withUploadFailure() = apply {
            everySuspend {
                uploadAsset.invoke(any(), any())
            } returns CoreFailure.Unknown(TestNetworkException.missingAuth).left()
        }

        fun withValidateAsseMimeTypeResult(result: Boolean) = apply {
            every {
                validateAssetMimeTypeUseCase.invoke(any(), any(), any())
            } returns result
        }

        fun withObserveFileSharingStatusResult(result: FileSharingStatus.Value) = apply {
            every {
                observerFileSharingStatusUseCase.invoke()
            } returns flowOf(FileSharingStatus(result, false))
        }

        suspend fun withMessageDeleted() = apply {
            everySuspend {
                messageRepository.observeMessageVisibility(any(), any())
            } returns flowOf(MessageEntity.Visibility.DELETED.right())
        }

        fun arrange(
            actualUpload: UploadAssetUseCase = uploadAsset,
            actualFailureHandler: MessageSendFailureHandler = messageSendFailureHandler,
        ) = this to ScheduleNewAssetMessageUseCaseImpl(
            persistNewAssetMessage = persistNewAssetMessage,
            uploadAsset = actualUpload,
            updateAssetMessageTransferStatus = updateTransferStatus,
            userId = QualifiedID("some-id", "some-domain"),
            slowSyncRepository = slowSyncRepository,
            messageRepository = messageRepository,
            observeFileSharingStatus = observerFileSharingStatusUseCase,
            validateAssetFileUseCase = validateAssetMimeTypeUseCase,
            messageSendFailureHandler = actualFailureHandler,
            pendingMessagesEnabled = pendingMessages,
            scope = coroutineScope,
            dispatcher = testDispatcher,
        ).also {
            every { slowSyncRepository.slowSyncStatus } returns completeStateFlow
            everySuspend { messageRepository.claimFailedMessageForRetry(any(), any()) } returns true.right()
            everySuspend { messageRepository.updateMessageStatusIfNotAcknowledged(any(), any(), any()) } returns Unit.right()
            everySuspend { messageRepository.getMessageById(any(), any()) } calls { persistedMessage.right() }
            everySuspend { messageRepository.observeMessageVisibility(any(), any()) } returns
                flowOf(MessageEntity.Visibility.VISIBLE.right())
            everySuspend { updateTransferStatus(any(), any(), any()) } returns UpdateTransferStatusResult.Success
        }
    }

    companion object {

        val testDispatcher = TestKaliumDispatcher

        internal val uploadMetadata = UploadAssetMessageMetadata(
            conversationId = QualifiedID("some-id", "some-domain"),
            mimeType = "",
            assetId = UploadedAssetId("some-asset-id", "some-domain"),
            assetDataPath = "/some/path/to/asset".toPath(),
            assetDataSize = 1L,
            assetName = "",
            assetWidth = null,
            assetHeight = null,
            otrKey = AES256Key(byteArrayOf()),
            sha256Key = SHA256Key(byteArrayOf()),
            audioLengthInMs = 0L,
            audioNormalizedLoudness = null
        )

        private val conversationId = QualifiedID("some-convo-id", "some-domain-id")

        internal val assetUploadParams = AssetUploadParams(
            conversationId = conversationId,
            assetDataPath = "/some/path/to/asset".toPath(),
            assetDataSize = 100L,
            assetName = "some-asset.txt",
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0,
            audioNormalizedLoudness = null
        )

    }
}
