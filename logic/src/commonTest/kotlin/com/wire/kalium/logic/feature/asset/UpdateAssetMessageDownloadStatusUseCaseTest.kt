package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateAssetMessageDownloadStatusUseCaseTest {

    @Test
    fun givenAValidDownloadStatusUpdateRequest_whenInvoked_thenResultSuccessIsReturned() = runTest {
        // Given
        val newDownloadStatus = Message.DownloadStatus.IN_PROGRESS
        val dummyConvId = ConversationId("dummy-value", "dummy.domain")
        val dummyMessageId = "dummy-message-id"
        val (arrangement, useCase) = Arrangement().withSuccessfulResponse().arrange()

        // When
        val result = useCase.invoke(newDownloadStatus, dummyConvId, dummyMessageId)

        // Then
        assertTrue(result is UpdateDownloadStatusResult.Success)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateAssetMessageDownloadStatus)
            .with(eq(newDownloadStatus), eq(dummyConvId), eq(dummyMessageId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAnErrorDownloadStatusUpdateRequest_whenInvoked_thenCoreFailureIsReturned() = runTest {
        // Given
        val newDownloadStatus = Message.DownloadStatus.DOWNLOADED
        val dummyConvId = ConversationId("dummy-value", "dummy.domain")
        val dummyMessageId = "dummy-message-id"
        val (arrangement, useCase) = Arrangement().withErrorResponse().arrange()

        // When
        val result = useCase.invoke(newDownloadStatus, dummyConvId, dummyMessageId)

        // Then
        assertTrue(result is UpdateDownloadStatusResult.Failure)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::updateAssetMessageDownloadStatus)
            .with(eq(newDownloadStatus), eq(dummyConvId), eq(dummyMessageId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        fun withSuccessfulResponse(): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::updateAssetMessageDownloadStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withErrorResponse(): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::updateAssetMessageDownloadStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(RuntimeException())))
            return this
        }

        fun arrange() = this to UpdateAssetMessageDownloadStatusUseCaseImpl(messageRepository)

    }
}
