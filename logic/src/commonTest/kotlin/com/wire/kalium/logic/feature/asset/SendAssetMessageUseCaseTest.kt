package com.wire.kalium.logic.feature.asset

import com.wire.kalium.cryptography.utils.SHA256Key
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.framework.TestAsset.dummyUploadedAssetId
import com.wire.kalium.logic.framework.TestAsset.mockedLongAssetData
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
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
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendAssetMessageUseCaseTest {

    @Test
    fun givenAValidSendAssetMessageRequest_whenSendingAssetMessage_thenShouldReturnASuccessResult() = runTest(testDispatcher.default) {
        // Given
        val assetToSend = mockedLongAssetData()
        val assetName = "some-asset.txt"
        val inputDataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
        val expectedAssetId = dummyUploadedAssetId
        val expectedAssetSha256 = SHA256Key("some-asset-sha-256".toByteArray())
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendAssetUseCase) = Arrangement()
            .withStoredData(assetToSend, inputDataPath)
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(
            conversationId, inputDataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null
        )
        advanceUntilIdle()

        // Then
        assertTrue(result is SendAssetMessageResult.Success)
    }

    @Test
    fun givenAValidSendAssetMessageRequest_whenThereIsAnAssetUploadError_thenShouldCallReturnsAFailureResult() =
        runTest(testDispatcher.default) {
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
            advanceUntilIdle()

            // Then
            assertTrue(result is SendAssetMessageResult.Failure)
            val exception = result.coreFailure
            assertTrue(exception is NetworkFailure.ServerMiscommunication)
            assertEquals(exception.rootCause, unauthorizedException)
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
        val (arrangement, sendAssetUseCase) = Arrangement()
            .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
            .arrange()

        // When
        sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)
        advanceUntilIdle()

        // Then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(any())
            .wasInvoked(exactly = twice)
        verify(arrangement.messageSender)
            .suspendFunction(arrangement.messageSender::sendPendingMessage)
            .with(eq(conversationId), any())
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
            val (arrangement, sendAssetUseCase) = Arrangement()
                .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
                .arrange()

            // When
            sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)
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
    fun givenAnErrorAtInitialMessagePersistCall_whenCheckingTheMessageRepository_thenTheAssetUploadStatusIsMarkedAsFailed() =
        runTest(testDispatcher.default) {
            // Given
            val assetToSend = mockedLongAssetData()
            val assetName = "some-asset.txt"
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val dataPath = fakeKaliumFileSystem.providePersistentAssetPath(assetName)
            val (arrangement, sendAssetUseCase) = Arrangement()
                .withPersistErrorResponse()
                .arrange()

            // When
            sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)
            advanceUntilIdle()

            // Then
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
            val (arrangement, sendAssetUseCase) = Arrangement()
                .withSuccessfulResponse(expectedAssetId, expectedAssetSha256)
                .arrange()

            // When
            sendAssetUseCase.invoke(conversationId, dataPath, assetToSend.size.toLong(), assetName, "text/plain", null, null)
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

    private class Arrangement {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val messageSender = mock(classOf<MessageSender>())

        @Mock
        private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        private val assetDataSource = mock(classOf<AssetRepository>())

        @Mock
        private val slowSyncRepository = mock(classOf<SlowSyncRepository>())

        @Mock
        val updateUploadStatus = mock(classOf<UpdateAssetMessageUploadStatusUseCase>())

        val someClientId = ClientId("some-client-id")

        val completeStateFlow = MutableStateFlow<SlowSyncStatus>(SlowSyncStatus.Complete).asStateFlow()

        private val testScope = TestScope()

        fun withStoredData(data: ByteArray, dataPath: Path): Arrangement {
            fakeKaliumFileSystem.sink(dataPath).buffer().use {
                it.write(data)
                it.flush()
            }
            return this
        }

        fun withSuccessfulResponse(expectedAssetId: UploadedAssetId, assetSHA256Key: SHA256Key): Arrangement = apply {
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
                .suspendFunction(messageSender::sendPendingMessage)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Success)
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
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any(), any(), any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        fun withPersistErrorResponse() = apply {
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
                .thenReturn(Either.Left(StorageFailure.Generic(IOException("Some error"))))
            given(updateUploadStatus)
                .suspendFunction(updateUploadStatus::invoke)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(UpdateUploadStatusResult.Failure(CoreFailure.Unknown(RuntimeException("some error"))))
        }

        fun arrange() = this to SendAssetMessageUseCaseImpl(
            persistMessage,
            updateUploadStatus,
            currentClientIdProvider,
            assetDataSource,
            QualifiedID("some-id", "some-domain"),
            slowSyncRepository,
            messageSender,
            testScope,
            testDispatcher
        )
    }

    companion object {
        val fakeKaliumFileSystem = FakeKaliumFileSystem()
        val testDispatcher = TestKaliumDispatcher
    }
}
