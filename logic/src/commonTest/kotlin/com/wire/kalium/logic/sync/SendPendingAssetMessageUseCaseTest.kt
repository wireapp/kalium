/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageThreadRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilderMock
import com.wire.kalium.logic.feature.asset.GetAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.UpdateTransferStatusResult
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.framework.TestAsset.mockedLongAssetData
import com.wire.kalium.logic.framework.TestMessage.ASSET_CONTENT
import com.wire.kalium.logic.framework.TestMessage.assetMessage
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.fileExtension
import com.wire.kalium.messaging.sending.MessageSender
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matching
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import kotlin.test.Test

class SendPendingAssetMessageUseCaseTest {

    @Test
    fun givenFailedUploadStatus_whenInvoked_thenFetchesLocalAssetAndUploads() = runTest {
        val name = "photo.jpg"
        val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val message = assetMessage().copy(content = content)
        val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
        val uploadedSha = SHA256Key(byteArrayOf())

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedSha))
            .withPersistMessage(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(
                mimeType = any(),
                assetDataPath = path,
                otrKey = any(),
                extension = name.fileExtension(),
                conversationId = any(),
                filename = any(),
                filetype = any()
            )
        }
    }

    @Test
    fun givenFailedUploadStatus_whenUploadSucceeds_thenPersistsUpdatedMessageAndSends() = runTest {
        val name = "photo.jpg"
        val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val message = assetMessage().copy(content = content)
        val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
        val uploadedSha = SHA256Key(byteArrayOf())

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedSha))
            .withPersistMessage(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matching {
                    it.id == message.id && it.content is MessageContent.Asset
                            && (it.content as MessageContent.Asset).value.remoteData.assetId == uploadedAssetId.key
                            && (it.content as MessageContent.Asset).value.remoteData.assetDomain == uploadedAssetId.domain
                }
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(
                matching { m ->
                    m.id == message.id && m.content is MessageContent.Asset
                            && (m.content as MessageContent.Asset).value.remoteData.assetId == uploadedAssetId.key
                },
                any(),
                any()
            )
        }
    }

    @Test
    fun givenThreadedMessageWithFailedUploadStatus_whenUploadSucceeds_thenSendsWithThreadId() = runTest {
        val name = "photo.jpg"
        val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val message = assetMessage().copy(content = content)
        val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
        val uploadedSha = SHA256Key(byteArrayOf())

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedSha))
            .withPersistMessage(Either.Right(Unit))
            .withThreadIdForMessage(TEST_THREAD_ID)
            .withSendMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendMessage(any(), any(), eq(TEST_THREAD_ID))
        }
    }

    @Test
    fun givenAudioAssetWithoutNormalizedLoudness_whenUploadSucceeds_thenBuildsAndPersistsNormalizedLoudness() = runTest {
        val name = "audio.wav"
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val normalizedLoudness = byteArrayOf(1, 2, 3)
        val content = MessageContent.Asset(
            ASSET_CONTENT.value.copy(
                name = name,
                mimeType = "audio/wav",
                metadata = AssetContent.AssetMetadata.Audio(durationMs = 1_000L, normalizedLoudness = null)
            )
        )
        val message = assetMessage().copy(content = content)
        val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
        val uploadedSha = SHA256Key(byteArrayOf())

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withAudioNormalizedLoudnessBuilderResult(path.toString(), normalizedLoudness)
            .withUploadAndPersistPrivateAssetDeletingSourcePath(Either.Right(uploadedAssetId to uploadedSha))
            .withPersistMessage(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        arrangement.audioNormalizedLoudnessBuilder.assertInvoked(path.toString())
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matching {
                    val assetContent = (it.content as? MessageContent.Asset)?.value
                    val metadata = assetContent?.metadata as? AssetContent.AssetMetadata.Audio
                    it.id == message.id &&
                            assetContent?.remoteData?.assetId == uploadedAssetId.key &&
                            metadata != null &&
                            metadata.durationMs == 1_000L &&
                            metadata.normalizedLoudness.contentEquals(normalizedLoudness)
                }
            )
        }
    }

    @Test
    fun givenAudioAssetAlreadyWithNormalizedLoudness_whenUploadSucceeds_thenPreservesItWithoutBuilding() = runTest {
        val name = "audio.wav"
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val existingLoudness = byteArrayOf(4, 5, 6)
        val content = MessageContent.Asset(
            ASSET_CONTENT.value.copy(
                name = name,
                mimeType = "audio/wav",
                metadata = AssetContent.AssetMetadata.Audio(durationMs = 2_000L, normalizedLoudness = existingLoudness)
            )
        )
        val message = assetMessage().copy(content = content)
        val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
        val uploadedSha = SHA256Key(byteArrayOf())

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedSha))
            .withPersistMessage(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        arrangement.audioNormalizedLoudnessBuilder.assertInvoked(path.toString(), 0)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matching {
                    val metadata = ((it.content as? MessageContent.Asset)?.value?.metadata as? AssetContent.AssetMetadata.Audio)
                    metadata != null &&
                            metadata.durationMs == 2_000L &&
                            metadata.normalizedLoudness.contentEquals(existingLoudness)
                }
            )
        }
    }

    @Test
    fun givenNonAudioAsset_whenUploadSucceeds_thenDoesNotBuildNormalizedLoudness() = runTest {
        val name = "photo.jpg"
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val content = MessageContent.Asset(
            ASSET_CONTENT.value.copy(
                name = name,
                mimeType = "image/jpeg",
                metadata = AssetContent.AssetMetadata.Image(width = 100, height = 100)
            )
        )
        val message = assetMessage().copy(content = content)
        val uploadedAssetId = UploadedAssetId("remote_key", "remote_domain", "remote_token")
        val uploadedSha = SHA256Key(byteArrayOf())

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withUploadAndPersistPrivateAsset(Either.Right(uploadedAssetId to uploadedSha))
            .withPersistMessage(Either.Right(Unit))
            .withSendMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        arrangement.audioNormalizedLoudnessBuilder.assertInvoked(path.toString(), 0)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matching {
                    val metadata = ((it.content as? MessageContent.Asset)?.value?.metadata as? AssetContent.AssetMetadata.Image)
                    metadata != null && metadata.width == 100 && metadata.height == 100
                }
            )
        }
    }

    @Test
    fun givenFailedUploadStatus_whenUploadFails_thenSetsFailedUploadAndFailedStatus() = runTest {
        val name = "photo.jpg"
        val content = MessageContent.Asset(ASSET_CONTENT.value.copy(name = name))
        val path = fakeKaliumFileSystem.providePersistentAssetPath(name)
        val message = assetMessage().copy(content = content)

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.FAILED_UPLOAD)
            .withUpdateAssetMessageTransferStatus(UpdateTransferStatusResult.Success)
            .withFetchPrivateDecodedAsset(Either.Right(path))
            .withStoredData(mockedLongAssetData(), path)
            .withUploadAndPersistPrivateAsset(Either.Left(NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)))
            .arrange()

        useCase(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateAssetMessageTransferStatus.invoke(
                AssetTransferStatus.FAILED_UPLOAD,
                message.conversationId,
                message.id
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(
                failure = any(),
                conversationId = message.conversationId,
                messageId = message.id,
                messageType = any(),
                scheduleResendIfNoNetwork = true
            )
        }
        arrangement.audioNormalizedLoudnessBuilder.assertInvoked(path.toString(), 0)
    }

    @Test
    fun givenUploadedStatus_whenInvoked_thenCallsSendPendingMessageDirectly() = runTest {
        val message = assetMessage()

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOADED)
            .withSendPendingMessage(Either.Right(Unit))
            .arrange()

        useCase(message)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageSender.sendPendingMessage(message.conversationId, message.id)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenUploadInProgressStatus_whenInvoked_thenSkipsWithoutSendingOrUploading() = runTest {
        val message = assetMessage()

        val (arrangement, useCase) = Arrangement()
            .withGetAssetMessageTransferStatus(AssetTransferStatus.UPLOAD_IN_PROGRESS)
            .arrange()

        useCase(message)

        verifySuspend(VerifyMode.not) {
            arrangement.messageSender.sendPendingMessage(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.assetRepository.uploadAndPersistPrivateAsset(any(), any(), any(), any(), any(), any(), any())
        }
    }

    private inner class Arrangement {
        val assetRepository = mock<AssetRepository>(mode = MockMode.autoUnit)
        val persistMessage = mock<PersistMessageUseCase>(mode = MockMode.autoUnit)
        val updateAssetMessageTransferStatus = mock<UpdateAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)
        val getAssetMessageTransferStatus = mock<GetAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)
        val messageSender = mock<MessageSender>(mode = MockMode.autoUnit)
        val messageSendFailureHandler = mock<MessageSendFailureHandler>(mode = MockMode.autoUnit)
        val messageThreadRepository = mock<MessageThreadRepository>(mode = MockMode.autoUnit)
        val audioNormalizedLoudnessBuilder = AudioNormalizedLoudnessBuilderMock(
            canReadFile = { fakeKaliumFileSystem.exists(it.toPath()) }
        )

        init {
            everySuspend { assetRepository.deleteAssetLocally(any()) } returns Unit.right()
            everySuspend { messageThreadRepository.getThreadIdByMessageId(any(), any()) } returns null.right()
        }

        suspend fun withGetAssetMessageTransferStatus(status: AssetTransferStatus) = apply {
            everySuspend { getAssetMessageTransferStatus.invoke(any(), any()) } returns status
        }

        suspend fun withUpdateAssetMessageTransferStatus(result: UpdateTransferStatusResult) = apply {
            everySuspend { updateAssetMessageTransferStatus.invoke(any(), any(), any()) } returns result
        }

        suspend fun withFetchPrivateDecodedAsset(result: Either<CoreFailure, Path>) = apply {
            everySuspend {
                assetRepository.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns result.map { FetchedAssetData(it, true) }
        }

        fun withStoredData(data: ByteArray, dataPath: Path) = apply {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
        }

        fun withAudioNormalizedLoudnessBuilderResult(filePath: String, result: ByteArray?) = apply {
            audioNormalizedLoudnessBuilder.every(filePath, result)
        }

        suspend fun withUploadAndPersistPrivateAsset(result: Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>>) = apply {
            everySuspend {
                assetRepository.uploadAndPersistPrivateAsset(
                    mimeType = any(),
                    assetDataPath = any(),
                    otrKey = any(),
                    extension = any(),
                    conversationId = any(),
                    filename = any(),
                    filetype = any()
                )
            } returns result
        }

        suspend fun withUploadAndPersistPrivateAssetDeletingSourcePath(
            result: Either<CoreFailure, Pair<UploadedAssetId, SHA256Key>>
        ) = apply {
            everySuspend {
                assetRepository.uploadAndPersistPrivateAsset(
                    mimeType = any(),
                    assetDataPath = any(),
                    otrKey = any(),
                    extension = any(),
                    conversationId = any(),
                    filename = any(),
                    filetype = any()
                )
            } calls { invocation ->
                val assetDataPath = invocation.args[1] as Path
                fakeKaliumFileSystem.delete(assetDataPath)
                result
            }
        }

        suspend fun withPersistMessage(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { persistMessage.invoke(any()) } returns result
        }

        suspend fun withSendMessage(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { messageSender.sendMessage(any(), any(), any()) } returns result
        }

        suspend fun withThreadIdForMessage(threadId: String?) = apply {
            everySuspend { messageThreadRepository.getThreadIdByMessageId(any(), any()) } returns threadId.right()
        }

        suspend fun withSendPendingMessage(result: Either<CoreFailure, Unit>) = apply {
            everySuspend { messageSender.sendPendingMessage(any(), any()) } returns result
        }

        fun arrange() = this to SendPendingAssetMessageUseCaseImpl(
            assetRepository = assetRepository,
            persistMessage = persistMessage,
            updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
            getAssetMessageTransferStatus = getAssetMessageTransferStatus,
            messageSender = messageSender,
            messageSendFailureHandler = messageSendFailureHandler,
            audioNormalizedLoudnessBuilder = audioNormalizedLoudnessBuilder,
            messageThreadRepository = messageThreadRepository,
        )
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
        const val TEST_THREAD_ID = "thread-id"
    }
}
