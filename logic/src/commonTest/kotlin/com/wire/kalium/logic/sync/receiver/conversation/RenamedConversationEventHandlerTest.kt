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
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

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
            coVerify {
                conversationDao.updateConversationName(any(), any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                persistMessage.invoke(any())
            }.wasInvoked(exactly = once)
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
            coVerify {
                conversationDao.updateConversationName(any(), any(), any())
            }.wasInvoked(exactly = once)

            coVerify {
                persistMessage.invoke(any())
            }.wasNotInvoked()
        }
    }

    private class Arrangement {

        @Mock
        val persistMessage = mock(PersistMessageUseCase::class)

        @Mock
        val conversationDao = mock(ConversationDAO::class)

        private val renamedConversationEventHandler: RenamedConversationEventHandler = RenamedConversationEventHandlerImpl(
            conversationDao,
            persistMessage
        )

        suspend fun withRenamingConversationSuccess() = apply {
            coEvery {
                conversationDao.updateConversationName(any(), any(), any())
            }.returns(Unit)
        }

        suspend fun withRenamingConversationFailure() = apply {
            coEvery {
                conversationDao.updateConversationName(any(), any(), any())
            }.throws(Exception("An error occurred persisting the data"))
        }

        suspend fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            coEvery {
                persistMessage.invoke(any())
            }.returns(result)
        }

        fun arrange() = this to renamedConversationEventHandler
    }

}
