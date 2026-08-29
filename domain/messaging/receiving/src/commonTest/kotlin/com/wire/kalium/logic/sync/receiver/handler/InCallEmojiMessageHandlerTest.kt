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

import com.wire.kalium.logic.data.call.InCallReactionMessage
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class InCallEmojiMessageHandlerTest {

    @Test
    fun givenInCallEmoji_whenHandling_thenEnvelopeIdsAndExistingEmojiKeySetAreEmittedExactlyOnce() = runTest {
        val repository = RecordingInCallReactionsRepository()
        val handler = InCallEmojiMessageHandlerImpl(repository)
        val expectedEmojis = content.emojis.keys

        handler.handle(signalingMessage, content)

        assertEquals(1, repository.calls.size)
        val call = repository.calls.single()
        assertEquals(conversationId, call.conversationId)
        assertEquals(senderUserId, call.senderUserId)
        assertSame(expectedEmojis, call.emojis)
    }

    @Test
    fun givenRepositorySuspends_whenHandling_thenHandlerRemainsSuspendedUntilEmissionCompletes() = runTest {
        val emissionCompleted = CompletableDeferred<Unit>()
        val repository = RecordingInCallReactionsRepository(beforeReturn = { emissionCompleted.await() })
        val handler = InCallEmojiMessageHandlerImpl(repository)

        val result = async(start = CoroutineStart.UNDISPATCHED) {
            handler.handle(signalingMessage, content)
        }

        assertFalse(result.isCompleted)
        emissionCompleted.complete(Unit)
        result.await()
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun givenRepositoryFailure_whenHandling_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("in-call reaction emission failed")
        val repository = RecordingInCallReactionsRepository(throwable = expected)
        val handler = InCallEmojiMessageHandlerImpl(repository)

        val actual = assertFailsWith<IllegalStateException> {
            handler.handle(signalingMessage, content)
        }

        assertSame(expected, actual)
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun givenRepositoryCancellation_whenHandling_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("in-call reaction emission cancelled")
        val repository = RecordingInCallReactionsRepository(throwable = expected)
        val handler = InCallEmojiMessageHandlerImpl(repository)

        val actual = assertFailsWith<CancellationException> {
            handler.handle(signalingMessage, content)
        }

        assertSame(expected, actual)
        assertEquals(1, repository.calls.size)
    }

    private class RecordingInCallReactionsRepository(
        private val throwable: Throwable? = null,
        private val beforeReturn: suspend () -> Unit = {},
    ) : InCallReactionsRepository {
        val calls = mutableListOf<InCallReactionMessage>()

        override suspend fun addInCallReaction(
            conversationId: ConversationId,
            senderUserId: UserId,
            emojis: Set<String>,
        ) {
            calls += InCallReactionMessage(conversationId, senderUserId, emojis)
            beforeReturn()
            throwable?.let { throw it }
        }

        override fun observeInCallReactions(conversationId: ConversationId): Flow<InCallReactionMessage> = emptyFlow()
    }

    private companion object {
        val conversationId = ConversationId("conversation-id", "wire.example")
        val senderUserId = UserId("sender-id", "wire.example")
        val content = MessageContent.InCallEmoji(linkedMapOf("first" to 1, "second" to 2))
        val signalingMessage = Message.Signaling(
            id = "signaling-id",
            content = content,
            conversationId = conversationId,
            date = Instant.parse("2026-08-19T10:15:30Z"),
            senderUserId = senderUserId,
            senderClientId = ClientId("sender-client"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
    }
}
