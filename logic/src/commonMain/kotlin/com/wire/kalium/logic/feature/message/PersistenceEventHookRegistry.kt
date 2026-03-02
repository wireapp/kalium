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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.ConversationDeleteEventData
import com.wire.kalium.messaging.hooks.MessageDeleteEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import com.wire.kalium.messaging.hooks.ReactionEventData
import com.wire.kalium.messaging.hooks.ReadReceiptEventData
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

internal class PersistenceEventHookRegistry : PersistenceEventHookNotifier {

    @Volatile
    private var hookNotifier: PersistenceEventHookNotifier? = null

    fun register(hookNotifier: PersistenceEventHookNotifier) {
        if (this.hookNotifier == null) {
            this.hookNotifier = hookNotifier
        } else {
            error("Hook notifier already registered")
        }
    }

    fun unregister(hookNotifier: PersistenceEventHookNotifier) {
        if (this.hookNotifier === hookNotifier) {
            this.hookNotifier = null
        }
    }

    fun clear() {
        hookNotifier = null
    }

    override suspend fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        safeInvoke("PersistMessage") { it.onMessagePersisted(message, selfUserId) }
    }

    override suspend fun onMessageDeleted(data: MessageDeleteEventData, selfUserId: UserId) {
        safeInvoke("MessageDelete") { it.onMessageDeleted(data, selfUserId) }
    }

    override suspend fun onReactionPersisted(data: ReactionEventData, selfUserId: UserId) {
        safeInvoke("ReactionPersist") { it.onReactionPersisted(data, selfUserId) }
    }

    override suspend fun onReadReceiptPersisted(data: ReadReceiptEventData, selfUserId: UserId) {
        safeInvoke("ReadReceipt") { it.onReadReceiptPersisted(data, selfUserId) }
    }

    override suspend fun onConversationDeleted(data: ConversationDeleteEventData, selfUserId: UserId) {
        safeInvoke("ConversationDelete") { it.onConversationDeleted(data, selfUserId) }
    }

    override suspend fun onConversationCleared(data: ConversationClearEventData, selfUserId: UserId) {
        safeInvoke("ConversationClear") { it.onConversationCleared(data, selfUserId) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend inline fun safeInvoke(tag: String, block: (PersistenceEventHookNotifier) -> Unit) {
        try {
            hookNotifier?.let(block)
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Exception) {
            kaliumLogger.w("$tag hook execution failed", throwable)
        }
    }
}
