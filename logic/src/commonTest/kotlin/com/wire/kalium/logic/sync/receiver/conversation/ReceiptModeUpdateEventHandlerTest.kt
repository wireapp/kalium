package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.ConversationDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptModeUpdateEventHandlerTest {

    @Test
    fun givenAConversationEventReceiptMode_whenHandlingIt_thenShouldUpdateTheConversation() = runTest {
        // given
        val event = TestEvent.receiptModeUpdate()
        val (arrangement, eventHandler) = Arrangement()
            .withUpdateReceiptModeSuccess()
            .withPersistingSystemMessage()
            .arrange()

        // when
        eventHandler.handle(event)

        // then
        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationReceiptMode)
                .with(any(), any())
                .wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAConversationEventReceiptMode_whenHandlingIt_thenShouldPersistEnabledReceiptModeChangedSystemMessage() = runTest {
        // given
        val event = TestEvent.receiptModeUpdate()
        val (arrangement, eventHandler) = Arrangement()
            .withUpdateReceiptModeSuccess()
            .withPersistingSystemMessage()
            .arrange()

        // when
        eventHandler.handle(event)

        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                val content = it.content as MessageContent.ConversationReceiptModeChanged
                content.receiptMode
            })
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationEventReceiptMode_whenHandlingIt_thenShouldPersistDisabledReceiptModeChangedSystemMessage() = runTest {
        // given
        val event = TestEvent.receiptModeUpdate().copy(
            receiptMode = Conversation.ReceiptMode.DISABLED
        )
        val (arrangement, eventHandler) = Arrangement()
            .withUpdateReceiptModeSuccess()
            .withPersistingSystemMessage()
            .arrange()

        // when
        eventHandler.handle(event)

        // then
        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(matching {
                val content = it.content as MessageContent.ConversationReceiptModeChanged
                content.receiptMode.not()
            })
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler = ReceiptModeUpdateEventHandlerImpl(
            conversationDAO = conversationDAO,
            receiptModeMapper = MapperProvider.receiptModeMapper(),
            persistMessage = persistMessage
        )

        fun withUpdateReceiptModeSuccess() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationReceiptMode)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withPersistingSystemMessage() = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to receiptModeUpdateEventHandler
    }
}
