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
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderMock
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSendFailureHandler
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

class UploadAssetUseCaseTest {

    @Test
    fun givenDeletionBeforeSenderCheck_whenMonitorHasNotReacted_thenTrackedUploadIsCancelled() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange { withUploadSuccess() }
        everySuspend { arrangement.messageSender.sendMessageWithOutcome(any(), any()) } returns false.right()
        var cleanupCalls = 0
        val operation = TrackedAssetUploadImpl(
            messageId = "deleted-message",
            scope = backgroundScope,
            retryOperation = { error("Deletion must not automatically retry") },
            monitor = { awaitCancellation() },
            upload = { uploadAsset(assetMessage(), uploadMetadata) },
            onCancelled = { cleanupCalls++ },
            onFailed = { cleanupCalls++ },
        )
        assertEquals(AssetUploadCompletion.Cancelled, operation.awaitCompletion())
        assertEquals(AssetUploadProgress.CANCELLED, operation.progress.value)
        assertEquals(0, cleanupCalls)
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun trackedFailuresLeaveTerminalStatusToOperationOwner() = runTest(testDispatcher.default) {
        for (failureStage in 0..2) {
            val (arrangement, uploadAsset) = Arrangement().arrange {
                if (failureStage == 0) withUploadFailure() else withUploadSuccess()
                if (failureStage == 1) withPersistMessageFailure()
                if (failureStage == 2) sendMessageResult = CoreFailure.Unknown(null).left()
            }

            val result = withContext(AssetMessageSendReceipt()) { uploadAsset(assetMessage(), uploadMetadata) }

            assertEquals(CoreFailure.Unknown(null).left(), result)
            verifySuspend(VerifyMode.not) {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
                arrangement.updateAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD, any(), any())
            }
        }
    }

    @Test
    fun givenAValidMessage_whenUploadStarts_thenTransferStatusUpdatedCorrectly() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = AssetTransferStatus.UPLOAD_IN_PROGRESS,
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenTransferStatusUpdatedCorrectly() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = AssetTransferStatus.UPLOADED,
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenLocalFileIsRemoved() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetDataSource.deleteAssetLocally(any())
        }
    }

    @Test
    fun givenPersistingUpdatedMessageFails_whenUploadSucceeds_thenLocalFileIsNotRemoved() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            withPersistMessageFailure()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.not) {
            arrangement.assetDataSource.deleteAssetLocally(any())
        }
    }

    @Test
    fun givenPersistingUpdatedMessageFails_whenUploadSucceeds_thenUploadedStatusIsNotSet() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            withPersistMessageFailure()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.not) {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = AssetTransferStatus.UPLOADED,
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenAValidMessage_whenUploadFails_thenTransferStatusUpdatedCorrectly() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadFailure()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateAssetMessageTransferStatus(
                transferStatus = AssetTransferStatus.FAILED_UPLOAD,
                conversationId = any(),
                messageId = any()
            )
        }
    }

    @Test
    fun givenUploadFailsAndPendingMessagesDisabled_whenUploadingAsset_thenDoesNotScheduleResend() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadFailure()
            withPendingMessagesDisabled()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), eq(false))
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenAssetMessageIsPersisted() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(any())
        }
    }

    @Test
    fun givenAValidMessage_whenUploadSucceeds_thenAssetMessageIsSent() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
        }

        uploadAsset(assetMessage(), uploadMetadata)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    @Test
    fun givenMessageSendingFails_whenUploadSucceeds_thenReturnSendingFailure() = runTest(testDispatcher.default) {
        val failure = CoreFailure.Unknown(IllegalStateException("send failed"))
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            sendMessageResult = failure.left()
        }

        assertEquals(failure.left(), uploadAsset(assetMessage(), uploadMetadata))
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(failure, any(), any(), any(), any())
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun givenMessageSendPending_whenUploadSucceeds_thenAwaitActualSend() = runTest(testDispatcher.default) {
        val sendGate = CompletableDeferred<Unit>()
        val (_, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            messageSendGate = sendGate
        }

        val result = async { uploadAsset(assetMessage(), uploadMetadata) }
        runCurrent()
        assertFalse(result.isCompleted)
        sendGate.complete(Unit)
        assertEquals(Unit.right(), result.await())
    }

    @Test
    fun givenSendAcknowledged_whenCancellationWinsIoReturn_thenTrackedAttemptStaysSuccessful() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange { withUploadSuccess() }
        lateinit var operation: TrackedAssetUpload
        everySuspend { arrangement.messageSender.sendMessageWithOutcome(any(), any()) } calls {
            operation.cancel()
            true.right()
        }
        var cancelledStatusUpdates = 0
        // Force the upload use case to dispatch back to another dispatcher after the sender acknowledges the message.
        val owner = CoroutineScope(backgroundScope.coroutineContext + StandardTestDispatcher(testScheduler))
        operation = TrackedAssetUploadImpl(
            messageId = "acknowledged-message",
            scope = owner,
            retryOperation = { error("Acknowledged sends must not be retried") },
            monitor = { awaitCancellation() },
            upload = { uploadAsset(assetMessage(), uploadMetadata) },
            onCancelled = { cancelledStatusUpdates++ },
        )

        assertEquals(AssetUploadCompletion.Success, operation.awaitCompletion())
        assertEquals(AssetUploadProgress.COMPLETED, operation.progress.value)
        assertEquals(0, cancelledStatusUpdates)
        assertSame(operation, operation.retry())
        verifySuspend(VerifyMode.not) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenUploadedAssetOnRetry_whenSending_thenReuseAssetWithoutUploadingOrPersistingAgain() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {}
        val result = uploadAsset(assetMessage(), uploadMetadata.copy(sha256Key = SHA256Key(byteArrayOf(1))))

        assertEquals(Unit.right(), result)
        verifySuspend(VerifyMode.not) {
            arrangement.assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
            arrangement.persistMessage(any())
            arrangement.assetDataSource.deleteAssetLocally(any())
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.messageSender.sendMessage(any(), any()) }
    }

    @Test
    fun givenPersistingUpdatedMessageFails_whenUploadSucceeds_thenReturnPersistenceFailure() = runTest(testDispatcher.default) {
        val (_, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            withPersistMessageFailure()
        }
        assertEquals(CoreFailure.Unknown(null).left(), uploadAsset(assetMessage(), uploadMetadata))

    }

    @Test
    fun previewFailureStillPersistsAndSendsVideoWithoutPreview() = runTest(testDispatcher.default) {
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            everySuspend { assetDataSource.fetchPreviewAsset(any()) } returns
                Either.Left(StorageFailure.DataNotFound)
        }
        val preview = AssetContent.Preview(
            "image/jpeg", 1024, 512, 288,
            AssetContent.RemoteData(ByteArray(32) { 1 }, byteArrayOf(), "temp-preview", null, "", MessageEncryptionAlgorithm.AES_CBC),
        )
        uploadAsset(assetMessage(), uploadMetadata.copy(mimeType = "video/mp4", preview = preview))
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(matches { (it.content as? MessageContent.Asset)?.value?.let { asset ->
                asset.mimeType == "video/mp4" && asset.preview == null && asset.remoteData.assetId == "remote-asset-id"
            } == true })
            arrangement.messageSender.sendMessage(
                matches { (it.content as? MessageContent.Asset)?.value?.preview == null }, any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.assetDataSource.deleteAssetLocally("temp-preview") }
    }

    @Test
    fun successfulPreviewIsPersistedWithVideoBeforeTemporaryPreviewIsRemoved() = runTest(testDispatcher.default) {
        val uploadedPreview = UploadedAssetId("uploaded-preview", "preview.domain", "preview-token")
        val previewHash = SHA256Key(ByteArray(32) { 4 })
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            everySuspend { assetDataSource.fetchPreviewAsset(any()) } returns
                Either.Right(FetchedAssetData("preview.jpg".toPath(), false))
            everySuspend {
                assetDataSource.uploadAndPersistPrivateAsset(eq("image/jpeg"), any(), any(), any(), any(), any(), any())
            } returns
                Either.Right(uploadedPreview to previewHash)
        }
        val preview = AssetContent.Preview(
            "image/jpeg", 1024, 512, 288,
            AssetContent.RemoteData(ByteArray(32) { 1 }, byteArrayOf(), "temp-preview", null, "", MessageEncryptionAlgorithm.AES_CBC),
        )
        uploadAsset(assetMessage(), uploadMetadata.copy(mimeType = "video/mp4", preview = preview))
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage(matches { (it.content as? MessageContent.Asset)?.value?.preview?.remoteData?.let { remote ->
                remote.assetId == uploadedPreview.key && remote.assetDomain == uploadedPreview.domain &&
                    remote.assetToken == uploadedPreview.assetToken && remote.sha256.contentEquals(previewHash.data)
            } == true })
        }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.assetDataSource.deleteAssetLocally("temp-preview") }
    }

    @Test
    fun cancellationDuringPreviewUploadKeepsPendingMessageAndItsCache() = runTest(testDispatcher.default) {
        val previewUploadStarted = CompletableDeferred<Unit>()
        val (arrangement, uploadAsset) = Arrangement().arrange {
            withUploadSuccess()
            everySuspend { assetDataSource.fetchPreviewAsset(any()) } returns
                Either.Right(FetchedAssetData("preview.jpg".toPath(), false))
            everySuspend {
                assetDataSource.uploadAndPersistPrivateAsset(eq("image/jpeg"), any(), any(), any(), any(), any(), any())
            } calls {
                previewUploadStarted.complete(Unit)
                awaitCancellation()
            }
        }
        val preview = AssetContent.Preview(
            "image/jpeg", 1024, 512, 288,
            AssetContent.RemoteData(ByteArray(32) { 1 }, byteArrayOf(), "temp-preview", null, "", MessageEncryptionAlgorithm.AES_CBC),
        )
        val upload = launch { uploadAsset(assetMessage(), uploadMetadata.copy(mimeType = "video/mp4", preview = preview)) }
        previewUploadStarted.await()
        upload.cancelAndJoin()

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.persistMessage(any())
            arrangement.assetDataSource.deleteAssetLocally(any())
            arrangement.messageSender.sendMessage(any(), any())
        }
    }

    private fun testGeneratingAudioNormalizedLoudness(
        mimeType: String,
        audioNormalizedLoudness: ByteArray?,
        builderInvoked: Boolean
    ) =
        runTest(testDispatcher.default) {
            val (arrangement, uploadAsset) = Arrangement().arrange {
                withUploadSuccess()
                withAudioNormalizedLoudnessBuilderResult(uploadMetadata.assetDataPath.toString(), byteArrayOf(1, 2, 3))
            }

            uploadAsset(assetMessage(), uploadMetadata.copy(mimeType = mimeType, audioNormalizedLoudness = audioNormalizedLoudness))

            arrangement.audioNormalizedLoudnessBuilder.assertInvoked(
                uploadMetadata.assetDataPath.toString(),
                if (builderInvoked) 1 else 0
            )
        }

    @Test
    fun givenNonAudioAsset_whenSending_thenDoNotGenerateItWhenSending() =
        testGeneratingAudioNormalizedLoudness("image/png", null, false)

    @Test
    fun givenAudioAssetWithoutNormalizedLoudness_whenSending_thenGenerateItWhenSending() =
        testGeneratingAudioNormalizedLoudness("audio/wav", null, true)

    @Test
    fun givenAudioAssetAlreadyWithNormalizedLoudness_whenSending_thenDoNotGenerateItWhenSending() =
        testGeneratingAudioNormalizedLoudness("audio/wav", byteArrayOf(1, 2, 3), false)

    private class Arrangement {

        suspend fun withUploadSuccess() = apply {
            val assetId = UploadedAssetId("remote-asset-id", "some-domain")
            val sha256Key = SHA256Key(byteArrayOf())
            everySuspend {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Pair(assetId, sha256Key).right()
        }

        suspend fun withUploadFailure() = apply {
            everySuspend {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns CoreFailure.Unknown(null).left()
        }

        fun withAudioNormalizedLoudnessBuilderResult(filePath: String, result: ByteArray?) = apply {
            audioNormalizedLoudnessBuilder.every(filePath, result)
        }

        fun withPersistMessageFailure() = apply {
            persistMessageResult = CoreFailure.Unknown(null).left()
        }

        val assetDataSource: AssetRepository = mock(mode = MockMode.autoUnit)
        val messageSender: MessageSender = mock(mode = MockMode.autoUnit)
        val messageSendFailureHandler: MessageSendFailureHandler = mock(mode = MockMode.autoUnit)
        val updateAssetMessageTransferStatus: UpdateAssetMessageTransferStatusUseCase =
            mock(mode = MockMode.autoUnit)
        val persistMessage: PersistMessageUseCase = mock(mode = MockMode.autoUnit)
        val audioNormalizedLoudnessBuilder = AudioNormalizedLoudnessBuilderMock()
        var persistMessageResult: Either<CoreFailure, Unit> = Unit.right()
        var sendMessageResult: Either<CoreFailure, Unit> = Unit.right()
        var messageSendGate: CompletableDeferred<Unit>? = null
        var updateAudioNormalizedLoudness: UpdateAudioMessageNormalizedLoudnessUseCase = mock(mode = MockMode.autoUnit)
        private var pendingMessages = true

        fun withPendingMessagesDisabled() = apply {
            pendingMessages = false
        }

        suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, UploadAssetUseCaseImpl> {
            block()

            everySuspend {
                updateAssetMessageTransferStatus(any(), any(), any())
            } returns UpdateTransferStatusResult.Success

            everySuspend {
                assetDataSource.deleteAssetLocally(any())
            } returns Unit.right()

            everySuspend {
                persistMessage(any())
            } returns persistMessageResult

            everySuspend {
                messageSender.sendMessage(any(), any())
            } calls {
                messageSendGate?.await()
                sendMessageResult
            }

            everySuspend { messageSender.sendMessageWithOutcome(any(), any()) } calls {
                messageSendGate?.await()
                sendMessageResult.map { true }
            }

            return this to UploadAssetUseCaseImpl(
                assetDataSource = assetDataSource,
                messageSender = messageSender,
                messageSendFailureHandler = messageSendFailureHandler,
                updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
                updateAudioNormalizedLoudness = updateAudioNormalizedLoudness,
                persistMessage = persistMessage,
                audioNormalizedLoudnessBuilder = audioNormalizedLoudnessBuilder,
                pendingMessagesEnabled = pendingMessages,
                dispatcher = testDispatcher,
            )

        }
    }

    private companion object {
        val testDispatcher = TestKaliumDispatcher
        private val uploadMetadata = UploadAssetMessageMetadata(
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
    }
}
