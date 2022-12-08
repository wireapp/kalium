package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveFailedDecryptedMessagesUseCaseTest {

    @Test
    fun givenAConversationId_whenMarkingMessagesAsResolvedDecryption_thenShouldReturnASuccessResult() = runTest {
        // Given
        val (arrangement, resolveFailedDecryptedMessages) = Arrangement()
            .withMarkMessagesResult(Either.Right(Unit))
            .arrange()

        // When
        val result = resolveFailedDecryptedMessages(TestConversation.ID)

        // Then
        assertTrue(result is ResolveDecryptedErrorResult.Success)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessagesAsDecryptionResolved)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenAConversationId_whenMarkingMessagesAsResolvedDecryptionFails_thenShouldReturnAFailureResult() = runTest {
        // Given
        val (arrangement, resolveFailedDecryptedMessages) = Arrangement()
            .withMarkMessagesResult(Either.Left(CoreFailure.Unknown(RuntimeException("Some error :'( "))))
            .arrange()

        // When
        val result = resolveFailedDecryptedMessages(TestConversation.ID)

        // Then
        assertTrue(result is ResolveDecryptedErrorResult.Failure)
        verify(arrangement.messageRepository)
            .suspendFunction(arrangement.messageRepository::markMessagesAsDecryptionResolved)
            .with(any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val messageRepository = mock(classOf<MessageRepository>())

        fun withMarkMessagesResult(result: Either<CoreFailure, Unit>): Arrangement {
            given(messageRepository)
                .suspendFunction(messageRepository::markMessagesAsDecryptionResolved)
                .whenInvokedWith(any())
                .thenReturn(result)
            return this
        }

        fun arrange() = this to ResolveFailedDecryptedMessagesUseCaseImpl(messageRepository)
    }

}
