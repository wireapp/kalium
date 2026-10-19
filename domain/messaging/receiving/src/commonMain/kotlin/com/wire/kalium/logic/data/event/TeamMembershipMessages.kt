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

fun Event.Team.MemberLeave.removedFromTeamMessage(selfDomain: String, conversationId: ConversationId, messageId: String): Message.System {
    val removedUser = UserId(memberId, selfDomain)
    return Message.System(
        id = messageId,
        content = MessageContent.MemberChange.RemovedFromTeam(listOf(removedUser)),
        conversationId = conversationId,
        date = dateTime,
        senderUserId = removedUser,
        status = Message.Status.Sent,
        visibility = Message.Visibility.VISIBLE,
        expirationData = null
    )
}
