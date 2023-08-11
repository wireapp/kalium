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
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
            verify(persistMessageUseCase)
                .suspendFunction(persistMessageUseCase::invoke)
                .with(
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
                .wasInvoked(once)
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
            verify(persistMessageUseCase)
                .suspendFunction(persistMessageUseCase::invoke)
                .with(any())
                .wasNotInvoked()
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

        fun withConversationUpdateMessageTimer() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateMessageTimer)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        fun withConversationUpdateMessageTimerError() = apply {
            given(conversationDAO)
                .suspendFunction(conversationDAO::updateMessageTimer)
                .whenInvokedWith(any())
                .thenThrow(IOException("Some error"))
        }

        fun withPersistMessage(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessageUseCase)
                .suspendFunction(persistMessageUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to conversationMessageTimerEventHandler
    }
}
