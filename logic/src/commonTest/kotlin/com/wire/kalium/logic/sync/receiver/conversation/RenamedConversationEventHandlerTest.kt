package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RenamedConversationEventHandlerTest {

    @Test
    fun givenAConversationEventRenamed_whenHandlingIt_thenShouldRenameTheConversation() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withGetConversation()
            .withGetUserAuthor(event.senderUserId)
            .withRenamingConversationReturning()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationName)
                .with(eq(TestConversation.ID), any(), any())
                .wasInvoked(exactly = once)

            verify(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .with(any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationEventRenamed_whenHandlingItFails_thenShouldNotUpdateTheConversation() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withGetConversation()
            .withGetUserAuthor(event.senderUserId)
            .withRenamingConversationReturning(Either.Left(StorageFailure.DataNotFound))
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationName)
                .with(eq(TestConversation.ID), any(), any())
                .wasInvoked(exactly = once)

            verify(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .with(any())
                .wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        private val renamedConversationEventHandler: RenamedConversationEventHandler = RenamedConversationEventHandlerImpl(
            conversationRepository,
            persistMessage
        )

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withRenamingConversationReturning(result: Either<CoreFailure, Unit> = Either.Right(Unit)) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateConversationName)
                .whenInvokedWith(eq(TestConversation.ID), eq("newName"), any())
                .thenReturn(result)
        }

        fun withGetConversation(conversation: Conversation? = TestConversation.CONVERSATION) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationById)
                .whenInvokedWith(any())
                .thenReturn(conversation)
        }

        fun withGetUserAuthor(userId: UserId = TestUser.USER_ID) = apply {
            given(userRepository)
                .suspendFunction(userRepository::observeUser)
                .whenInvokedWith(eq(userId))
                .thenReturn(flowOf(TestUser.OTHER))
        }

        fun arrange() = this to renamedConversationEventHandler
    }

}
