/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class MembershipMessagesTest {
    @Test
    fun otherMemberPromotionsDoNotCreateLocalMessages() {
        assertNull(promotion(UserId("other", self.domain)).memberPromotedMessage(self))
        assertNull(promotion(self.copy(domain = "other.example")).memberPromotedMessage(self))
    }

    @Test
    fun selfPromotionKeepsItsCanonicalMessageIdentity() {
        val event = promotion(self)
        val message = assertNotNull(event.memberPromotedMessage(self))

        assertEquals(event.id, message.id)
        assertEquals(event.conversationId, message.conversationId)
        assertEquals(event.dateTime, message.date)
        assertEquals(self, message.senderUserId)
        assertEquals(MessageContent.MemberChange.UserPromotedToAdmin(listOf(self)), message.content)
    }

    private fun promotion(user: UserId) = Event.Conversation.MemberChanged.MemberChangedRole(
        id = "promotion",
        conversationId = ConversationId("group", self.domain),
        dateTime = Instant.parse("2026-11-10T12:00:00Z"),
        member = Conversation.Member(user, Conversation.Member.Role.Admin),
    )

    private val self = UserId("self", "wire.example")
}
