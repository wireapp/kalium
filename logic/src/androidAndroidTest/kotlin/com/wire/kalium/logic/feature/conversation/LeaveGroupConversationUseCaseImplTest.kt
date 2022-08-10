package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.anything
import io.mockative.classOf
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class LeaveGroupConversationUseCaseImplTest {

    @Test
    fun givenValidConversationIdAndRemovedByUser_whenInvokingTheUseCase_itUpdatesSuccessfullyTheDB() = runTest {
        // Given
        val mockUserId = UserId("some-user-value", "some-domain")
        val mockConversationId = ConversationId("some-conversation-value", "some-domain")
        val (arrangement, leaveGroupUseCase) = Arrangement()
            .withSuccessfulUpdate()
            .arrange()

        // When
        val result = leaveGroupUseCase.invoke(conversationId = mockConversationId, removedBy = mockUserId)

        // Then
        assertTrue { result is LeaveGroupResult.Success }
        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateRemovedBy)
                .with(matching { it == mockConversationId }, matching { it == mockUserId })
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenValidConversationIdAndNullRemovedByUser_whenInvokingTheUseCase_itUpdatesSuccessfullyTheDB() = runTest {
        // Given
        val mockConversationId = ConversationId("some-conversation-value", "some-domain")
        val (arrangement, leaveGroupUseCase) = Arrangement()
            .withSuccessfulUpdate()
            .arrange()

        // When
        val result = leaveGroupUseCase.invoke(conversationId = mockConversationId, removedBy = null)

        // Then
        assertTrue { result is LeaveGroupResult.Success }
        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateRemovedBy)
                .with(matching { it == mockConversationId }, anything())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenDBErrorRemovedByUserRequest_whenInvokingTheUseCase_itReturnsAnErrorResult() = runTest {
        // Given
        val mockUserId = UserId("some-user-value", "some-domain")
        val mockConversationId = ConversationId("some-conversation-value", "some-domain")
        val exception = RuntimeException("some-error")
        val (arrangement, leaveGroupUseCase) = Arrangement()
            .withErrorUpdate(exception)
            .arrange()

        // When
        val result = leaveGroupUseCase.invoke(conversationId = mockConversationId, removedBy = mockUserId)

        // Then
        assertTrue(result is LeaveGroupResult.NoConversationFound)
        assertTrue(result.coreFailure is StorageFailure.Generic)
        assertEquals((result.coreFailure as StorageFailure.Generic).rootCause, exception)
        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateRemovedBy)
                .with(matching { it == mockConversationId }, matching { it == mockUserId })
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {
        val conversationRepository = mock(classOf<ConversationRepository>())

        fun withSuccessfulUpdate(): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateRemovedBy)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withErrorUpdate(exception: Exception): Arrangement {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateRemovedBy)
                .whenInvokedWith(anything(), anything())
                .thenReturn(Either.Left(StorageFailure.Generic(exception)))
            return this
        }

        fun arrange() = this to LeaveGroupConversationUseCaseImpl(conversationRepository)

    }
}
