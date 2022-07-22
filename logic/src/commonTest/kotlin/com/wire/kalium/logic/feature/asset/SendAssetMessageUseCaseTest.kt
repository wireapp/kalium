package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestAsset.dummyUploadedAssetId
import com.wire.kalium.logic.framework.TestAsset.mockedLongAssetData
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendAssetMessageUseCaseTest {

    @Test
    fun givenAValidSendAssetMessageRequest_whenSendingAssetMessage_thenShouldReturnASuccessResult() = runTest {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val outputEncryptedPath = fakeKaliumFileSystem.providePersistentAssetPath("output-encrypted-file.aes")
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendAssetUseCase) = Arrangement()
            .withStoredData(assetToSend, inputDataPath)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .arrange()

        // When
        val result =
            sendAssetUseCase.invoke(conversationId, inputDataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)

        // Then
        assertTrue(result is SendAssetMessageResult.Success)
    }

    @Test
    fun givenAValidSendAssetMessageRequest_whenThereIsAnAssetUploadError_thenShouldCallReturnsAFailureResult() = runTest {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val unauthorizedException = TestNetworkException.missingAuth
        val (_, sendAssetUseCase) = Arrangement()
            .withUploadAssetErrorResponse(unauthorizedException)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)

        // Then
        assertTrue(result is SendAssetMessageResult.Failure)
        val exception = result.coreFailure
        assertTrue(exception is NetworkFailure.ServerMiscommunication)
        assertEquals(exception.rootCause, unauthorizedException)
    }

    @Test
    fun givenASuccessfulSendAssetMessageRequest_whenSendingTheAsset_thenTheAssetIsPersisted() = runTest {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val (arrangement, sendAssetUseCase) = Arrangement()
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)

        // Then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendPendingMessage)
            .with(eq(conversationId), any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASuccessfulSendAssetMessageRequest_whenCheckingTheMessageRepository_thenTheAssetIsMarkedAsSavedInternally() =
        runTest {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val expectedAssetId = dummyUploadedAssetId
            val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
            val (arrangement, sendAssetUseCase) = Arrangement()
                .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
                .arrange()

            // When
            sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)

            // Then
            verify(arrangement.persistMessage)
                .suspendFunction(arrangement.persistMessage::invoke)
                .with(matching {
                    val content = it.content
                    content is MessageContent.Asset && content.value.downloadStatus == Message.DownloadStatus.SAVED_INTERNALLY
                })
                .wasInvoked(exactly = once)
        }

    private class Arrangement {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        private val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        private val assetDataSource = mock(classOf<AssetRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        val someClientId = ClientId("some-client-id")

        private fun fakeSelfUser() = SelfUser(
            UserId("some_id", "some_domain"),
            "some_name",
            "some_handle",
            "some_email",
            null,
            1,
            null,
            ConnectionState.ACCEPTED,
            previewPicture = UserAssetId("value1", "domain"),
            completePicture = UserAssetId("value2", "domain"),
            UserAvailabilityStatus.NONE
        )

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
            return this
        }

        fun withSuccessfulResponse(expectedAssetId: UploadedAssetId, assetSHA256Key: SHA256Key): Arrangement {
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(expectedAssetId to assetSHA256Key))
            given(userRepository)
                .suspendFunction(userRepository::observeSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(fakeSelfUser()))
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(messageSender)
                .suspendFunction(messageSender::sendPendingMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withUploadAssetErrorResponse(exception: KaliumException): Arrangement {
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to SendAssetMessageUseCaseImpl(
            persistMessage,
            clientRepository,
            assetDataSource,
            userRepository,
            messageSender
        )
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
    }
}
