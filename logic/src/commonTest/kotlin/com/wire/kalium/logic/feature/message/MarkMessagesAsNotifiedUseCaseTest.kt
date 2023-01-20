package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.feature.message.MarkMessagesAsNotifiedUseCase.UpdateTarget
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MarkMessagesAsNotifiedUseCaseTest {

    @Test
    fun givenMarkIsCalledForAllConversations_whenInvokingTheUseCase_thenAllConversationsAreMarkedAsNotified() = runTest {
        val (arrangement, markMessagesAsNotified) = Arrangement()
            .withUpdatingAllConversationsReturning(Either.Right(Unit))
            .withLastInstantFromOthersReturning(Either.Right(TEST_INSTANT))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.AllConversations)

        verify(arrangement.conversationRepository)
            .coroutine { updateAllConversationsNotificationDate(TEST_INSTANT) }
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(anything(), anything())
            .wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenMarkIsCalledWithSpecificConversationId_whenInvokingTheUseCase_thenSpecificConversationIsMarkedAsNotified() = runTest {
        val (arrangement, markMessagesAsNotified) = Arrangement()
            .withUpdatingOneConversationReturning(Either.Right(Unit))
            .withLastInstantFromOthersReturning(Either.Right(TEST_INSTANT))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.SingleConversation(CONVERSATION_ID))

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateConversationNotificationDate)
            .with(eq(CONVERSATION_ID), anything())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateAllConversationsNotificationDate)
            .with(anything())
            .wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenUpdatingOneConversationFails_whenInvokingTheUseCase_thenFailureIsPropagated() = runTest {
        val failure = StorageFailure.DataNotFound

        val (_, markMessagesAsNotified) = Arrangement()
            .withUpdatingOneConversationReturning(Either.Left(failure))
            .withLastInstantFromOthersReturning(Either.Right(TEST_INSTANT))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.SingleConversation(CONVERSATION_ID))

        assertEquals(result, Result.Failure(failure))
    }

    @Test
    fun givenUpdatingAllConversationsFails_whenInvokingTheUseCase_thenFailureIsPropagated() = runTest {
        val failure = StorageFailure.DataNotFound

        val (_, markMessagesAsNotified) = Arrangement()
            .withUpdatingAllConversationsReturning(Either.Left(failure))
            .withLastInstantFromOthersReturning(Either.Right(TEST_INSTANT))
            .arrange()

        val result = markMessagesAsNotified(UpdateTarget.AllConversations)

        assertEquals(result, Result.Failure(failure))
    }

    private class Arrangement {

        @Mock
        val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val messageRepository: MessageRepository = mock(classOf<MessageRepository>())

        fun withUpdatingAllConversationsReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateAllConversationsNotificationDate)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUpdatingOneConversationReturning(result: Either<StorageFailure, Unit>) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationNotificationDate)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun withLastInstantFromOthersReturning(result: Either<StorageFailure, Instant>) = apply {
            given(messageRepository)
                .suspendFunction(messageRepository::getInstantOfLatestMessageFromOtherUsers)
                .whenInvoked()
                .thenReturn(result)
        }

        fun arrange() = this to MarkMessagesAsNotifiedUseCase(
            conversationRepository, messageRepository
        )

    }

    companion object {
        private val TEST_INSTANT = Instant.fromEpochMilliseconds(123_456_789L)
        private val CONVERSATION_ID = QualifiedID("some_id", "some_domain")
    }
}
