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

package com.wire.kalium.nomaddevice

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData

/**
 * Nomad-side hook implementation that can be registered into CoreLogic.
 */
public class NomadPersistenceEventHookNotifier(
    private val onPersistedMessage: suspend (PersistedMessageData, UserId) -> Unit = { _, _ -> },
    private val onDeletedMessage: suspend (MessageDeleteEventData, UserId) -> Unit = { _, _ -> },
    private val onPersistedReaction: suspend (ReactionEventData, UserId) -> Unit = { _, _ -> },
    private val onPersistedReadReceipt: suspend (ReadReceiptEventData, UserId) -> Unit = { _, _ -> },
    private val onDeletedConversation: suspend (ConversationDeleteEventData, UserId) -> Unit = { _, _ -> },
    private val onClearedConversation: suspend (ConversationClearEventData, UserId) -> Unit = { _, _ -> },
) : PersistenceEventHookNotifier {
    override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        onPersistedMessage(message, selfUserId)
    }

    override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
        onDeletedMessage(data, selfUserId)
    }

    override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
        onPersistedReaction(data, selfUserId)
    }

    override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
        onPersistedReadReceipt(data, selfUserId)
    }

    override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
        onDeletedConversation(data, selfUserId)
    }

    override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
        onClearedConversation(data, selfUserId)
    }
}
