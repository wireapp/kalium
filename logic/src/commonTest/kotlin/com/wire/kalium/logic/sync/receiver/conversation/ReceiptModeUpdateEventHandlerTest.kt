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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDAO.updateConversationReceiptMode(any(), any())
            }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches {
                    val content = it.content as MessageContent.ConversationReceiptModeChanged
                    content.receiptMode
                }
            )
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistMessage.invoke(
                matches {
                    val content = it.content as MessageContent.ConversationReceiptModeChanged
                    content.receiptMode.not()
                }
            )
        }
    }

    private class Arrangement {
        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        val persistMessage = mock<PersistMessageUseCase>()

        private val receiptModeUpdateEventHandler: ReceiptModeUpdateEventHandler = ReceiptModeUpdateEventHandlerImpl(
            conversationDAO = conversationDAO,
            receiptModeMapper = MapperProvider.receiptModeMapper(),
            persistMessage = persistMessage
        )

        suspend fun withUpdateReceiptModeSuccess() = apply {
            everySuspend {
                conversationDAO.updateConversationReceiptMode(any(), any())
            } returns Unit
        }

        suspend fun withPersistingSystemMessage() = apply {
            everySuspend {
                persistMessage.invoke(any())
            } returns Either.Right(Unit)
        }

        fun arrange() = this to receiptModeUpdateEventHandler
    }
}
