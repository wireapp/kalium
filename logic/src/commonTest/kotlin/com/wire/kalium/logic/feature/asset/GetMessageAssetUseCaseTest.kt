package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.AES256Key
import com.wire.kalium.cryptography.utils.PlainData
import com.wire.kalium.cryptography.utils.encryptDataWithAES256
import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val fakeFileSystem = FakeFileSystem()
        val encryptedPath = "output_encrypted_path".toPath()
        val encodedSuccessfully = encryptDataWithAES256(PlainData(expectedDecodedAsset), randomAES256Key, encryptedPath, fakeFileSystem)
        val someConversationId = ConversationId("some-conversation-id", "some-domain.com")
        val someMessageId = "some-message-id"
        val (_, getMessageAsset) = Arrangement()
            .withSuccessfulFlow(someConversationId, someMessageId, encryptedPath, randomAES256Key)
            .arrange()

        // When
        val result = getMessageAsset(someConversationId, someMessageId)

        // Then
        assertTrue(result is MessageAssetResult.Success)
        assertTrue(encodedSuccessfully)
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
            .arrange()

        // When
        val result = getMessageAsset(someConversationId, someMessageId)

        // Then
        assertTrue(result is MessageAssetResult.Failure)
        assertEquals(result.coreFailure::class, connectionFailure::class)
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val assetDataSource = mock(classOf<AssetRepository>())

        private lateinit var convId: ConversationId
        private lateinit var msgId: String
        private lateinit var encryptionKey: ByteArray

        val userId = UserId("some-user", "some-domain.com")
        val clientId = ClientId("some-client-id")
        val someAssetId = "some-asset-id"
        val someAssetToken = "==some-asset-token"

        private val mockedMessage by lazy {
            Message.Regular(
                id = msgId,
                content = MessageContent.Asset(
                    AssetContent(
                        sizeInBytes = 1000,
                        name = "some_asset.jpg",
                        mimeType = "image/jpeg",
                        metadata = AssetContent.AssetMetadata.Image(width = 100, height = 100),
                        remoteData = AssetContent.RemoteData(
                            otrKey = encryptionKey,
                            sha256 = ByteArray(16),
                            assetId = someAssetId,
                            assetToken = someAssetToken,
                            assetDomain = "some-asset-domain.com",
                            encryptionAlgorithm = AssetContent.RemoteData.EncryptionAlgorithm.AES_GCM
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

        val getMessageAssetUseCase = GetMessageAssetUseCaseImpl(assetDataSource, messageRepository)

        fun withSuccessfulFlow(
            conversationId: ConversationId,
            messageId: String,
            encodedPath: Path,
            secretKey: AES256Key
        ): Arrangement {
            convId = conversationId
            msgId = messageId
            encryptionKey = secretKey.data
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(mockedMessage))
            given(assetDataSource)
                .suspendFunction(assetDataSource::fetchPrivateDecodedAsset)
                .whenInvokedWith(eq(secretKey), any(), any())
                .thenReturn(Either.Right(encodedPath))
            return this
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
            encryptionKey = ByteArray(0)
            msgId = ""
            given(messageRepository)
                .suspendFunction(messageRepository::getMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(mockedMessage))
            given(assetDataSource)
                .suspendFunction(assetDataSource::fetchPrivateDecodedAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Left(noNetworkConnection))
            return this
        }

        fun arrange() = this to getMessageAssetUseCase
    }
}
