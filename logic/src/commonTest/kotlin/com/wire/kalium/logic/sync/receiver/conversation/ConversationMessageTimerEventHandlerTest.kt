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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
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
            coVerify {
                persistMessageUseCase.invoke(
                    eq(Message.System(
                    event.id,
                    MessageContent.ConversationMessageTimerChanged(
                        messageTimer = event.messageTimer
                    ),
                    event.conversationId,
                    event.timestampIso,
                    event.senderUserId,
                    Message.Status.Sent,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )))
            }.wasInvoked(once)
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
            coVerify {
                persistMessageUseCase.invoke(any())
            }.wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val conversationDAO = mock(classOf<ConversationDAO>())

        @Mock
        val persistMessageUseCase = mock(classOf<PersistMessageUseCase>())

        private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler = ConversationMessageTimerEventHandlerImpl(
            conversationDAO,
            persistMessageUseCase
        )

        suspend fun withConversationUpdateMessageTimer() = apply {
            coEvery {
                conversationDAO.updateMessageTimer(any(), any())
            }.returns(Unit)
        }

        suspend fun withConversationUpdateMessageTimerError() = apply {
            coEvery {
                conversationDAO.updateMessageTimer(any(), any())
            }.throws(IOException("Some error"))
        }

        suspend fun withPersistMessage(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                persistMessageUseCase.invoke(any())
            }.returns(result)
        }

        fun arrange() = this to conversationMessageTimerEventHandler
    }
}
