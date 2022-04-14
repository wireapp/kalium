package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.core.*
import io.mockative.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendAssetMessageUseCaseTest {

    @Test
    fun givenAValidSendAssetMessageRequest_whenSendingAssetMessage_thenShouldReturnASuccessResult() = runTest {
        // Given
        val assetToSend = getMockedAsset()
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendAssetUseCase) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(conversationId, assetToSend, "temp_asset.txt", "text/plain")

        // Then
        assertEquals(result, SendAssetMessageResult.Success)
    }

    @Test
    fun givenAValidSendImageMessageRequest_whenThereIsAnAssetUploadError_thenShouldCallReturnsAFailureResult() = runTest {
        // Given
        val assetToSend = getMockedAsset()
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val unauthorizedException = TestNetworkException.missingAuth
        val (_, sendAssetUseCase) = Arrangement()
            .withUploadAssetErrorResponse(unauthorizedException)
            .arrange()

        // When
        val result = sendAssetUseCase.invoke(conversationId, assetToSend, "temp_asset.txt", "text/plain")

        // Then
        assertTrue(result is SendAssetMessageResult.Failure)
        val exception = result.coreFailure
        assertTrue(exception is NetworkFailure.ServerMiscommunication)
        assertEquals(exception.rootCause, unauthorizedException)
    }

    @Test
    fun givenASuccessfulSendImageMessageRequest_whenCheckingTheMessageRepository_thenTheAssetIsPersisted() =
        runTest {
            // Given
            val assetToSend = getMockedAsset()
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val (arrangement, sendAssetUseCase) = Arrangement()
                .withSuccessfulResponse()
                .arrange()

            // When
            val result = sendAssetUseCase.invoke(conversationId, assetToSend, "temp_asset.txt", "text/plain")

            // Then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistMessage)
                .with(any())
                .wasInvoked(exactly = once)
        }

    private class Arrangement {

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        @Mock
        private val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        private val assetDataSource = mock(classOf<AssetRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        @Mock
        private val messageSender = mock(classOf<MessageSender>())

        val someAssetId = UploadedAssetId("some-asset-id", "some-asset-token")

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
            "some_key",
            "some_key"
        )

        val sendAssetUseCase =
            SendAssetMessageUseCaseImpl(messageRepository, clientRepository, assetDataSource, userRepository, messageSender)

        fun withSuccessfulResponse(): Arrangement {
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(someAssetId))
            given(userRepository)
                .suspendFunction(userRepository::getSelfUser)
                .whenInvoked()
                .thenReturn(flowOf(fakeSelfUser()))
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .thenReturn(Either.Right(someClientId))
            given(messageRepository)
                .suspendFunction(messageRepository::persistMessage)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(messageSender)
                .suspendFunction(messageSender::trySendingOutgoingMessageById)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withUploadAssetErrorResponse(exception: KaliumException): Arrangement {
            given(assetDataSource)
                .suspendFunction(assetDataSource::uploadAndPersistPrivateAsset)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to sendAssetUseCase
    }

    private fun getMockedAsset(): ByteArray =
        "some VERY long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long long asset".toByteArray()
}
