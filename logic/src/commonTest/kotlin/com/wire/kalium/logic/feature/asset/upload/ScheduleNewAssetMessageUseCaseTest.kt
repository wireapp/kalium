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
import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.asset.UpdateAssetMessageTransferStatusUseCase
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.persistence.dao.message.MessageEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verify
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import kotlin.test.Test
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

    private class Arrangement(val coroutineScope: CoroutineScope) {
        val persistNewAssetMessage = mock<PersistNewAssetMessageUseCase>(mode = MockMode.autoUnit)
        val uploadAsset = mock<UploadAssetUseCase>(mode = MockMode.autoUnit)
        private val slowSyncRepository = mock<SlowSyncRepository>(mode = MockMode.autoUnit)
        val updateTransferStatus = mock<UpdateAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)
        private val messageRepository: MessageRepository = mock(mode = MockMode.autoUnit)
        val validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase = mock(mode = MockMode.autoUnit)
        val observerFileSharingStatusUseCase: ObserveFileSharingStatusUseCase = mock(mode = MockMode.autoUnit)
        val messageSendFailureHandler: MessageSendFailureHandler = mock(mode = MockMode.autoUnit)

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

        suspend fun withAssetMessagePersistSuccess() = apply {
            everySuspend {
                persistNewAssetMessage.invoke(any(), any(), any())
            } returns Pair(uploadMetadata, TestMessage.assetMessage()).right()
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

        fun arrange() = this to ScheduleNewAssetMessageUseCaseImpl(
            persistNewAssetMessage,
            uploadAsset,
            updateTransferStatus,
            QualifiedID("some-id", "some-domain"),
            slowSyncRepository,
            messageRepository,
            observerFileSharingStatusUseCase,
            validateAssetMimeTypeUseCase,
            messageSendFailureHandler,
            coroutineScope,
            testDispatcher
        ).also {
            every { slowSyncRepository.slowSyncStatus } returns completeStateFlow
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
