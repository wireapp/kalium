/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class ClientActionMessageHandlerTest {

    @Test
    fun givenClientAction_whenHandling_thenExactCryptoSessionResetSystemMessageIsPersistedOnce() = runTest {
        val persistence = RecordingPersistMessageUseCase()
        val handler = ClientActionMessageHandlerImpl(persistence)

        handler.handle(signalingMessage)

        assertEquals(expectedPersistedMessages, persistence.calls)
    }

    @Test
    fun givenEitherRightOrLeft_whenPersisting_thenReturnedResultIsIgnored() = runTest {
        val results: List<Either<CoreFailure, Unit>> = listOf(
            Either.Right(Unit),
            Either.Left(StorageFailure.DataNotFound),
        )

        results.forEach { result ->
            val persistence = RecordingPersistMessageUseCase(result = result)
            val handler = ClientActionMessageHandlerImpl(persistence)

            handler.handle(signalingMessage)

            assertEquals(expectedPersistedMessages, persistence.calls)
        }
    }

    @Test
    fun givenPersistenceException_whenHandling_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("client-action persistence failed")
        val persistence = RecordingPersistMessageUseCase(throwable = expected)
        val handler = ClientActionMessageHandlerImpl(persistence)

        val actual = assertFailsWith<IllegalStateException> {
            handler.handle(signalingMessage)
        }

        assertSame(expected, actual)
        assertEquals(expectedPersistedMessages, persistence.calls)
    }

    @Test
    fun givenPersistenceCancellation_whenHandling_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("client-action persistence cancelled")
        val persistence = RecordingPersistMessageUseCase(throwable = expected)
        val handler = ClientActionMessageHandlerImpl(persistence)

        val actual = assertFailsWith<CancellationException> {
            handler.handle(signalingMessage)
        }

        assertSame(expected, actual)
        assertEquals(expectedPersistedMessages, persistence.calls)
    }

    private class RecordingPersistMessageUseCase(
        private val result: Either<CoreFailure, Unit> = Either.Right(Unit),
        private val throwable: Throwable? = null,
    ) : PersistMessageUseCase {
        val calls = mutableListOf<Message.Standalone>()

        override suspend fun invoke(message: Message.Standalone): Either<CoreFailure, Unit> {
            calls += message
            throwable?.let { throw it }
            return result
        }
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
        val senderUserId = UserId("sender-id", "wire.example")
        val messageDate = Instant.parse("2026-08-19T10:15:30Z")
        val signalingMessage = Message.Signaling(
            id = "signaling-id",
            content = MessageContent.ClientAction,
            conversationId = conversationId,
            date = messageDate,
            senderUserId = senderUserId,
            senderClientId = ClientId("sender-client"),
            status = Message.Status.Delivered,
            senderUserName = "Sender Name",
            isSelfMessage = true,
            expirationData = Message.ExpirationData(30.seconds),
        )
        val expectedSystemMessage = Message.System(
            id = signalingMessage.id,
            content = MessageContent.CryptoSessionReset,
            conversationId = signalingMessage.conversationId,
            date = signalingMessage.date,
            senderUserId = signalingMessage.senderUserId,
            status = signalingMessage.status,
            senderUserName = signalingMessage.senderUserName,
            expirationData = null,
        )
        val expectedPersistedMessages: List<Message.Standalone> = listOf(expectedSystemMessage)
    }
}
