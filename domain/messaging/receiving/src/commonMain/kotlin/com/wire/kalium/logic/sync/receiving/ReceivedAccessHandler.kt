/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.appsAccessChangedMessage
import com.wire.kalium.logic.data.conversation.toDAO
import com.wire.kalium.logic.data.conversation.updateConversationAccess
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.matchesSystemMessage
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.MessageEffectDAOs
import kotlin.time.Clock

internal class ReceivedAccessHandler(
    private val data: MessageEffectDAOs,
    private val persistMessage: PersistMessageUseCase,
) {
    suspend fun previous(event: Event.Conversation.AccessUpdate): MessageEntity? =
        data.messages.getMessageById(event.id, event.conversationId.toDao())?.also { existing ->
            val expected = appsAccessChangedMessage(event.id, event.conversationId, event.qualifiedFrom, event.hasApps()).toEntity()
            check(existing.matchesSystemMessage(expected.senderUserId, expected.content)) {
                "Apps-access message identity conflicts"
            }
        }

    suspend fun handle(
        event: Event.Conversation.AccessUpdate,
        previous: MessageEntity?,
    ) {
        val old = updateConversationAccess(
            data.conversations,
            event.conversationId,
            event.access.map { it.toDAO() },
            event.accessRole.map { it.toDAO() }
        )
        val hadApps = old?.accessRole?.contains(ConversationEntity.AccessRole.SERVICE) == true
        if (hadApps != event.hasApps() || previous != null) {
            persistMessage(
                appsAccessChangedMessage(
                    event.id,
                    event.conversationId,
                    event.qualifiedFrom,
                    event.hasApps(),
                    previous?.date ?: Clock.System.now()
                )
            ).orThrow()
        }
    }
}

private fun Event.Conversation.AccessUpdate.hasApps() = Conversation.AccessRole.SERVICE in accessRole
