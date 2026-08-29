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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MessageDeletionPersistenceImplTest {

    @Test
    fun givenDeleteRequest_whenDeleting_thenMessageIdAndMappedConversationIdAreForwarded() = runTest {
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
        val persistence = MessageDeletionPersistenceImpl(messageDAO)

        val result = persistence.deleteMessage(messageId, conversationId)

        assertEquals(Either.Right(Unit), result)
        verifySuspend(VerifyMode.exactly(1)) {
            messageDAO.deleteMessage(eq(messageId), eq(conversationEntity))
        }
    }

    @Test
    fun givenDaoFailure_whenDeleting_thenExceptionIsWrapped() = runTest {
        val expected = IllegalStateException("message deletion failed")
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
        everySuspend { messageDAO.deleteMessage(eq(messageId), eq(conversationEntity)) } throws expected
        val persistence = MessageDeletionPersistenceImpl(messageDAO)

        assertEquals(Either.Left(StorageFailure.Generic(expected)), persistence.deleteMessage(messageId, conversationId))
    }

    @Test
    fun givenDaoCancellation_whenDeleting_thenCancellationEscapes() = runTest {
        val expected = CancellationException("message deletion cancelled")
        val messageDAO = mock<MessageDAO>(mode = MockMode.autoUnit)
        everySuspend { messageDAO.deleteMessage(eq(messageId), eq(conversationEntity)) } throws expected
        val persistence = MessageDeletionPersistenceImpl(messageDAO)

        val actual = assertFailsWith<CancellationException> {
            persistence.deleteMessage(messageId, conversationId)
        }

        assertSame(expected, actual)
    }

    private companion object {
        const val messageId = "message-id"
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationEntity = QualifiedIDEntity("conversation-id", "wire.example")
    }
}
