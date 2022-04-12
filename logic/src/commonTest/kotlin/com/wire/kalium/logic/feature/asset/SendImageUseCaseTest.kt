package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.core.toByteArray
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SendImageUseCaseTest {

    @Test
    fun givenAValidSendImageMessageRequest_whenSendingImageMessage_thenShouldReturnASuccessResult() = runTest {
        // Given
        val imageToSend = getMockedImage()
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val (_, sendImageUseCase) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        // When
        val result = sendImageUseCase.invoke(conversationId, imageToSend, 1, 1)

        // Then
        assertEquals(result, SendImageMessageResult.Success)
    }

    private fun getMockedImage(): ByteArray = "some_image".toByteArray()

    @Test
    fun givenAValidSendImageMessageRequest_whenThereIsAnAssetUploadError_thenShouldCallReturnsAFailureResult() = runTest {
        // Given
        val imageByteArray = getMockedImage()
        val conversationId = ConversationId("some-convo-id", "some-domain-id")
        val unauthorizedException = TestNetworkException.missingAuth
        val (_, sendImageUseCase) = Arrangement()
            .withUploadAssetErrorResponse(unauthorizedException)
            .arrange()

        // When
        val result = sendImageUseCase.invoke(conversationId, imageByteArray, 1, 1)

        // Then
        assertTrue(result is SendImageMessageResult.Failure)
        val exception = result.coreFailure
        assertTrue(exception is NetworkFailure.ServerMiscommunication)
        assertEquals(exception.rootCause, unauthorizedException)
    }

    @Test
    fun givenASuccessfulSendImageMessageRequest_whenCheckingTheMessageRepository_thenTheAssetIsPersisted() =
        runTest {
            // Given
            val mockedImg = getMockedImage()
            val conversationId = ConversationId("some-convo-id", "some-domain-id")
            val (arrangement, sendImageUseCase) = Arrangement()
                .withSuccessfulResponse()
                .arrange()

            // When
            sendImageUseCase.invoke(conversationId, mockedImg, 1, 1)

            // Then
            verify(arrangement.messageRepository)
                .suspendFunction(arrangement.messageRepository::persistMessage)
                .with(any())
                .wasInvoked(exactly = once)
        }
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
        "some_key",
        "some_key"
    )

    val sendImageUseCase = SendImageMessageUseCaseImpl(messageRepository, clientRepository, assetDataSource, userRepository, messageSender)

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

    fun arrange() = this to sendImageUseCase
}
