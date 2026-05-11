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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test

class ConversationMessageTimerEventHandlerTest {

    @Test
    fun givenAConversationMessageTimerEvent_whenItGetsUpdated_thenShouldPersistSystemMessage() = runTest {
        val event = TestEvent.timerChanged()
        val (arrangement, eventHandler) = Arrangement()
            .withConversationUpdateMessageTimer()
            .withPersistMessage(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                persistMessageUseCase.invoke(
                    eq(Message.System(
                    event.id,
                    MessageContent.ConversationMessageTimerChanged(
                        messageTimer = event.messageTimer
                    ),
                    event.conversationId,
                    event.dateTime,
                    event.senderUserId,
                    Message.Status.Sent,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )))
            }
        }
    }

    @Test
    fun givenAConversationMessageTimerEvent_whenItFailed_thenShouldNotPersistSystemMessage() = runTest {
        val event = TestEvent.timerChanged()
        val (arrangement, eventHandler) = Arrangement()
            .withConversationUpdateMessageTimerError()
            .withPersistMessage(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                persistMessageUseCase.invoke(any())
            }
        }
    }

    private class Arrangement {

        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        val persistMessageUseCase = mock<PersistMessageUseCase>()

        private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler = ConversationMessageTimerEventHandlerImpl(
            conversationDAO,
            persistMessageUseCase
        )

        suspend fun withConversationUpdateMessageTimer() = apply {
            everySuspend {
                conversationDAO.updateMessageTimer(any(), any())
            } returns Unit
        }

        suspend fun withConversationUpdateMessageTimerError() = apply {
            everySuspend {
                conversationDAO.updateMessageTimer(any(), any())
            } throws IOException("Some error")
        }

        suspend fun withPersistMessage(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                persistMessageUseCase.invoke(any())
            } returns result
        }

        fun arrange() = this to conversationMessageTimerEventHandler
    }
}
