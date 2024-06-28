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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
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
    ): Arrangement {
        val expectedDecodedAsset = byteArrayOf(14, 2, 10, 63, -2, -1, 34, 0, 12, 4, 5, 6, 8, 9, -22, 9, 63)
        val randomAES256Key = generateRandomAES256Key()
        val (fakeFileSystem, rootPath) = createFileSystem()
        val rawDataPath = copyDataToDummyPath(expectedDecodedAsset, rootPath, fakeFileSystem)
        val rawDataSource = fakeFileSystem.source(rawDataPath)
        val encryptedDataSink = fakeFileSystem.sink(encryptedPath)
        encryptFileWithAES256(rawDataSource, randomAES256Key, encryptedDataSink)
        return Arrangement()
            .withSuccessfulFlow(conversationId, messageId, encryptedPath, randomAES256Key)
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

            coVerify {
                arrangement.updateAssetMessageTransferStatus.invoke(
                    matches { it == AssetTransferStatus.FAILED_DOWNLOAD },
                    eq(someConversationId),
                    eq(someMessageId)
                )
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenACallToGetAMessageAsset_whenAssetNotFound_thenShouldReturnAFailureResultWithRetryDisabled() =
        runTest(testDispatcher.default) {
            // Given
            val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
            val someMessageId = "some-message-id"
            val notFoundFailure = NetworkFailure.ServerMiscommunication(
                KaliumException.InvalidRequestError(
                    ErrorResponse(
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

            coVerify {
                arrangement.updateAssetMessageTransferStatus.invoke(
                    matches { it == AssetTransferStatus.NOT_FOUND },
                    eq(someConversationId),
                    eq(someMessageId)
                )
            }.wasInvoked(exactly = once)

            coVerify {
                arrangement.userRepository.removeUserBrokenAsset(any())
            }.wasInvoked(once)
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

            coVerify {
                arrangement.updateAssetMessageTransferStatus.invoke(
                    matches { it == AssetTransferStatus.FAILED_DOWNLOAD },
                    eq(someConversationId),
                    eq(someMessageId)
                )
            }.wasInvoked(once)

            coVerify {
                arrangement.userRepository.removeUserBrokenAsset(any())
            }.wasNotInvoked()
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
        coVerify {
            arrangement.assetDataSource.fetchPrivateDecodedAsset(
                eq(arrangement.mockedImageContent.remoteData.assetId),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(true)
            )
        }.wasInvoked(once)
        coVerify {
            arrangement.updateAssetMessageTransferStatus.invoke(
                matches { it == AssetTransferStatus.SAVED_INTERNALLY },
                eq(someConversationId),
                eq(someMessageId)
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.assetDataSource.fetchPrivateDecodedAsset(
                eq(arrangement.mockedImageContent.remoteData.assetId),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        }.wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(MessageRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        @Mock
        val assetDataSource = mock(AssetRepository::class)

        @Mock
        val updateAssetMessageTransferStatus = mock(UpdateAssetMessageTransferStatusUseCase::class)

        private val testScope = TestScope(testDispatcher.default)

        private lateinit var convId: ConversationId
        private lateinit var msgId: String
        private var encryptionKey = AES256Key(ByteArray(1))

        val userId = UserId("some-user", "some-domain.com")
        val clientId = ClientId("some-client-id")
        val someAssetId = "some-asset-id"
        val someAssetToken = "==some-asset-token"

        val mockedImageContent by lazy {
            AssetContent(
                sizeInBytes = 1000,
                name = "some_asset.jpg",
                mimeType = "image/jpeg",
                metadata = AssetContent.AssetMetadata.Image(width = 100, height = 100),
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

        val getMessageAssetUseCase =
            GetMessageAssetUseCaseImpl(
                assetDataSource, messageRepository, userRepository,
                updateAssetMessageTransferStatus, testScope, testDispatcher
            )

        suspend fun withSuccessfulFlow(
            conversationId: ConversationId,
            messageId: String,
            encodedPath: Path,
            secretKey: AES256Key
        ): Arrangement {
            convId = conversationId
            msgId = messageId
            encryptionKey = secretKey
            coEvery {
                messageRepository.getMessageById(any(), any())
            }.returns(Either.Right(mockedImageMessage.copy(content = MessageContent.Asset(mockedImageContent))))
            coEvery {
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
            }.returns(Either.Right(encodedPath))
            coEvery {
                updateAssetMessageTransferStatus.invoke(any(), matches { it == conversationId }, matches { it == messageId })
            }.returns(UpdateTransferStatusResult.Success)
            return this
        }

        suspend fun withSuccessfulDownloadStatusUpdate(): Arrangement = apply {
            coEvery {
                updateAssetMessageTransferStatus.invoke(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Success)
        }

        suspend fun withSuccessfulDeleteUserAsset(): Arrangement = apply {
            coEvery {
                userRepository.removeUserBrokenAsset(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetMessageErrorResponse(): Arrangement {
            coEvery {
                messageRepository.getMessageById(any(), any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
            return this
        }

        suspend fun withDownloadAssetErrorResponse(noNetworkConnection: NetworkFailure): Arrangement {
            convId = ConversationId("", "")
            encryptionKey = AES256Key(ByteArray(0))
            msgId = ""
            coEvery {
                messageRepository.getMessageById(any(), any())
            }.returns(Either.Right(mockedImageMessage))
            coEvery {
                assetDataSource.fetchPrivateDecodedAsset(any(), any(), any(), any(), any(), any(), any(), any())
            }.returns(Either.Left(noNetworkConnection))
            coEvery {
                assetDataSource.fetchDecodedAsset(any())
            }.returns(Either.Left(StorageFailure.DataNotFound))
            return this
        }

        suspend fun withFetchDecodedAsset(result: Either<CoreFailure, Path>) = apply {
            coEvery {
                assetDataSource.fetchDecodedAsset(any())
            }.returns(result)
        }

        suspend fun arrange() = this to getMessageAssetUseCase.also {
            coEvery {
                updateAssetMessageTransferStatus.invoke(any(), any(), any())
            }.returns(UpdateTransferStatusResult.Success)
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
