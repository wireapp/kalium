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

import co.touchlab.stately.concurrency.AtomicReference
import co.touchlab.stately.concurrency.value
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.messaging.hooks.CryptoStateChangeHookNotifier
import kotlin.coroutines.cancellation.CancellationException

internal class CryptoStateChangeHookRegistry : CryptoStateChangeHookNotifier {

    private val hookNotifier = AtomicReference<CryptoStateChangeHookNotifier?>(null)

    fun register(hookNotifier: CryptoStateChangeHookNotifier) {
        val success = this.hookNotifier.compareAndSet(null, hookNotifier)
        if (!success) {
            error("Hook notifier already registered")
        }
    }

    fun unregister(hookNotifier: CryptoStateChangeHookNotifier) {
        this.hookNotifier.compareAndSet(hookNotifier, null)
    }

    fun clear() {
        hookNotifier.value = null
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun onCryptoStateChanged(userId: UserId) {
        try {
            hookNotifier.value?.onCryptoStateChanged(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Exception) {
            kaliumLogger.w("Crypto state change hook execution failed", throwable)
        }
    }
}
