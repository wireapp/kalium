/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlin.time.Instant

/** The input contains only members from the two affected domains, as returned by MemberDAO. */
fun Event.Federation.membersToRemove(conversationDomain: String, selfDomain: String, members: List<UserId>): List<UserId> = when (this) {
    is Event.Federation.Delete -> when (conversationDomain) {
        selfDomain -> members.filterNot { it.domain == selfDomain }
        domain -> members.filter { it.domain == selfDomain }
        else -> members
    }
    is Event.Federation.ConnectionRemoved -> when (conversationDomain) {
        domains.first() -> members.filter { it.domain == domains.last() }
        domains.last() -> members.filter { it.domain == domains.first() }
        else -> members
    }
}

fun Event.Federation.stoppedContent(): MessageContent.FederationStopped = when (this) {
    is Event.Federation.Delete -> MessageContent.FederationStopped.Removed(domain)
    is Event.Federation.ConnectionRemoved -> MessageContent.FederationStopped.ConnectionRemoved(domains)
}

fun federationSystemMessage(
    id: String,
    conversationId: ConversationId,
    selfUserId: UserId,
    content: MessageContent.System,
    date: Instant,
): Message.System = Message.System(
    id = id,
    content = content,
    conversationId = conversationId,
    date = date,
    senderUserId = selfUserId,
    status = Message.Status.Read(0),
    visibility = Message.Visibility.VISIBLE,
    expirationData = null,
)
