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

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.encryptFileWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.FetchedAssetData
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GetMessageAssetUseCaseTest {

    private suspend fun getSuccessfulFlowArrangement(
        conversationId: ConversationId = ConversationId("some-conversation-id", "some-domain.com"),
        messageId: String = "some-message-id",
        encryptedPath: Path = "output_encrypted_path".toPath(),
        assetMetadata: AssetContent.AssetMetadata? = AssetContent.AssetMetadata.Image(width = 100, height = 100),
        justDownloaded: Boolean = true
    ): Arrangement {
        val expectedDecodedAsset = byteArrayOf(14, 2, 10, 63, -2, -1, 34, 0, 12, 4, 5, 6, 8, 9, -22, 9, 63)
        val randomAES256Key = generateRandomAES256Key()
        val (fakeFileSystem, rootPath) = createFileSystem()
        val rawDataPath = copyDataToDummyPath(expectedDecodedAsset, rootPath, fakeFileSystem)
        val rawDataSource = fakeFileSystem.source(rawDataPath)
        val encryptedDataSink = fakeFileSystem.sink(encryptedPath)
        encryptFileWithAES256(rawDataSource, randomAES256Key, encryptedDataSink)
        return Arrangement()
            .withSuccessfulFlow(conversationId, messageId, encryptedPath, randomAES256Key, assetMetadata, justDownloaded)
    }

    @Test
    fun givenACallToGetAMessageAsset_whenEverythingGoesWell_thenShouldReturnTheAssetDecodedDataPath() = runTest(testDispatcher.default) {
        // Given
        val encryptedPath = "output_encrypted_path".toPath()
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (_, getMessageAsset) =
            getSuccessfulFlowArrangement(conversationId = someConversationId, messageId = someMessageId, encryptedPath = encryptedPath)
                .withFetchDecodedAsset(Either.Right(encryptedPath))
                .arrange()

        // When
        val result = getMessageAsset(someConversationId, someMessageId).await()

        // Then
        assertTrue(result is MessageAssetResult.Success)
        assertEquals(result.decodedAssetPath, encryptedPath)
    }

    @Test
    fun givenACallToGetAMessageAsset_whenThereIsAMessageRepositoryError_thenShouldReturnAFailureResult() = runTest(testDispatcher.default) {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (_, getMessageAsset) = Arrangement()
            .withGetMessageErrorResponse()
            .arrange()

        // When
        val result = getMessageAsset(someConversationId, someMessageId).await()

        // Then
        assertTrue(result is MessageAssetResult.Failure)
        assertFalse(result.isRetryNeeded)

    }

    @Test
    fun givenACallToGetAMessageAsset_whenThereIsNoInternetConnection_thenShouldReturnAFailureResultWithRetryEnabled() =
        runTest(testDispatcher.default) {
            // Given
            val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
            val someMessageId = "some-message-id"
            val connectionFailure = NetworkFailure.NoNetworkConnection(null)
            val (arrangement, getMessageAsset) = Arrangement()
                .withDownloadAssetErrorResponse(connectionFailure)
                .withSuccessfulDownloadStatusUpdate()
                .arrange()

            // When
            val result = getMessageAsset(someConversationId, someMessageId).await()

            // Then
            assertTrue(result is MessageAssetResult.Failure)
            assertEquals(result.coreFailure::class, connectionFailure::class)
            assertEquals(true, result.isRetryNeeded)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.updateAssetMessageTransferStatus.invoke(
                    AssetTransferStatus.FAILED_DOWNLOAD,
                    someConversationId,
                    someMessageId
                )
            }
        }

    @Test
    fun givenACallToGetAMessageAsset_whenAssetNotFound_thenShouldReturnAFailureResultWithRetryDisabled() =
        runTest(testDispatcher.default) {
            // Given
            val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
            val someMessageId = "some-message-id"
            val notFoundFailure = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    GenericAPIErrorResponse(
                        404,
                        "asset not found",
                        "not-found"
                    )
                )
            )
            val (arrangement, getMessageAsset) = Arrangement()
                .withDownloadAssetErrorResponse(notFoundFailure)
                .withSuccessfulDownloadStatusUpdate()
                .withSuccessfulDeleteUserAsset()
                .arrange()

            // When
            val result = getMessageAsset(someConversationId, someMessageId).await()

            // Then
            assertTrue(result is MessageAssetResult.Failure)
            assertEquals(result.coreFailure::class, notFoundFailure::class)
            assertEquals(false, result.isRetryNeeded)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.updateAssetMessageTransferStatus.invoke(
                    AssetTransferStatus.NOT_FOUND,
                    someConversationId,
                    someMessageId
                )
            }

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userRepository.removeUserBrokenAsset(any())
            }
        }

    @Test
    fun givenACallToGetAMessageAsset_whenAssetReturnsFederationError_thenShouldReturnAFailureResultWithRetryDisabled() =
        runTest(testDispatcher.default) {
            // Given
            val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
            val someMessageId = "some-message-id"
            val federatedBackendFailure = NetworkFailure.FederatedBackendFailure.General("error")
            val (arrangement, getMessageAsset) = Arrangement()
                .withDownloadAssetErrorResponse(federatedBackendFailure)
                .withSuccessfulDownloadStatusUpdate()
                .withSuccessfulDeleteUserAsset()
                .arrange()

            // When
            val result = getMessageAsset(someConversationId, someMessageId).await()

            // Then
            assertTrue(result is MessageAssetResult.Failure)
            assertEquals(result.coreFailure::class, federatedBackendFailure::class)
            assertEquals(false, result.isRetryNeeded)

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.updateAssetMessageTransferStatus.invoke(
                    AssetTransferStatus.FAILED_DOWNLOAD,
                    someConversationId,
                    someMessageId
                )
            }

            verifySuspend(VerifyMode.not) {
                arrangement.userRepository.removeUserBrokenAsset(any())
            }
        }

    @Test
    fun givenAssetNotDownloadedButAlreadyUploaded_whenGettingAsset_thenFetchAssetAndDownloadIfNeeded() = runTest(testDispatcher.default) {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (arrangement, getMessageAsset) = getSuccessfulFlowArrangement(someConversationId, someMessageId)
            .withFetchDecodedAsset(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        // When
        getMessageAsset(someConversationId, someMessageId).await()

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.assetDataSource.fetchPrivateDecodedAsset(
                arrangement.mockedImageContent.remoteData.assetId,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                true
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.updateAssetMessageTransferStatus.invoke(
                AssetTransferStatus.SAVED_INTERNALLY,
                someConversationId,
                someMessageId
            )
        }
    }

    @Test
    fun givenAssetStoredButUploadFailed_whenGettingAsset_thenFetchAssetFromLocalWithoutDownloading() = runTest(testDispatcher.default) {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (arrangement, getMessageAsset) = getSuccessfulFlowArrangement(someConversationId, someMessageId)
            .withFetchDecodedAsset(Either.Right("local/path".toPath()))
            .arrange()

        // When
        getMessageAsset(someConversationId, someMessageId).await()

        // Then
        verifySuspend(VerifyMode.not) {
            arrangement.assetDataSource.fetchPrivateDecodedAsset(
                arrangement.mockedImageContent.remoteData.assetId,
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }
    }

    private fun testBuildingAudioNormalizedLoudness(
        metadata: AssetContent.AssetMetadata?,
        justDownloaded: Boolean,
        shouldScheduleBuilding: Boolean
    ) = runTest(testDispatcher.default) {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (arrangement, getMessageAsset) =
            getSuccessfulFlowArrangement(someConversationId, someMessageId, assetMetadata = metadata, justDownloaded = justDownloaded)
                .withFetchDecodedAsset(Either.Left(StorageFailure.DataNotFound))
                .arrange()
        // When
        getMessageAsset(someConversationId, someMessageId).await()
        // Then
        assertEquals(if (shouldScheduleBuilding) 1 else 0, arrangement.audioNormalizedLoudnessSchedulerCount)
    }

    @Test
    fun givenNonAudioAsset_whenGetting_thenDoNotScheduleBuildingNormalizedLoudness() = testBuildingAudioNormalizedLoudness(
        metadata = AssetContent.AssetMetadata.Image(width = 100, height = 100),
        justDownloaded = true,
        shouldScheduleBuilding = false
    )

    @Test
    fun givenNewlyDownloadedAudioAssetWithNormalizedLoudness_whenGetting_thenDoNotScheduleBuildingNormalizedLoudness() =
        testBuildingAudioNormalizedLoudness(
            metadata = AssetContent.AssetMetadata.Audio(durationMs = 3000, normalizedLoudness = byteArrayOf(1, 2, 3)),
            justDownloaded = true,
            shouldScheduleBuilding = false
        )

    @Test
    fun givenNewlyDownloadedAudioAssetWithoutNormalizedLoudness_whenGetting_thenScheduleBuildingNormalizedLoudness() =
        testBuildingAudioNormalizedLoudness(
            metadata = AssetContent.AssetMetadata.Audio(durationMs = 3000, normalizedLoudness = null),
            justDownloaded = true,
            shouldScheduleBuilding = true
        )

    @Test
    fun givenAlreadyDownloadedAudioAssetWithoutNormalizedLoudness_whenGetting_thenDoNotScheduleBuildingNormalizedLoudness() =
        testBuildingAudioNormalizedLoudness(
            metadata = AssetContent.AssetMetadata.Audio(durationMs = 3000, normalizedLoudness = null),
            justDownloaded = false,
            shouldScheduleBuilding = false
        )

    private class Arrangement {
        val messageRepository = mock<MessageRepository>(mode = MockMode.autoUnit)
        val userRepository = mock<UserRepository>(mode = MockMode.autoUnit)
        val assetDataSource = mock<AssetRepository>(mode = MockMode.autoUnit)
        val updateAssetMessageTransferStatus = mock<UpdateAssetMessageTransferStatusUseCase>(mode = MockMode.autoUnit)

        private val testScope = TestScope(testDispatcher.default)

        var audioNormalizedLoudnessSchedulerCount = 0
        val audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler = object : AudioNormalizedLoudnessScheduler {
            override fun scheduleBuildingAudioNormalizedLoudness(conversationId: ConversationId, messageId: String) {
                audioNormalizedLoudnessSchedulerCount++
            }
        }

        private lateinit var convId: ConversationId
        private lateinit var msgId: String
        private var encryptionKey = AES256Key(ByteArray(1))
        private var metadata: AssetContent.AssetMetadata? = AssetContent.AssetMetadata.Image(width = 100, height = 100)

        val userId = UserId("some-user", "some-domain.com")
        val clientId = ClientId("some-client-id")
        val someAssetId = "some-asset-id"
        val someAssetToken = "==some-asset-token"

        val mockedImageContent by lazy {
            AssetContent(
                sizeInBytes = 1000,
                name = "some_asset.jpg",
                mimeType = when (metadata) {
                    is AssetContent.AssetMetadata.Image -> "image/png"
                    is AssetContent.AssetMetadata.Video -> "video/mp4"
                    is AssetContent.AssetMetadata.Audio -> "audio/mpeg"
                    else -> "application/octet-stream"
                },
                metadata = metadata,
                remoteData = AssetContent.RemoteData(
                    otrKey = encryptionKey.data,
                    sha256 = ByteArray(16),
                    assetId = someAssetId,
                    assetToken = someAssetToken,
                    assetDomain = "some-asset-domain.com",
                    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM
                )
            )
        }
        val mockedImageMessage by lazy {
            Message.Regular(
                id = msgId,
                content = MessageContent.Asset(mockedImageContent),
                conversationId = convId,
                date = Instant.UNIX_FIRST_DATE,
                senderUserId = userId,
                senderClientId = clientId,
                status = Message.Status.Sent,
                editStatus = Message.EditStatus.NotEdited,
                isSelfMessage = false
            )
        }

        val getMessageAssetUseCase = GetMessageAssetUseCaseImpl(
            assetRepository = assetDataSource,
            messageRepository = messageRepository,
            userRepository = userRepository,
            updateAssetMessageTransferStatus = updateAssetMessageTransferStatus,
            audioNormalizedLoudnessScheduler = audioNormalizedLoudnessScheduler,
            scope = testScope,
            dispatcher = testDispatcher
        )

        suspend fun withSuccessfulFlow(
            conversationId: ConversationId,
            messageId: String,
            encodedPath: Path,
            secretKey: AES256Key,
            assetMetadata: AssetContent.AssetMetadata?,
            justDownloaded: Boolean = true,
        ): Arrangement {
            convId = conversationId
            msgId = messageId
            encryptionKey = secretKey
            metadata = assetMetadata
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Right(mockedImageMessage.copy(content = MessageContent.Asset(mockedImageContent)))
            everySuspend {
                assetDataSource.fetchPrivateDecodedAsset(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                    matches { it.data.contentEquals(secretKey.data) },
                    any(),
                    any()
                )
            } returns Either.Right(FetchedAssetData(encodedPath, justDownloaded))
            everySuspend {
                updateAssetMessageTransferStatus.invoke(any(), conversationId, messageId)
            } returns UpdateTransferStatusResult.Success
            return this
        }

        suspend fun withSuccessfulDownloadStatusUpdate(): Arrangement = apply {
            everySuspend {
                updateAssetMessageTransferStatus.invoke(any(), any(), any())
            } returns UpdateTransferStatusResult.Success
        }

        suspend fun withSuccessfulDeleteUserAsset(): Arrangement = apply {
            everySuspend {
                userRepository.removeUserBrokenAsset(any())
            } returns Either.Right(Unit)
        }

        suspend fun withGetMessageErrorResponse(): Arrangement {
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Left(StorageFailure.DataNotFound)
            return this
        }

        suspend fun withDownloadAssetErrorResponse(noNetworkConnection: NetworkFailure): Arrangement {
            convId = ConversationId("", "")
            encryptionKey = AES256Key(ByteArray(0))
            msgId = ""
            everySuspend {
                messageRepository.getMessageById(any(), any())
            } returns Either.Right(mockedImageMessage)
            everySuspend {
                assetDataSource.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
            } returns Either.Left(noNetworkConnection)
            everySuspend {
                assetDataSource.fetchDecodedAsset(any())
            } returns Either.Left(StorageFailure.DataNotFound)
            return this
        }

        suspend fun withFetchDecodedAsset(result: Either<CoreFailure, Path>) = apply {
            everySuspend {
                assetDataSource.fetchDecodedAsset(any())
            } returns result
        }

        suspend fun arrange() = this to getMessageAssetUseCase.also {
            everySuspend {
                updateAssetMessageTransferStatus.invoke(any(), any(), any())
            } returns UpdateTransferStatusResult.Success
        }
    }

    private fun createFileSystem(): Pair<FakeFileSystem, Path> {
        val userHome = "/Users/me".toPath()
        val fileSystem = FakeFileSystem()
        fileSystem.createDirectories(userHome)
        return fileSystem to userHome
    }

    private fun copyDataToDummyPath(expectedDecodedAsset: ByteArray, rootPath: Path, fileSystem: FakeFileSystem): Path {
        val inputPath = "$rootPath/test-text.txt".toPath()
        fileSystem.write(inputPath) {
            write(expectedDecodedAsset)
        }
        return inputPath
    }

    private companion object {
        val testDispatcher = TestKaliumDispatcher
    }
}
