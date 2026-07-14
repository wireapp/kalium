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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        eventHandler.handle(event).shouldSucceed()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDao.updateConversationName(any(), any(), any())
            }

            verifySuspend(VerifyMode.exactly(1)) {
                persistMessage.invoke(any())
            }
        }
    }

    @Test
    fun givenSystemMessagePersistenceFails_whenHandlingRenameEvent_thenFailureIsPropagated() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withRenamingConversationSuccess()
            .withPersistingMessageReturning(Either.Left(CoreFailure.Unknown(RuntimeException("message failed"))))
            .arrange()

        eventHandler.handle(event).shouldFail()
    }

    @Test
    fun givenAConversationEventRenamed_whenHandlingItFails_thenShouldNotUpdateTheConversation() = runTest {
        val event = TestEvent.renamedConversation()
        val (arrangement, eventHandler) = Arrangement()
            .withRenamingConversationFailure()
            .withPersistingMessageReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event).shouldFail()

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                conversationDao.updateConversationName(any(), any(), any())
            }

            verifySuspend(VerifyMode.not) {
                persistMessage.invoke(any())
            }
        }
    }

    private class Arrangement {
        val persistMessage = mock<PersistMessageUseCase>()
        val conversationDao = mock<ConversationDAO>(mode = MockMode.autoUnit)

        private val renamedConversationEventHandler: RenamedConversationEventHandler = RenamedConversationEventHandlerImpl(
            conversationDao,
            persistMessage
        )

        suspend fun withRenamingConversationSuccess() = apply {
            everySuspend {
                conversationDao.updateConversationName(any(), any(), any())
            } returns Unit
        }

        suspend fun withRenamingConversationFailure() = apply {
            everySuspend {
                conversationDao.updateConversationName(any(), any(), any())
            } throws Exception("An error occurred persisting the data")
        }

        suspend fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                persistMessage.invoke(any())
            } returns result
        }

        fun arrange() = this to renamedConversationEventHandler
    }

}
