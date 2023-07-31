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
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
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
