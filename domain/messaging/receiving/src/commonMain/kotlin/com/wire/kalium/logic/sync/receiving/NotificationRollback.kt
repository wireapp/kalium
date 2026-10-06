/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.cryptography.RequireConfirmedCryptoRollback
import com.wire.kalium.cryptography.exceptions.ProteusException
import kotlinx.coroutines.withContext

internal suspend fun <T> withConfirmedNotificationRollback(block: suspend () -> T): T =
    withContext(RequireConfirmedCryptoRollback) {
        try {
            block()
        } catch (failure: ProteusException) {
            // ProteusClient wraps non-Proteus callback errors exactly once. Do not traverse
            // arbitrary cause chains: a native failure must never become a local deferral.
            val callback = failure.cause
            val wrappedCallback = failure.code == ProteusException.Code.UNKNOWN_ERROR && failure.intCode == null
            if (wrappedCallback && callback is DeferredNotification) throw callback
            if (wrappedCallback && callback is NotificationOrderingBarrier) throw callback
            if (wrappedCallback && callback is NotificationAppHandoff) throw callback
            throw failure
        }
    }
