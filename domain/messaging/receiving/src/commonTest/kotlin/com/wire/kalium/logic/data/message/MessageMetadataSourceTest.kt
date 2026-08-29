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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageMetadataDAO
import dev.mokkery.answering.returns
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

class MessageMetadataSourceTest {

    @Test
    fun givenStoredSender_whenLookingUp_thenExactIdsAreMappedAndSenderIsReturned() = runTest {
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.messageMetadataDAO.originalSenderId(eq(conversationIdEntity), eq(messageId))
        } returns senderIdEntity

        val result = repository.originalSenderId(conversationId, messageId)

        assertEquals(Either.Right(senderId), result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.messageMetadataDAO.originalSenderId(eq(conversationIdEntity), eq(messageId))
        }
    }

    @Test
    fun givenMissingMessage_whenLookingUp_thenDataNotFoundIsReturned() = runTest {
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.messageMetadataDAO.originalSenderId(eq(conversationIdEntity), eq(messageId))
        } returns null

        assertEquals(Either.Left(StorageFailure.DataNotFound), repository.originalSenderId(conversationId, messageId))
    }

    @Test
    fun givenSenderLookupFailure_whenLookingUp_thenFailureIsWrapped() = runTest {
        val expected = IllegalStateException("sender lookup failed")
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.messageMetadataDAO.originalSenderId(eq(conversationIdEntity), eq(messageId))
        } throws expected

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            repository.originalSenderId(conversationId, messageId),
        )
    }

    @Test
    fun givenSenderLookupCancellation_whenLookingUp_thenCancellationEscapes() = runTest {
        val expected = CancellationException("sender lookup cancelled")
        val (arrangement, repository) = arrangement()
        everySuspend {
            arrangement.messageMetadataDAO.originalSenderId(eq(conversationIdEntity), eq(messageId))
        } throws expected

        val actual = assertFailsWith<CancellationException> {
            repository.originalSenderId(conversationId, messageId)
        }

        assertSame(expected, actual)
    }

    private fun arrangement(): Pair<Arrangement, MessageMetadataRepository> {
        val arrangement = Arrangement()
        return arrangement to MessageMetadataSource(arrangement.messageMetadataDAO)
    }

    private class Arrangement {
        val messageMetadataDAO = mock<MessageMetadataDAO>()
    }

    private companion object {
        const val messageId = "message-id"
        val conversationId = ConversationId("conversation-id", "wire.example")
        val conversationIdEntity = ConversationIDEntity("conversation-id", "wire.example")
        val senderId = UserId("sender-id", "wire.example")
        val senderIdEntity = UserIDEntity("sender-id", "wire.example")
    }
}
