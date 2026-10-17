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
package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class LegalHoldDeclarationTest {

    @Test
    fun regularMessagesDeclareTheStoredStatus() {
        for (status in listOf(Conversation.LegalHoldStatus.ENABLED, Conversation.LegalHoldStatus.DISABLED)) {
            assertEquals(status, declaredLegalHoldStatus(Either.Right(status), REGULAR))
        }
    }

    @Test
    fun unreadableStatusIsUnknown() {
        assertEquals(Conversation.LegalHoldStatus.UNKNOWN, declaredLegalHoldStatus(Either.Left(StorageFailure.DataNotFound), REGULAR))
    }

    @Test
    fun signalingMessagesDeclareNoStatus() {
        assertEquals(
            Conversation.LegalHoldStatus.UNKNOWN,
            declaredLegalHoldStatus(Either.Right(Conversation.LegalHoldStatus.ENABLED), SIGNALING)
        )
    }

    private companion object {
        val CONVERSATION = ConversationId("conversation", "example.com")
        val SENDER = UserId("sender", "example.com")
        val DATE = Instant.fromEpochSeconds(0)
        val REGULAR = Message.Regular(
            id = "message",
            content = MessageContent.Text("text"),
            conversationId = CONVERSATION,
            date = DATE,
            senderUserId = SENDER,
            status = Message.Status.Pending,
            isSelfMessage = true,
            senderClientId = ClientId("client"),
            editStatus = Message.EditStatus.NotEdited,
        )
        val SIGNALING = Message.Signaling(
            id = "edit",
            content = MessageContent.DeleteMessage("message"),
            conversationId = CONVERSATION,
            date = DATE,
            senderUserId = SENDER,
            senderClientId = ClientId("client"),
            status = Message.Status.Pending,
            isSelfMessage = true,
            expirationData = null,
        )
    }
}
