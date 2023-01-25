/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
