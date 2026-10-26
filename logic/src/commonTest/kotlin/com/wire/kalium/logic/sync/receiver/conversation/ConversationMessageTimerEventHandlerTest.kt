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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ConversationMessageTimerEventHandlerTest {

    @Test
    fun givenAConversationMessageTimerEvent_whenItGetsUpdated_thenShouldPersistSystemMessage() = runTest {
        val event = TestEvent.timerChanged().copy(systemMessageId = "eventId:2")
        val (arrangement, eventHandler) = Arrangement()
            .withConversationUpdateMessageTimer()
            .withPersistMessage(Either.Right(Unit))
            .arrange()

        assertEquals(Either.Right(Unit), eventHandler.handle(event, requireSystemMessagePersistence = false))

        with(arrangement) {
            verifySuspend(VerifyMode.exactly(1)) {
                persistMessageUseCase.invoke(
                    Message.System(
                    event.systemMessageId,
                    MessageContent.ConversationMessageTimerChanged(
                        messageTimer = event.messageTimer
                    ),
                    event.conversationId,
                    event.dateTime,
                    event.senderUserId,
                    Message.Status.Sent,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                ))
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

        assertIs<Either.Left<*>>(eventHandler.handle(event, requireSystemMessagePersistence = false))

        with(arrangement) {
            verifySuspend(VerifyMode.not) {
                persistMessageUseCase.invoke(any())
            }
        }
    }

    @Test
    fun systemMessageFailureIsBestEffortForTheApp() = runTest {
        val (_, handler) = Arrangement()
            .withPersistMessage(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        assertEquals(Either.Right(Unit), handler.handle(TestEvent.timerChanged(), requireSystemMessagePersistence = false))
    }

    @Test
    fun systemMessageFailureIsRequiredByDefaultForNSE() = runTest {
        val failure = Either.Left(StorageFailure.DataNotFound)
        val (_, handler) = Arrangement().withPersistMessage(failure).arrange()

        assertEquals(failure, handler.handle(TestEvent.timerChanged()))
    }

    @Test
    fun replayDoesNotRegressTimerOrPersistAgainInEitherPolicy() = runTest {
        val event = TestEvent.timerChanged().copy(systemMessageId = "eventId:2")
        for (required in listOf(false, true)) {
            val (arrangement, handler) = Arrangement().arrange()
            everySuspend { arrangement.messageDAO.getMessageById(any(), any()) } returns Message.System(
                id = event.systemMessageId,
                content = MessageContent.ConversationMessageTimerChanged(event.messageTimer),
                conversationId = event.conversationId,
                date = event.dateTime,
                senderUserId = event.senderUserId,
                status = Message.Status.Sent,
                expirationData = null,
            ).toEntity()

            assertEquals(Either.Right(Unit), handler.handle(event, requireSystemMessagePersistence = required))

            verifySuspend(VerifyMode.not) { arrangement.conversationDAO.updateMessageTimer(any(), any()) }
            verifySuspend(VerifyMode.not) { arrangement.persistMessageUseCase.invoke(any()) }
        }
    }

    @Test
    fun replayReadFailureRemainsRequiredForTheApp() = runTest {
        val (arrangement, handler) = Arrangement().arrange()
        everySuspend { arrangement.messageDAO.getMessageById(any(), any()) } throws IOException("Replay read failed")

        assertIs<Either.Left<*>>(handler.handle(TestEvent.timerChanged(), requireSystemMessagePersistence = false))

        verifySuspend(VerifyMode.not) { arrangement.conversationDAO.updateMessageTimer(any(), any()) }
        verifySuspend(VerifyMode.not) { arrangement.persistMessageUseCase.invoke(any()) }
    }

    @Test
    fun cancellationDuringOptionalSystemMessagePersistenceIsPropagated() = runTest {
        val (arrangement, handler) = Arrangement().arrange()
        everySuspend { arrangement.persistMessageUseCase.invoke(any()) } throws CancellationException("Cancelled")

        assertFailsWith<CancellationException> {
            handler.handle(TestEvent.timerChanged(), requireSystemMessagePersistence = false)
        }
    }

    private class Arrangement {

        val conversationDAO = mock<ConversationDAO>(mode = MockMode.autoUnit)
        val messageDAO = mock<MessageDAO>(mode = MockMode.autofill)
        val persistMessageUseCase = mock<PersistMessageUseCase>()

        private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler = ConversationMessageTimerEventHandlerImpl(
            conversationDAO,
            messageDAO,
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
