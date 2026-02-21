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
import com.wire.kalium.messaging.hooks.PersistMessageHookNotifier
import com.wire.kalium.messaging.hooks.PersistedMessageData
import kotlin.concurrent.Volatile

internal class MessageHookRegistry : PersistMessageHookNotifier {

    @Volatile
    private var hookNotifier: PersistMessageHookNotifier? = null

    fun register(hookNotifier: PersistMessageHookNotifier) {
        if (this.hookNotifier == null) {
            this.hookNotifier = hookNotifier
        } else {
            error("Hook notifier already registered")
        }
    }

    fun unregister(hookNotifier: PersistMessageHookNotifier) {
        if (this.hookNotifier === hookNotifier) {
            this.hookNotifier = null
        }
    }

    fun clear() {
        hookNotifier = null
    }

    @Suppress("TooGenericExceptionCaught")
    override fun onMessagePersisted(message: PersistedMessageData, selfUserId: UserId) {
        try {
            hookNotifier?.onMessagePersisted(message, selfUserId)
        } catch (throwable: Throwable) {
            kaliumLogger.w("PersistMessage hook execution failed", throwable)
        }
    }
}
