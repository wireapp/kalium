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

package com.wire.kalium.logic.data.message.receipt

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.receipt.ReceiptDAO
import com.wire.kalium.persistence.dao.receipt.ReceiptTypeEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class IncomingReceiptPersistenceImplTest {

    @Test
    fun givenMessageReferences_whenUpdatingStatus_thenValuesAreForwardedToMessageDao() = runTest {
        val (arrangement, persistence) = arrangement()

        val result = persistence.updateReferencedMessageStatusesIfNotRead(messageStatus, conversationId, messageIds)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageDAO.updateMessagesStatusIfNotRead(
                eq(messageStatus),
                eq(conversationIdEntity),
                eq(messageIds),
            )
        }
    }

    @Test
    fun givenMessageDaoFailure_whenUpdatingStatus_thenFailureIsWrapped() = runTest {
        val expectedException = IllegalStateException("status update failed")
        val (arrangement, persistence) = arrangement()
        everySuspend {
            arrangement.messageDAO.updateMessagesStatusIfNotRead(
                eq(messageStatus),
                eq(conversationIdEntity),
                eq(messageIds),
            )
        } throws expectedException

        val result = persistence.updateReferencedMessageStatusesIfNotRead(messageStatus, conversationId, messageIds)

        assertEquals(Either.Left(StorageFailure.Generic(expectedException)), result)
    }

    @Test
    fun givenReadReceipt_whenInserting_thenReadEntityAndOriginalPayloadAreForwarded() = runTest {
        val (arrangement, persistence) = arrangement()

        persistence.insertReceipts(userId, conversationId, date, ReceiptType.READ, messageIds)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.receiptDAO.insertReceipts(
                eq(userIdEntity),
                eq(conversationIdEntity),
                eq(date),
                eq(ReceiptTypeEntity.READ),
                eq(messageIds),
            )
        }
    }

    @Test
    fun givenDeliveredReceipt_whenInserting_thenDeliveryEntityIsForwarded() = runTest {
        val (arrangement, persistence) = arrangement()

        persistence.insertReceipts(userId, conversationId, date, ReceiptType.DELIVERED, messageIds)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.receiptDAO.insertReceipts(
                eq(userIdEntity),
                eq(conversationIdEntity),
                eq(date),
                eq(ReceiptTypeEntity.DELIVERY),
                eq(messageIds),
            )
        }
    }

    @Test
    fun givenReceiptDaoFailure_whenInserting_thenFailurePropagates() = runTest {
        val expectedException = IllegalStateException("receipt insert failed")
        val (arrangement, persistence) = arrangement()
        everySuspend {
            arrangement.receiptDAO.insertReceipts(
                eq(userIdEntity),
                eq(conversationIdEntity),
                eq(date),
                eq(ReceiptTypeEntity.READ),
                eq(messageIds),
            )
        } throws expectedException

        val actualException = assertFailsWith<IllegalStateException> {
            persistence.insertReceipts(userId, conversationId, date, ReceiptType.READ, messageIds)
        }

        assertSame(expectedException, actualException)
    }

    private fun arrangement(): Pair<Arrangement, IncomingReceiptPersistence> {
        val arrangement = Arrangement()
        return arrangement to IncomingReceiptPersistenceImpl(arrangement.messageDAO, arrangement.receiptDAO)
    }

    private class Arrangement {
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
        val receiptDAO = mock<ReceiptDAO>(mode = MockMode.autoUnit)
    }

    private companion object {
        val userId = UserId("sender-id", "wire.example")
        val userIdEntity = UserIDEntity("sender-id", "wire.example")
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationIdEntity = ConversationIDEntity("conversation-id", "wire.example")
        val date = Instant.parse("2026-08-19T10:15:30Z")
        val messageStatus = MessageEntity.Status.DELIVERED
        val messageIds = listOf("message-1", "message-2")
    }
}
