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

/**
 * Callback manager for clients that need to react to persisted messages without blocking
 * the message persistence flow.
 */
public interface PersistMessageCallbackManager {
    public fun register(callback: PersistMessageCallback)
    public fun unregister(callback: PersistMessageCallback)
}

public data class PersistedMessageData(
    val conversationId: ConversationId,
    val messageId: String,
    val content: MessageContent,
    val date: Instant,
    val expireAfter: Duration?
)

public interface PersistMessageCallback {
    public suspend operator fun invoke(message: PersistedMessageData, selfUserId: UserId)
}

public fun interface PersistMessageHookNotifier {
    public fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId)
}

public object NoOpPersistMessageHookNotifier : PersistMessageHookNotifier {
    override fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) = Unit
}
