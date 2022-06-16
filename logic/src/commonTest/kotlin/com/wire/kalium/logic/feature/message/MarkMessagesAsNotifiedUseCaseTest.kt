package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MarkMessagesAsNotifiedUseCaseTest {

    @Mock
    val conversationRepository: ConversationRepository = mock(classOf<ConversationRepository>())

    private lateinit var markMessagesAsNotifiedUseCase: MarkMessagesAsNotifiedUseCase

    @BeforeTest
    fun setUp() {
        markMessagesAsNotifiedUseCase = MarkMessagesAsNotifiedUseCaseImpl(conversationRepository)
    }

    @Test
    fun givenMarkIsCalledWithConversationIdNull_whenInvokingTheUseCase_thenAllConversationsAreMarkedAsNotified() = runTest {
        given(conversationRepository).coroutine { updateAllConversationsNotificationDate(DATE) }.then { Either.Right(Unit) }

        val result = markMessagesAsNotifiedUseCase(null, DATE)

        verify(conversationRepository)
            .coroutine { updateAllConversationsNotificationDate(DATE) }
            .wasInvoked(exactly = once)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationNotificationDate)
            .with(anything(), anything())
            .wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenMarkIsCalledWithSomeConversationId_whenInvokingTheUseCase_thenSpecificConversationIsMarkedAsNotified() = runTest {
        given(conversationRepository).coroutine { updateAllConversationsNotificationDate(DATE) }.then { Either.Right(Unit) }
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationNotificationDate)
            .whenInvokedWith(eq(CONVERSATION_ID), anything())
            .then { _, _ -> Either.Right(Unit) }

        val result = markMessagesAsNotifiedUseCase(CONVERSATION_ID, DATE)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationNotificationDate)
            .with(eq(CONVERSATION_ID), anything())
            .wasInvoked(exactly = once)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::updateAllConversationsNotificationDate)
            .with(anything())
            .wasNotInvoked()

        assertEquals(result, Result.Success)
    }

    @Test
    fun givenMarkingPropagateError_whenInvokingTheUseCase_thenFailureIsReturned() = runTest {
        val failure = StorageFailure.DataNotFound
        given(conversationRepository).coroutine { updateAllConversationsNotificationDate(DATE) }.then { Either.Right(Unit) }
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationNotificationDate)
            .whenInvokedWith(eq(CONVERSATION_ID), anything())
            .then { _, _ -> Either.Left(failure) }

        val result = markMessagesAsNotifiedUseCase(CONVERSATION_ID, DATE)

        assertEquals(result, Result.Failure(failure))
    }

    @Test
    fun givenMarkingPropagateError_whenInvokingTheUseCase_thenFailureIsReturned2() = runTest {
        val failure = StorageFailure.DataNotFound
        given(conversationRepository).coroutine { updateAllConversationsNotificationDate(DATE) }.then { Either.Left(failure) }
        given(conversationRepository)
            .suspendFunction(conversationRepository::updateConversationNotificationDate)
            .whenInvokedWith(eq(CONVERSATION_ID), anything())
            .then { _, _ -> Either.Right(Unit) }

        val result = markMessagesAsNotifiedUseCase(null, DATE)

        assertEquals(result, Result.Failure(failure))
    }

    companion object {
        private const val DATE = "some_date"
        private val CONVERSATION_ID = QualifiedID("some_id", "some_domain")
    }
}
