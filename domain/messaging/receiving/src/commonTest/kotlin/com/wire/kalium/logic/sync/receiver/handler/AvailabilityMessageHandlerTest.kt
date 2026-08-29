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

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.IncomingAvailabilityPersistence
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class AvailabilityMessageHandlerTest {

    @Test
    fun givenAvailabilityMessage_whenHandling_thenUserAvailabilityIsUpdated() = runTest {
        val content = MessageContent.Availability(UserAvailabilityStatus.AWAY)
        val persistence = mock<IncomingAvailabilityPersistence>(MockMode.autoUnit)
        everySuspend { persistence.updateAvailabilityStatus(any(), any()) } returns Unit
        val handler = AvailabilityMessageHandlerImpl(persistence)

        handler.handle(signalingMessage.copy(content = content), content)

        verifySuspend(VerifyMode.exactly(1)) {
            persistence.updateAvailabilityStatus(signalingMessage.senderUserId, content.status)
        }
    }

    @Test
    fun givenAvailabilityMessage_whenHandling_thenEnvelopeSenderAndContentStatusAreForwardedExactlyOnce() = runTest {
        val persistence = RecordingIncomingAvailabilityPersistence()
        val handler = AvailabilityMessageHandlerImpl(persistence)

        handler.handle(signalingMessage, availabilityContent)

        assertEquals(listOf(senderUserId to UserAvailabilityStatus.BUSY), persistence.calls)
    }

    @Test
    fun givenPersistenceFailure_whenHandling_thenSameExceptionEscapes() = runTest {
        val expected = IllegalStateException("availability persistence failed")
        val persistence = RecordingIncomingAvailabilityPersistence(expected)
        val handler = AvailabilityMessageHandlerImpl(persistence)

        val actual = assertFailsWith<IllegalStateException> {
            handler.handle(signalingMessage, availabilityContent)
        }

        assertSame(expected, actual)
        assertEquals(listOf(senderUserId to UserAvailabilityStatus.BUSY), persistence.calls)
    }

    @Test
    fun givenPersistenceCancellation_whenHandling_thenSameCancellationEscapes() = runTest {
        val expected = CancellationException("availability persistence cancelled")
        val persistence = RecordingIncomingAvailabilityPersistence(expected)
        val handler = AvailabilityMessageHandlerImpl(persistence)

        val actual = assertFailsWith<CancellationException> {
            handler.handle(signalingMessage, availabilityContent)
        }

        assertSame(expected, actual)
        assertEquals(listOf(senderUserId to UserAvailabilityStatus.BUSY), persistence.calls)
    }

    private class RecordingIncomingAvailabilityPersistence(
        private val throwable: Throwable? = null,
    ) : IncomingAvailabilityPersistence {
        val calls = mutableListOf<Pair<UserId, UserAvailabilityStatus>>()

        override suspend fun updateAvailabilityStatus(senderUserId: UserId, status: UserAvailabilityStatus) {
            calls += senderUserId to status
            throwable?.let { throw it }
        }
    }

    private companion object {
        val senderUserId = UserId("sender-id", "wire.example")
        val conversationId = ConversationId("conversation-id", "wire.example")
        val messageDate = Instant.parse("2026-08-19T10:15:30Z")
        val availabilityContent = MessageContent.Availability(UserAvailabilityStatus.BUSY)
        val signalingMessage = Message.Signaling(
            id = "signaling-id",
            content = availabilityContent,
            conversationId = conversationId,
            date = messageDate,
            senderUserId = senderUserId,
            senderClientId = ClientId("sender-client"),
            status = Message.Status.Sent,
            isSelfMessage = false,
            expirationData = null,
        )
    }
}
