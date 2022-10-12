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
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
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
    fun givenACallToGetAMessageAsset_whenEverythingGoesWell_thenShouldReturnTheAssetDecodedDataPath() = runTest {
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
        val result = getMessageAsset(someConversationId, someMessageId)
        advanceUntilIdle()

        // Then
        assertTrue(result is MessageAssetResult.Success)
        assertEquals(result.decodedAssetPath, encryptedPath)
    }

    @Test
    fun givenACallToGetAMessageAsset_whenThereIsAMessageRepositoryError_thenShouldReturnAFailureResult() = runTest {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (_, getMessageAsset) = Arrangement()
            .withGetMessageErrorResponse()
            .arrange()

        // When
        val result = getMessageAsset(someConversationId, someMessageId)
        advanceUntilIdle()

        // Then
        assertTrue(result is MessageAssetResult.Failure)
    }

    @Test
    fun givenACallToGetAMessageAsset_whenThereIsNoInternetConnection_thenShouldReturnAFailureResult() = runTest {
        // Given
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val connectionFailure = NetworkFailure.NoNetworkConnection(null)
        val (_, getMessageAsset) = Arrangement()
            .withDownloadAssetErrorResponse(connectionFailure)
            .withSuccessfulDownloadStatusUpdate()
            .arrange()

        // When
        val result = getMessageAsset(someConversationId, someMessageId)
        advanceUntilIdle()

        // Then
        assertTrue(result is MessageAssetResult.Failure)
        assertEquals(result.coreFailure::class, connectionFailure::class)
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val assetDataSource = mock(classOf<AssetRepository>())

        @Mock
        private val updateAssetMessageDownloadStatus = mock(classOf<UpdateAssetMessageDownloadStatusUseCase>())

        private val testScope = TestScope()

        private val testDispatcher = TestKaliumDispatcher

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
                .whenInvokedWith(anything(), anything(), anything(), matching { it.data.contentEquals(secretKey.data) })
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
                .whenInvokedWith(any(), any(), any(), anything())
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
}
