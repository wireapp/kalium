package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestEvent
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
class ReceiptModeUpdateEventHandlerTest {

    @Test
    fun givenAConversationEventReceiptMode_whenHandlingIt_thenShouldUpdateTheConversation() = runTest {
        val event = TestEvent.receiptModeUpdate()
        val (arrangement, eventHandler) = Arrangement()
            .withUpdateReceiptModeSuccess()
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verify(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationReceiptMode)
                .with(any(), any())
                .wasInvoked(exactly = once)
        }
    }

    private class Arrangement {

        @Mock
        val conversationDAO = mock(classOf<ConversationDAO>())

        private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler = ReceiptModeUpdateEventHandlerImpl(
            conversationDAO = conversationDAO,
            idMapper = MapperProvider.idMapper(),
            receiptModeMapper = MapperProvider.receiptModeMapper()
        )

        fun withUpdateReceiptModeSuccess() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateConversationReceiptMode)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun arrange() = this to receiptModeUpdateEventHandler
    }
}
