/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
            coVerify {
                conversationDAO.updateConversationReceiptMode(any(), any())
            }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    val content = it.content as MessageContent.ConversationReceiptModeChanged
                    content.receiptMode
                }
            )
        }.wasInvoked(exactly = once)
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
        coVerify {
            arrangement.persistMessage.invoke(
                matches {
                    val content = it.content as MessageContent.ConversationReceiptModeChanged
                    content.receiptMode.not()
                }
            )
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val conversationDAO = mock(ConversationDAO::class)

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler = ReceiptModeUpdateEventHandlerImpl(
            conversationDAO = conversationDAO,
            receiptModeMapper = MapperProvider.receiptModeMapper(),
            persistMessage = persistMessage
        )

        suspend fun withUpdateReceiptModeSuccess() = apply {
            coEvery {
                conversationDAO.updateConversationReceiptMode(any(), any())
            }.returns(Unit)
        }

        suspend fun withPersistingSystemMessage() = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(Either.Right(Unit))
        }

        fun arrange() = this to receiptModeUpdateEventHandler
    }
}
