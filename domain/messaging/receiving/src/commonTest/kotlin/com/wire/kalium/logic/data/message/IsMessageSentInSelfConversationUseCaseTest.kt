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
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsMessageSentInSelfConversationUseCaseTest {

    @Test
    fun givenEnvelopeConversationInProviderResults_whenVerifying_thenReturnsTrue() = runTest {
        val verifier = IsMessageSentInSelfConversationUseCaseImpl(
            SelfConversationIdProvider { Either.Right(listOf(envelopeConversationId)) }
        )

        assertTrue(verifier(signalingMessage()))
    }

    @Test
    fun givenEnvelopeConversationNotInProviderResults_whenVerifying_thenReturnsFalse() = runTest {
        val verifier = IsMessageSentInSelfConversationUseCaseImpl(
            SelfConversationIdProvider { Either.Right(listOf(payloadConversationId)) }
        )

        assertFalse(verifier(signalingMessage()))
    }

    @Test
    fun givenProviderFailure_whenVerifying_thenFailsClosed() = runTest {
        val verifier = IsMessageSentInSelfConversationUseCaseImpl(
            SelfConversationIdProvider { Either.Left(StorageFailure.DataNotFound) }
        )

        assertFalse(verifier(signalingMessage()))
    }

    private fun signalingMessage() = Message.Signaling(
        id = "signaling-message-id",
        content = MessageContent.DeleteForMe("payload-message-id", payloadConversationId),
        conversationId = envelopeConversationId,
        date = Instant.parse("2026-08-19T10:15:30Z"),
        senderUserId = UserId("sender", "wire.example"),
        senderClientId = ClientId("sender-client"),
        status = Message.Status.Sent,
        isSelfMessage = true,
        expirationData = null,
    )

    private companion object {
        val envelopeConversationId = ConversationId("envelope-conversation", "wire.example")
        val payloadConversationId = ConversationId("payload-conversation", "wire.example")
    }
}
