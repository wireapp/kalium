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
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.message.MessageSendFailureHandler
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
<<<<<<< HEAD
=======
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
>>>>>>> 596eb022e3 (fix: asset restriction [WPB-9947] (#2831) (#2856) (#2861))
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
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
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
            coVerify {
                arrangement.assetDataSource.persistAsset(any(), any(), eq(dataPath), any(), any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.updateTransferStatus.invoke(matches { it == AssetTransferStatus.FAILED_UPLOAD }, any(), any())
            }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.persistMessage.invoke(any())
        }.wasInvoked(exactly = twice)
        coVerify {
            arrangement.assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageSender.sendMessage(any(), any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
        }.wasNotInvoked()
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
<<<<<<< HEAD
                .withUpdateMessageAssetTransferStatus(UpdateTransferStatusResult.Success)
=======
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
>>>>>>> 596eb022e3 (fix: asset restriction [WPB-9947] (#2831) (#2856) (#2861))
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
<<<<<<< HEAD
            coVerify {
                arrangement.persistMessage.invoke(any())
            }.wasInvoked(exactly = twice)
            coVerify {
                arrangement.assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.messageSender.sendMessage(any(), any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
            }.wasInvoked(exactly = once)
=======
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
>>>>>>> 596eb022e3 (fix: asset restriction [WPB-9947] (#2831) (#2856) (#2861))
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
            coVerify {
                arrangement.assetDataSource.persistAsset(any(), any(), eq(dataPath), any(), any())
            }.wasInvoked(once)
            coVerify {
                arrangement.persistMessage.invoke(any())
            }.wasNotInvoked()
            coVerify {
                arrangement.updateTransferStatus.invoke(
                    matches {
                        it == AssetTransferStatus.FAILED_UPLOAD
                    },
                    any(),
                    any()
                )
            }.wasInvoked(exactly = once)
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
<<<<<<< HEAD
                .withUpdateMessageAssetTransferStatus(UpdateTransferStatusResult.Success)
=======
                .withObserveFileSharingStatusResult(FileSharingStatus.Value.EnabledAll)
>>>>>>> 596eb022e3 (fix: asset restriction [WPB-9947] (#2831) (#2856) (#2861))
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
            coVerify {
                arrangement.assetDataSource.persistAsset(any(), any(), eq(dataPath), any(), any())
            }.wasInvoked(once)
            coVerify {
                arrangement.persistMessage.invoke(
                    matches {
                        val content = it.content
                        content is MessageContent.Asset
                    }
                )
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.updateTransferStatus.invoke(
                    matches {
                        it == AssetTransferStatus.FAILED_UPLOAD
                    },
                    any(),
                    any()
                )
            }.wasInvoked(exactly = once)
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
            coVerify {
                arrangement.persistMessage.invoke(
                    matches {
                        val content = it.content
                        content is MessageContent.Asset
                    }
                )
            }.wasInvoked(exactly = twice)

            coVerify {
                arrangement.updateTransferStatus.invoke(
                    matches {
                        it == AssetTransferStatus.UPLOADED
                    },
                    any(),
                    any()
                )
            }.wasInvoked(exactly = once)
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

        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    assertIs<Message.Regular>(it)
                    it.expirationData == null
                }
            )
        }.wasInvoked(exactly = twice)
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

        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    assertIs<Message.Regular>(it)
                    it.expirationData == Message.ExpirationData(expectedDuration)
                }
            )
        }
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
            .with(eq("text/plain"), eq(listOf("png")))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAssetMimeTypeRestrictedAndFileAllowed_whenSending_thenReturnSendTheFile() = runTest(testDispatcher.default) {
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
            .with(eq("image/png"), eq(listOf("png")))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(val coroutineScope: CoroutineScope) {

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val messageSender = mock(MessageSender::class)

        @Mock
        private val currentClientIdProvider = mock(CurrentClientIdProvider::class)

        @Mock
        val assetDataSource = mock(AssetRepository::class)

        @Mock
        private val slowSyncRepository = mock(SlowSyncRepository::class)

        @Mock
        val updateTransferStatus = mock(UpdateAssetMessageTransferStatusUseCase::class)

        @Mock
        private val userPropertyRepository = mock(UserPropertyRepository::class)

        @Mock
        val messageSendFailureHandler: MessageSendFailureHandler = mock(MessageSendFailureHandler::class)

        @Mock
        val observeSelfDeletionTimerSettingsForConversation = mock(ObserveSelfDeletionTimerSettingsForConversationUseCase::class)

        @Mock
        private val messageRepository: MessageRepository = mock(MessageRepository::class)

        @Mock
        val validateAssetMimeTypeUseCase: ValidateAssetMimeTypeUseCase = mock(ValidateAssetMimeTypeUseCase::class)

        @Mock
        val observerFileSharingStatusUseCase: ObserveFileSharingStatusUseCase = mock(ObserveFileSharingStatusUseCase::class)

        val someClientId = ClientId("some-client-id")

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

<<<<<<< HEAD
        suspend fun withToggleReadReceiptsStatus(enabled: Boolean = false) = apply {
            coEvery {
                userPropertyRepository.getReadReceiptsStatus()
            }.returns(enabled)
=======

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
>>>>>>> 596eb022e3 (fix: asset restriction [WPB-9947] (#2831) (#2856) (#2861))
        }

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
            return this
        }

        suspend fun withSuccessfulResponse(
            expectedAssetId: UploadedAssetId,
            assetSHA256Key: SHA256Key,
            temporaryAssetId: String = "temporary_id"
        ): Arrangement = apply {
            coEvery {
                assetDataSource.persistAsset(any(), any(), any(), any(), any())
            }.returns(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath(temporaryAssetId)))
            coEvery {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any())
            }.returns(Either.Right(expectedAssetId to assetSHA256Key))
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(someClientId))
            every { slowSyncRepository.slowSyncStatus }
                .returns(completeStateFlow)
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Right(Unit))
            coEvery {
                updateTransferStatus.invoke(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Success)
        }

        suspend fun withUnsuccessfulSendMessageResponse(
            expectedAssetId: UploadedAssetId,
            assetSHA256Key: SHA256Key,
            temporaryAssetId: String = "temporary_id"
        ): Arrangement = apply {
            coEvery {
                assetDataSource.persistAsset(any(), any(), any(), any(), any())
            }.returns(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath(temporaryAssetId)))
            coEvery {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any())
            }.returns(Either.Right(expectedAssetId to assetSHA256Key))
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(someClientId))
            every { slowSyncRepository.slowSyncStatus }
                .returns(completeStateFlow)
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
            coEvery {
                messageSender.sendMessage(any(), any())
            }.returns(Either.Left(CoreFailure.Unknown(RuntimeException("some error"))))
            coEvery {
                messageSendFailureHandler.handleFailureAndUpdateMessageStatus(any(), any(), any(), any(), any())
            }.returns(Unit)
        }

        suspend fun withUploadAssetErrorResponse(exception: KaliumException): Arrangement = apply {
            every { slowSyncRepository.slowSyncStatus }
                .returns(completeStateFlow)
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(someClientId))
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
            coEvery {
                assetDataSource.persistAsset(any(), any(), any(), any(), any())
            }.returns(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath("temporary_id")))
            coEvery {
                assetDataSource.uploadAndPersistPrivateAsset(any(), any(), any(), any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            coEvery {
                updateTransferStatus.invoke(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        suspend fun withPersistMessageErrorResponse() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(someClientId))
            every { slowSyncRepository.slowSyncStatus }
                .returns(completeStateFlow)
            coEvery {
                assetDataSource.persistAsset(any(), any(), any(), any(), any())
            }.returns(Either.Right(fakeKaliumFileSystem.providePersistentAssetPath("temporary_id")))
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Left(StorageFailure.Generic(IOException("Some error"))))
            coEvery {
                updateTransferStatus.invoke(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        suspend fun withPersistAssetErrorResponse() = apply {
            coEvery {
                currentClientIdProvider.invoke()
            }.returns(Either.Right(someClientId))
            every { slowSyncRepository.slowSyncStatus }
                .returns(completeStateFlow)
            coEvery {
                assetDataSource.persistAsset(any(), any(), any(), any(), any())
            }.returns(Either.Left(StorageFailure.Generic(IOException("Some error"))))
            coEvery {
                updateTransferStatus.invoke(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        suspend fun withSelfDeleteTimer(result: SelfDeletionTimer) = apply {
            coEvery {
                observeSelfDeletionTimerSettingsForConversation.invoke(any(), any())
            }.returns(flowOf(result))
        }

        suspend fun withObserveMessageVisibility() = apply {
            coEvery {
                messageRepository.observeMessageVisibility(any(), any())
            }.returns(flowOf(Either.Right(MessageEntity.Visibility.VISIBLE)))
        }

        suspend fun withDeleteAssetLocally() = apply {
            coEvery {
                assetDataSource.deleteAssetLocally(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateMessageAssetTransferStatus(result: UpdateTransferStatusResult) = apply {
            coEvery {
                updateTransferStatus.invoke(any(), any(), any())
            }.returns(result)
        }

        suspend fun arrange() = this to ScheduleNewAssetMessageUseCaseImpl(
            persistMessage,
            updateTransferStatus,
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
        ).also {
            withToggleReadReceiptsStatus()
        }
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
        val testDispatcher = TestKaliumDispatcher
    }
}
