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

package com.wire.kalium.messaging.hooks

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant
import kotlin.time.Duration

public data class PersistedMessageData(
    val conversationId: ConversationId,
    val messageId: String,
    val content: MessageContent,
    val date: Instant,
    val expireAfter: Duration?
)

public data class MessageDeleteEventData(
    val conversationId: ConversationId,
    val messageId: String
)

public data class ReactionEventData(
    val conversationId: ConversationId,
    val messageId: String,
    val date: Instant
)

public data class ReadReceiptEventData(
    val conversationId: ConversationId,
    val messageIds: List<String>,
    val date: Instant
)

public data class ConversationDeleteEventData(
    val conversationId: ConversationId
)

public data class ConversationClearEventData(
    val conversationId: ConversationId
)

public interface PersistenceEventHookNotifier {
    public suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {}
    public suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {}
    public suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {}
    public suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {}
    public suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {}
    public suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {}
}

public object NoOpPersistenceEventHookNotifier : PersistenceEventHookNotifier
