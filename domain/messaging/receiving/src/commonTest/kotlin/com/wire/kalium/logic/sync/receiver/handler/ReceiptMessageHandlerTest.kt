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
import com.wire.kalium.logic.data.message.IncomingReceiptPersistence
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReceiptMessageHandlerTest {

    @Test
    fun givenReadReceipt_whenHandled_thenStatusAndReceiptArePersistedBeforeHookWithOriginalPayload() = runTest {
        val arrangement = Arrangement(ReceiptType.READ)

        arrangement.handler.handle(arrangement.message, arrangement.content)

        assertEquals(listOf("status", "receipt", "hook"), arrangement.callOrder)
        assertEquals(MessageEntity.Status.READ, arrangement.persistence.statusCalls.single().messageStatus)
        assertEquals(conversationId, arrangement.persistence.statusCalls.single().conversationId)
        assertEquals(messageIds, arrangement.persistence.statusCalls.single().messageIds)
        assertEquals(senderUserId, arrangement.persistence.receiptCalls.single().userId)
        assertEquals(conversationId, arrangement.persistence.receiptCalls.single().conversationId)
        assertEquals(date, arrangement.persistence.receiptCalls.single().date)
        assertEquals(ReceiptType.READ, arrangement.persistence.receiptCalls.single().type)
        assertEquals(messageIds, arrangement.persistence.receiptCalls.single().messageIds)
        assertEquals(ReadReceiptEventData(conversationId, messageIds, date), arrangement.hook.calls.single().first)
        assertEquals(selfUserId, arrangement.hook.calls.single().second)
    }

    @Test
    fun givenDeliveredReceipt_whenHandled_thenDeliveredStatusIsMappedAndHookIsNotCalled() = runTest {
        val arrangement = Arrangement(ReceiptType.DELIVERED)

        arrangement.handler.handle(arrangement.message, arrangement.content)

        assertEquals(listOf("status", "receipt"), arrangement.callOrder)
        assertEquals(MessageEntity.Status.DELIVERED, arrangement.persistence.statusCalls.single().messageStatus)
        assertTrue(arrangement.hook.calls.isEmpty())
    }

    @Test
    fun givenStatusUpdateFailure_whenHandled_thenReceiptInsertAndReadHookAreStillAttempted() = runTest {
        val arrangement = Arrangement(
            type = ReceiptType.READ,
            statusResult = Either.Left(StorageFailure.DataNotFound),
        )

        arrangement.handler.handle(arrangement.message, arrangement.content)

        assertEquals(listOf("status", "receipt", "hook"), arrangement.callOrder)
    }

    @Test
    fun givenReceiptInsertFailure_whenHandled_thenFailurePropagatesAndHookIsNotCalled() = runTest {
        val expectedException = IllegalStateException("receipt insert failed")
        val arrangement = Arrangement(
            type = ReceiptType.READ,
            receiptFailure = expectedException,
        )

        val actualException = assertFailsWith<IllegalStateException> {
            arrangement.handler.handle(arrangement.message, arrangement.content)
        }

        assertSame(expectedException, actualException)
        assertEquals(listOf("status", "receipt"), arrangement.callOrder)
        assertTrue(arrangement.hook.calls.isEmpty())
    }

    @Test
    fun givenSelfSenderReceipt_whenHandled_thenNoPersistenceOrHookIsAttempted() = runTest {
        val arrangement = Arrangement(ReceiptType.READ)

        arrangement.handler.handle(
            arrangement.message.copy(senderUserId = selfUserId),
            arrangement.content,
        )

        assertTrue(arrangement.callOrder.isEmpty())
        assertTrue(arrangement.persistence.statusCalls.isEmpty())
        assertTrue(arrangement.persistence.receiptCalls.isEmpty())
        assertTrue(arrangement.hook.calls.isEmpty())
    }

    private class Arrangement(
        type: ReceiptType,
        statusResult: Either<CoreFailure, Unit> = Either.Right(Unit),
        receiptFailure: Throwable? = null,
    ) {
        val callOrder = mutableListOf<String>()
        val persistence = RecordingIncomingReceiptPersistence(callOrder, statusResult, receiptFailure)
        val hook = RecordingHook(callOrder)
        val content = MessageContent.Receipt(type, messageIds)
        val message = Message.Signaling(
            id = "signaling-id",
            content = content,
            conversationId = conversationId,
            date = date,
            senderUserId = senderUserId,
            senderClientId = ClientId("client-id"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
        val handler: ReceiptMessageHandler = ReceiptMessageHandlerImpl(
            selfUserId = selfUserId,
            incomingReceiptPersistence = persistence,
            persistenceEventHookNotifier = hook,
        )
    }

    private class RecordingIncomingReceiptPersistence(
        private val callOrder: MutableList<String>,
        private val result: Either<CoreFailure, Unit>,
        private val failure: Throwable?,
    ) : IncomingReceiptPersistence {
        val statusCalls = mutableListOf<StatusCall>()
        val receiptCalls = mutableListOf<ReceiptCall>()

        override suspend fun updateReferencedMessageStatusesIfNotRead(
            messageStatus: MessageEntity.Status,
            conversationId: ConversationId,
            messageIds: List<String>,
        ): Either<CoreFailure, Unit> {
            callOrder += "status"
            statusCalls += StatusCall(messageStatus, conversationId, messageIds)
            return result
        }

        override suspend fun insertReceipts(
            userId: UserId,
            conversationId: ConversationId,
            date: Instant,
            type: ReceiptType,
            messageIds: List<String>,
        ) {
            callOrder += "receipt"
            receiptCalls += ReceiptCall(userId, conversationId, date, type, messageIds)
            failure?.let { throw it }
        }
    }

    private class RecordingHook(
        private val callOrder: MutableList<String>,
    ) : PersistenceEventHookNotifier {
        val calls = mutableListOf<Pair<ReadReceiptEventData, UserId>>()

        override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
            callOrder += "hook"
            calls += data to selfUserId
        }
    }

    private data class StatusCall(
        val messageStatus: MessageEntity.Status,
        val conversationId: ConversationId,
        val messageIds: List<String>,
    )

    private data class ReceiptCall(
        val userId: UserId,
        val conversationId: ConversationId,
        val date: Instant,
        val type: ReceiptType,
        val messageIds: List<String>,
    )

    private companion object {
        val selfUserId = UserId("self-user", "wire.example")
        val senderUserId = UserId("sender-user", "wire.example")
        val conversationId = ConversationId("conversation-id", "wire.example")
        val date = Instant.parse("2026-08-19T10:15:30Z")
        val messageIds = listOf("message-2", "message-1")
    }
}
