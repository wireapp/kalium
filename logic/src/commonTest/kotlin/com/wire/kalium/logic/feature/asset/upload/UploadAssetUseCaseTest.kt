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
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderMock
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test

class UploadAssetUseCaseTest {

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
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
            } returns Pair(assetId, sha256Key).right()
        }

        suspend fun withUploadFailure() = apply {
            everySuspend {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
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
        var updateAudioNormalizedLoudness: UpdateAudioMessageNormalizedLoudnessUseCase = mock(mode = MockMode.autoUnit)

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
            } returns Unit.right()

            return this to UploadAssetUseCaseImpl(
                assetDataSource,
                messageSender,
                messageSendFailureHandler,
                updateAssetMessageTransferStatus,
                updateAudioNormalizedLoudness,
                persistMessage,
                audioNormalizedLoudnessBuilder,
                testDispatcher
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
