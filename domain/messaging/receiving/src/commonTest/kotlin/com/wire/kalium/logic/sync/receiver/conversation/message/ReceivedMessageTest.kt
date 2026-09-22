/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class ReceivedMessageTest {
    private val self = UserId("self", "example.com")
    private val serverTime = Instant.parse("2026-09-07T10:00:00Z")
    private val receivedAt = serverTime + 30.seconds

    @Test
    fun appLeavesSelfExpirationForItsExecutor() {
        val message = unpacked(self).toRegularMessage(self)

        assertEquals(Message.ExpirationData.SelfDeletionStatus.NotStarted, message.expirationData?.selfDeletionStatus)
        assertEquals(10.seconds, message.expirationData?.expireAfter)
        assertEquals(serverTime, message.date)
    }

    @Test
    fun nseCanPersistSelfDeadlineFromExplicitReceiveTime() {
        val message = unpacked(self).toRegularMessage(self, selfDeletionStartedAt = receivedAt)

        assertEquals(
            Message.ExpirationData.SelfDeletionStatus.Started(receivedAt + 10.seconds),
            message.expirationData?.selfDeletionStatus,
        )
        assertEquals(serverTime, message.date)
    }

    @Test
    fun explicitStartDoesNotStartAnotherUsersExpiration() {
        val message = unpacked(UserId("other", "example.com")).toRegularMessage(self, selfDeletionStartedAt = receivedAt)

        assertEquals(Message.ExpirationData.SelfDeletionStatus.NotStarted, message.expirationData?.selfDeletionStatus)
        assertEquals(10.seconds, message.expirationData?.expireAfter)
    }

    @Test
    fun explicitStartDoesNotAddExpirationToAnOrdinaryMessage() {
        val message = unpacked(self, expiresAfterMillis = null).toRegularMessage(self, selfDeletionStartedAt = receivedAt)

        assertNull(message.expirationData)
    }

    private fun unpacked(sender: UserId, expiresAfterMillis: Long? = 10_000) = MessageUnpackResult.ApplicationMessage(
        conversationId = ConversationId("conversation", "example.com"),
        instant = serverTime,
        senderUserId = sender,
        senderClientId = ClientId("client"),
        content = ProtoContent.Readable(
            messageUid = "message",
            messageContent = MessageContent.Text("text"),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN,
            expiresAfterMillis = expiresAfterMillis,
        ),
    )
}
