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

package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.framework.TestAsset.dummyUploadedAssetId
import com.wire.kalium.logic.framework.TestAsset.mockedLongAssetData
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.message.MessageEntity
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleNewAssetMessageUseCaseTest {

    @Test
    fun givenAValidSendAssetMessageRequest_whenSendingAssetMessage_thenShouldReturnASuccessResult() = runTest(testDispatcher.default) {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendAssetUseCase) = Arrangement(this)
            .withStoredData(assetToSend, inputDataPath)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .withObserveMessageVisibility()
            .withDeleteAssetLocally()
            .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = inputDataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Success)
    }

    @Test
    fun givenAValidSendAssetMessageRequest_whenThereIsAnAssetUploadError_thenShouldStillReturnSuccessResult() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val unauthorizedException = TestNetworkException.missingAuth
            val (_, sendAssetUseCase) = Arrangement(this)
                .withUploadAssetErrorResponse(unauthorizedException)
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withDeleteAssetLocally()
                .withObserveMessageVisibility()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            val result = sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )
            advanceUntilIdle()

            // Then
            assertTrue(result is ScheduleNewAssetMessageResult.Success)
        }

    @Test
    fun givenAValidSendAssetMessageRequest_whenThereIsAnAssetUploadError_thenAssetShouldStillBeSavedInitiallyWithStatusUploadFailed() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val unauthorizedException = TestNetworkException.missingAuth
            val (arrangement, sendAssetUseCase) = Arrangement(this)
                .withUploadAssetErrorResponse(unauthorizedException)
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withObserveMessageVisibility()
                .withDeleteAssetLocally()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )
            advanceUntilIdle()

            // Then
            verify(arrangement.assetDataSource)
                .suspendFunction(arrangement.assetDataSource::persistAsset)
                .with(any(), any(), eq(dataPath), any(), any())
                .wasInvoked(exactly = once)
            verify(arrangement.updateUploadStatus)
                .suspendFunction(arrangement.updateUploadStatus::invoke)
                .with(matching { it == Message.UploadStatus.FAILED_UPLOAD }, any(), any())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenASuccessfulSendAssetMessageRequest_whenSendingTheAsset_thenTheAssetIsPersisted() = runTest(testDispatcher.default) {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
            .withObserveMessageVisibility()
            .withDeleteAssetLocally()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()

        // When
        sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = dataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )

        advanceUntilIdle()

        // Then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = twice)
        verify(arrangement.assetDataSource)
            .suspendFunction(arrangement.assetDataSource::uploadAndPersistPrivateAsset)
            .with(any(), any(), any(), any())
            .wasInvoked(exactly = once)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendMessage)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.messageSendFailureHandler)
            .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
            .with(any(), any(), any(), any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenASuccessfulSendAssetMessageRequest_whenSendingTheAssetAndMessageFails_thenTheMessageStatusIsUpdatedToFailed() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val expectedAssetId = dummyUploadedAssetId
            val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
            val (arrangement, sendAssetUseCase) = Arrangement(this)
                .withUnsuccessfulSendMessageResponse(expectedAssetId, expectedAssetSha256)
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withObserveMessageVisibility()
                .withDeleteAssetLocally()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )

            advanceUntilIdle()

            // Then
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(any())
                .wasInvoked(exactly = twice)
            verify(arrangement.assetDataSource)
                .suspendFunction(arrangement.assetDataSource::uploadAndPersistPrivateAsset)
                .with(any(), any(), any(), any())
                .wasInvoked(exactly = once)
            verify(arrangement.messageSender)
                .suspendFunction(arrangement.messageSender::sendMessage)
                .with(any())
                .wasInvoked(exactly = once)
            verify(arrangement.messageSendFailureHandler)
                .suspendFunction(arrangement.messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .with(any(), any(), any(), any(), any())
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenASuccessfulSendAssetMessageRequest_whenCheckingTheMessageRepository_thenTheAssetIsMarkedAsSavedInternally() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val expectedAssetId = dummyUploadedAssetId
            val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
            val (arrangement, sendAssetUseCase) = Arrangement(this)
                .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withObserveMessageVisibility()
                .withDeleteAssetLocally()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )
            advanceUntilIdle()

            // Then
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(
                    matching {
                        val content = it.content
                        content is MessageContent.Asset && content.value.downloadStatus == Message.DownloadStatus.SAVED_INTERNALLY
                    }
                )
                .wasInvoked(exactly = twice)
        }

    @Test
    fun givenAnErrorAtInitialAssetPersistCall_whenCheckingTheMessageRepository_thenTheAssetUploadStatusIsMarkedAsFailed() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val (arrangement, sendAssetUseCase) = Arrangement(this)
                .withPersistAssetErrorResponse()
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withObserveMessageVisibility()
                .withDeleteAssetLocally()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )
            advanceUntilIdle()

            // Then
            verify(arrangement.assetDataSource)
                .suspendFunction(arrangement.assetDataSource::persistAsset)
                .with(any(), any(), eq(dataPath), any(), any())
                .wasInvoked(once)
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.updateUploadStatus)
                .suspendFunction(arrangement.updateUploadStatus::invoke)
                .with(
                    matching {
                        it == Message.UploadStatus.FAILED_UPLOAD
                    },
                    any(), any()
                )
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenAnErrorAtInitialMessagePersistCall_whenCheckingTheMessageRepository_thenTheAssetUploadStatusIsMarkedAsFailed() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val (arrangement, sendAssetUseCase) = Arrangement(this)
                .withPersistMessageErrorResponse()
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withObserveMessageVisibility()
                .withDeleteAssetLocally()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )
            advanceUntilIdle()

            // Then
            verify(arrangement.assetDataSource)
                .suspendFunction(arrangement.assetDataSource::persistAsset)
                .with(any(), any(), eq(dataPath), any(), any())
                .wasInvoked(once)
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(
                    matching {
                        val content = it.content
                        content is MessageContent.Asset && content.value.uploadStatus == Message.UploadStatus.UPLOAD_IN_PROGRESS
                    }
                )
                .wasInvoked(exactly = once)
            verify(arrangement.updateUploadStatus)
                .suspendFunction(arrangement.updateUploadStatus::invoke)
                .with(
                    matching {
                        it == Message.UploadStatus.FAILED_UPLOAD
                    },
                    any(), any()
                )
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenASuccessfulSendAssetMessageRequest_whenCheckingTheMessageRepository_thenTheAssetIsMarkedAsUploaded() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val expectedAssetId = dummyUploadedAssetId
            val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
            val (arrangement, sendAssetUseCase) = Arrangement(this)
                .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
                .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
                .withObserveMessageVisibility()
                .withDeleteAssetLocally()
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
                .arrange()

            // When
            sendAssetUseCase.invoke(
                conversationId = conversationId,
                assetDataPath = dataPath,
                assetDataSize = assetToSend.size.toLong(),
                assetName = assetName,
                assetMimeType = "text/plain",
                assetWidth = null,
                assetHeight = null,
                audioLengthInMs = 0
            )
            advanceUntilIdle()

            // Then
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(
                    matching {
                        val content = it.content
                        content is MessageContent.Asset && content.value.uploadStatus == Message.UploadStatus.UPLOADED
                    }
                )
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(any())
                .wasInvoked(exactly = twice)
        }

    @Test
    fun givenMessageTimerIsDisabled_whenSendingAssetMessage_thenTimerIsNull() = runTest(testDispatcher.default) {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withStoredData(assetToSend, inputDataPath)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
            .withObserveMessageVisibility()
            .withDeleteAssetLocally()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = inputDataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Success)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                assertIs<Message.Regular>(it)
                it.expirationData == null
            })
    }

    @Test
    fun givenMessageTimerIsSet_whenSendingAssetMessage_thenTimerIsCorrect() = runTest(testDispatcher.default) {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val conversationId = ConversationId("some-convo-id", "some-domain-id")

        val expectedDuration = Duration.parse("PT1H")
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withStoredData(assetToSend, inputDataPath)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .withSelfDeleteTimer(SelfDeletionTimer.Enabled(expectedDuration))
            .withObserveMessageVisibility()
            .withDeleteAssetLocally()
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = inputDataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Success)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                assertIs<Message.Regular>(it)
                it.expirationData == Message.ExpirationData(expectedDuration)
            })
    }

    @Test
    fun givenFileSendingRestrictedByTeam_whenSending_thenReturnDisabledByTeam() = runTest {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendAssetUseCase) = Arrangement(this)
            .withStoredData(assetToSend, inputDataPath)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.Disabled)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = inputDataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Failure.DisabledByTeam)
    }

    @Test
    fun givenAseetMimeTypeRestricted_whenSending_thenReturnRestrictedFileType() = runTest {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withStoredData(assetToSend, inputDataPath)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledSome(listOf("png")))
            .withValidateAsseMimeTypeResult(false)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = inputDataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "text/plain",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Failure.RestrictedFileType)

        verify(arrangement.validateAssetMimeTypeUseCase)
            .function(arrangement.validateAssetMimeTypeUseCase::invoke)
            .with(eq("some-asset.txt"), eq("text/plain"), eq(listOf("png")))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAssetMimeTypeRestrictedAndFileAllowed_whenSending_thenReturnSendTheFile() = runTest(testDispatcher.default) {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.png"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (arrangement, sendAssetUseCase) = Arrangement(this)
            .withStoredData(assetToSend, inputDataPath)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledSome(listOf("png")))
            .withValidateAsseMimeTypeResult(true)
            .withSelfDeleteTimer(SelfDeletionTimer.Disabled)
            .withObserveMessageVisibility()
            .withDeleteAssetLocally()
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId = conversationId,
            assetDataPath = inputDataPath,
            assetDataSize = assetToSend.size.toLong(),
            assetName = assetName,
            assetMimeType = "image/png",
            assetWidth = null,
            assetHeight = null,
            audioLengthInMs = 0
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is ScheduleNewAssetMessageResult.Success)

        verify(arrangement.validateAssetMimeTypeUseCase)
            .function(arrangement.validateAssetMimeTypeUseCase::invoke)
            .with(eq("some-asset.png"), eq("image/png"), eq(listOf("png")))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(val coroutineScope: CoroutineScope) {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val assetDataSource = mock(classOf<AssetRepository>())

        @Mock
        private val slowSyncRepository = mock(classOf<SlowSyncRepository>())

        @Mock
        val updateUploadStatus = mock(classOf<UpdateAssetMessageUploadStatusUseCase>())

        @Mock
        private val userPropertyRepository = mock(classOf<UserPropertyRepository>())

        @Mock
        val messageSendFailureHandler: MessageSendFailureHandler = mock(MessageSendFailureHandler::class)

        @Mock
        val observeSelfDeletionTimerSettingsForConversation = mock(classOf<ObserveSelfDeletionTimerSettingsForConversationUseCase>())

        @Mock
        private val messageRepository: MessageRepository = mock(MessageRepository::class)

        @Mock
        val validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase = mock(ValidateAssetFileTypeUseCase::class)

        @Mock
        val observerFileSharingStatusUseCase: ObserveFileSharingStatusUseCase = mock(ObserveFileSharingStatusUseCase::class)

        val someClientId = ClientId("some-client-id")

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()


        init {
            withToggleReadReceiptsStatus()
        }

        fun withValidateAsseMimeTypeResult(result: Boolean) = apply {
            given(validateAssetMimeTypeUseCase)
                .function(validateAssetMimeTypeUseCase::invoke)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withObserveFileSharingStatusResult(result: FileSharingStatus.Value) = apply {
            given(observerFileSharingStatusUseCase)
                .function(observerFileSharingStatusUseCase::invoke)
                .whenInvoked()
                .thenReturn(flowOf(FileSharingStatus(result, false)))
        }

        fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::getReadReceiptsStatus)
                .whenInvoked()
                .thenReturn(enabled)
        }

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
            return this
        }

        fun withSuccessfulResponse(
            expectedAssetId: UploadedAssetId,
            assetSHA256Key: SHA256Key,
            temporaryAssetId: String = "temporary_id"
        ): Arrangement = apply {
            given(assetDataSource)
                .suspendFunction(assetDataSource::persistAsset)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath(temporaryAssetId)))
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any(), any(), any())
                .thenReturn(Either.Right(expectedAssetId to assetSHA256Key))
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(completeStateFlow)
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Success)
        }

        fun withUnsuccessfulSendMessageResponse(
            expectedAssetId: UploadedAssetId,
            assetSHA256Key: SHA256Key,
            temporaryAssetId: String = "temporary_id"
        ): Arrangement = apply {
            given(assetDataSource)
                .suspendFunction(assetDataSource::persistAsset)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath(temporaryAssetId)))
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any(), any(), any())
                .thenReturn(Either.Right(expectedAssetId to assetSHA256Key))
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(completeStateFlow)
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(messageSender)
                .suspendFunction(messageSender::sendMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
            given(messageSendFailureHandler)
                .suspendFunction(messageSendFailureHandler::handleFailureAndUpdateMessageStatus)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Unit)
        }

        fun withUploadAssetErrorResponse(exception: KaliumException): Arrangement = apply {
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(completeStateFlow)
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(assetDataSource)
                .suspendFunction(assetDataSource::persistAsset)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath("temporary_id")))
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any(), any(), any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        fun withPersistMessageErrorResponse() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(completeStateFlow)
            given(assetDataSource)
                .suspendFunction(assetDataSource::persistAsset)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath("temporary_id")))
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(StorageFailure.Generic(IOException("Some error"))))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        fun withPersistAssetErrorResponse() = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(slowSyncRepository)
                .getter(slowSyncRepository::slowSyncStatus)
                .whenInvoked()
                .thenReturn(completeStateFlow)
            given(assetDataSource)
                .suspendFunction(assetDataSource::persistAsset)
                .whenInvokedWith(any(), any(), any(), any(), any())
                .thenReturn(Either.Left(StorageFailure.Generic(IOException("Some error"))))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        fun withSelfDeleteTimer(result: SelfDeletionTimer) = apply {
            given(observeSelfDeletionTimerSettingsForConversation)
                .suspendFunction(observeSelfDeletionTimerSettingsForConversation::invoke)
                .whenInvokedWith(any())
                .thenReturn(flowOf(result))
        }

        fun withObserveMessageVisibility() = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::observeMessageVisibility)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Right(MessageEntity.Visibility.VISIBLE)))
        }

        fun withDeleteAssetLocally() = apply {
            given(assetDataSource)
                .suspendFunction(assetDataSource::deleteAssetLocally)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }


        fun arrange() = this to ScheduleNewAssetMessageUseCaseImpl(
            persistMessage,
            updateUploadStatus,
            currentClientIdProvider,
            assetDataSource,
            QualifiedID("some-id", "some-domain"),
            slowSyncRepository,
            messageSender,
            messageSendFailureHandler,
            messageRepository,
            userPropertyRepository,
            observeSelfDeletionTimerSettingsForConversation,
            coroutineScope,
            observerFileSharingStatusUseCase,
            validateAssetMimeTypeUseCase,
            testDispatcher
        )
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
        val testDispatcher = TestKaliumDispatcher
    }
}
