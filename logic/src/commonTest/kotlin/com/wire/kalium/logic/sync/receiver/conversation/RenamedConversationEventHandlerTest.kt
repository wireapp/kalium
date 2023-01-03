package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.ConversationDAO
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

@OptIn(ExperimentalCoroutinesApi::class)
class RenamedConversationEventHandlerTest {

    @Test
    fun givenAConversationEventRenamed_whenHandlingIt_thenShouldRenameTheConversation() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withRenamingConversationSuccess()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationDao)
                .suspendFunction(conversationDao::updateConversationName)
                .with(any(), any(), any())
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
            .withRenamingConversationFailure()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationDao)
                .suspendFunction(conversationDao::updateConversationName)
                .with(any(), any(), any())
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
        val conversationDao = mock(classOf<ConversationDAO>())

        private val renamedConversationEventHandler: RenamedConversationEventHandler = RenamedConversationEventHandlerImpl(
            conversationDao,
            persistMessage
        )

        fun withRenamingConversationSuccess() = apply {
            given(conversationDao)
                .suspendFunction(conversationDao::updateConversationName)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Unit)
        }

        fun withRenamingConversationFailure() = apply {
            given(conversationDao)
                .suspendFunction(conversationDao::updateConversationName)
                .whenInvokedWith(any(), any(), any())
                .thenThrow(Exception("An error occurred persisting the data"))
        }

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to renamedConversationEventHandler
    }

}
