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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageDeletionPersistence
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class DeleteForMeHandlerTest {

    @Test
    fun givenVerifiedEnvelope_whenHandling_thenPayloadIsDeletedBeforeExactHookNotification() = runTest {
        val calls = mutableListOf<String>()
        var verifiedConversationId: ConversationId? = null
        var deletedMessageId: String? = null
        var deletedConversationId: ConversationId? = null
        var hookData: MessageDeleteEventData? = null
        var hookUserId: UserId? = null
        val handler = handler(
            verifier = {
                verifiedConversationId = it.conversationId
                true
            },
            deletion = { messageId, conversationId ->
                calls += "delete"
                deletedMessageId = messageId
                deletedConversationId = conversationId
                Either.Right(Unit)
            },
            hook = { data, userId ->
                calls += "hook"
                hookData = data
                hookUserId = userId
            },
        )

        handler.handle(signalingMessage, payload)

        assertEquals(envelopeConversationId, verifiedConversationId)
        assertEquals(payload.messageId, deletedMessageId)
        assertEquals(payload.conversationId, deletedConversationId)
        assertEquals(MessageDeleteEventData(payloadConversationId, payloadMessageId), hookData)
        assertEquals(selfUserId, hookUserId)
        assertEquals(listOf("delete", "hook"), calls)
    }

    @Test
    fun givenWrappedDeleteFailure_whenHandling_thenFailureIsIgnoredAndHookStillRuns() = runTest {
        val calls = mutableListOf<String>()
        val handler = handler(
            verifier = { true },
            deletion = { _, _ ->
                calls += "delete"
                Either.Left(StorageFailure.DataNotFound)
            },
            hook = { _, _ -> calls += "hook" },
        )

        handler.handle(signalingMessage, payload)

        assertEquals(listOf("delete", "hook"), calls)
    }

    @Test
    fun givenDeleteException_whenHandling_thenExceptionEscapesAndHookIsSkipped() = runTest {
        val expected = IllegalStateException("delete escaped")
        var hookCalls = 0
        val handler = handler(
            verifier = { true },
            deletion = { _, _ -> throw expected },
            hook = { _, _ -> hookCalls += 1 },
        )

        val actual = assertFailsWith<IllegalStateException> { handler.handle(signalingMessage, payload) }

        assertSame(expected, actual)
        assertEquals(0, hookCalls)
    }

    @Test
    fun givenDeleteCancellation_whenHandling_thenCancellationEscapesAndHookIsSkipped() = runTest {
        val expected = CancellationException("delete cancelled")
        var hookCalls = 0
        val handler = handler(
            verifier = { true },
            deletion = { _, _ -> throw expected },
            hook = { _, _ -> hookCalls += 1 },
        )

        val actual = assertFailsWith<CancellationException> { handler.handle(signalingMessage, payload) }

        assertSame(expected, actual)
        assertEquals(0, hookCalls)
    }

    @Test
    fun givenUnverifiedEnvelope_whenHandling_thenDeleteAndHookAreSkipped() = runTest {
        var deleteCalls = 0
        var hookCalls = 0
        val handler = handler(
            verifier = { false },
            deletion = { _, _ ->
                deleteCalls += 1
                Either.Right(Unit)
            },
            hook = { _, _ -> hookCalls += 1 },
        )

        handler.handle(signalingMessage, payload)

        assertEquals(0, deleteCalls)
        assertEquals(0, hookCalls)
    }

    private fun handler(
        verifier: suspend (Message) -> Boolean,
        deletion: suspend (String, ConversationId) -> Either<StorageFailure, Unit>,
        hook: suspend (MessageDeleteEventData, UserId) -> Unit,
    ): DeleteForMeHandler = DeleteForMeHandlerImpl(
        messageDeletionPersistence = MessageDeletionPersistence { messageId, conversationId ->
            deletion(messageId, conversationId)
        },
        isMessageSentInSelfConversation = object : IsMessageSentInSelfConversationUseCase {
            override suspend fun invoke(message: Message): Boolean = verifier(message)
        },
        persistenceEventHookNotifier = object : PersistenceEventHookNotifier {
            override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
                hook(data, selfUserId)
            }
        },
        selfUserId = selfUserId,
    )

    private companion object {
        const val payloadMessageId = "payload-message-id"
        val envelopeConversationId = ConversationId("envelope-conversation", "wire.example")
        val payloadConversationId = ConversationId("payload-conversation", "wire.example")
        val selfUserId = UserId("self-user", "wire.example")
        val payload = MessageContent.DeleteForMe(payloadMessageId, payloadConversationId)
        val signalingMessage = Message.Signaling(
            id = "signaling-message-id",
            content = payload,
            conversationId = envelopeConversationId,
            date = Instant.parse("2026-08-19T10:15:30Z"),
            senderUserId = selfUserId,
            senderClientId = ClientId("self-client"),
            status = Message.Status.Sent,
            isSelfMessage = true,
            expirationData = null,
        )
    }
}
