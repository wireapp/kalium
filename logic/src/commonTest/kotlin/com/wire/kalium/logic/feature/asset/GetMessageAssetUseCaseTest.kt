/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GetMessageAssetUseCaseTest {

    @Test
    fun givenACallToGetAMessageAsset_whenEverythingGoesWell_thenShouldReturnTheAssetDecodedDataPath() = runTest(testDispatcher.default) {
        // Given
        val expectedDecodedAsset = byteArrayOf(14, 2, 10, 63, -2, -1, 34, 0, 12, 4, 5, 6, 8, 9, -22, 9, 63)
        val randomAES256Key = generateRandomAES256Key()
        val (fakeFileSystem, rootPath) = createFileSystem()
        val encryptedPath = "output_encrypted_path".toPath()
        val rawDataPath = copyDataToDummyPath(expectedDecodedAsset, rootPath, fakeFileSystem)
        val rawDataSource = fakeFileSystem.source(rawDataPath)
        val encryptedDataSink = fakeFileSystem.sink(encryptedPath)
        encryptFileWithAES256(rawDataSource, randomAES256Key, encryptedDataSink)

        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (_, getMessageAsset) = Arrangement()
            .withSuccessfulFlow(someConversationId, someMessageId, encryptedPath, randomAES256Key)
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
    }

    @Test
    fun givenACallToGetAMessageAsset_whenThereIsNoInternetConnection_thenShouldReturnAFailureResult() = runTest(testDispatcher.default) {
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
        verify(arrangement.updateAssetMessageDownloadStatus)
            .suspendFunction(arrangement.updateAssetMessageDownloadStatus::invoke)
            .with(matching { it == Message.DownloadStatus.FAILED_DOWNLOAD }, eq(someConversationId), eq(someMessageId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val assetDataSource = mock(classOf<AssetRepository>())

        @Mock
        val updateAssetMessageDownloadStatus = mock(classOf<UpdateAssetMessageDownloadStatusUseCase>())

        private val testScope = TestScope(testDispatcher.default)

        private lateinit var convId: ConversationId
        private lateinit var msgId: String
        private var encryptionKey = AES256Key(ByteArray(1))

        val userId = UserId("some-user", "some-domain.com")
        val clientId = ClientId("some-client-id")
        val someAssetId = "some-asset-id"
        val someAssetToken = "==some-asset-token"

        private val mockedImageMessage by lazy {
            Message.Regular(
                id = msgId,
                content = MessageContent.Asset(
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
                        ),
                        downloadStatus = Message.DownloadStatus.NOT_DOWNLOADED
                    )
                ),
                conversationId = convId,
                date = "22-03-2022",
                senderUserId = userId,
                senderClientId = clientId,
                status = Message.Status.SENT,
                editStatus = Message.EditStatus.NotEdited
            )
        }

        val getMessageAssetUseCase =
            GetMessageAssetUseCaseImpl(assetDataSource, messageRepository, updateAssetMessageDownloadStatus, testScope, testDispatcher)

        fun withSuccessfulFlow(
            conversationId: ConversationId,
            messageId: String,
            encodedPath: Path,
            secretKey: AES256Key
        ): Arrangement {
            convId = conversationId
            msgId = messageId
            encryptionKey = secretKey
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(mockedImageMessage))
            given(assetDataSource)
                .suspendFunction(assetDataSource::fetchPrivateDecodedAsset)
                .whenInvokedWith(
                    anything(),
                    anything(),
                    anything(),
                    anything(),
                    matching { it.data.contentEquals(secretKey.data) },
                    anything()
                )
                .thenReturn(Either.Right(encodedPath))
            given(updateAssetMessageDownloadStatus)
                .suspendFunction(updateAssetMessageDownloadStatus::invoke)
                .whenInvokedWith(anything(), matching { it == conversationId }, matching { it == messageId })
                .thenReturn(UpdateDownloadStatusResult.Success)
            return this
        }

        fun withSuccessfulDownloadStatusUpdate(): Arrangement = apply {
            given(updateAssetMessageDownloadStatus)
                .suspendFunction(updateAssetMessageDownloadStatus::invoke)
                .whenInvokedWith(anything(), anything(), anything())
                .thenReturn(UpdateDownloadStatusResult.Success)
        }

        fun withGetMessageErrorResponse(): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
            return this
        }

        fun withDownloadAssetErrorResponse(noNetworkConnection: NetworkFailure.NoNetworkConnection): Arrangement {
            convId = ConversationId("", "")
            encryptionKey = AES256Key(ByteArray(0))
            msgId = ""
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(mockedImageMessage))
            given(assetDataSource)
                .suspendFunction(assetDataSource::fetchPrivateDecodedAsset)
                .whenInvokedWith(any(), any(), any(), anything(), any(), any())
                .thenReturn(Either.Left(noNetworkConnection))
            return this
        }

        fun arrange() = this to getMessageAssetUseCase
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
