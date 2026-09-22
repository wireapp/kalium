/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.removedFromTeamMessage
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.db.MessageEffectDAOs

internal class ReceivedTeamHandler(
    private val data: MessageEffectDAOs,
    private val self: UserId,
    private val persistMessage: PersistMessageUseCase,
) {
    suspend fun leave(event: Event.Team.MemberLeave) {
        val removed = UserId(event.memberId, self.domain)
        // Repeat required user/member effects even when individual system messages exist.
        val affected = data.users.markUserAsDeletedAndRemoveFromGroupConv(removed.toDao())
        affected.forEach { conversation ->
            val message = event.removedFromTeamMessage(self.domain, conversation.toModel(), event.id)
            persistMessage(message).orThrow()
        }
    }
}
