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
package com.wire.kalium.logic.feature

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Waits for [acquire] until [revoked] returns, and returns null if it did first. What [acquire] wins but cannot reach
 * the caller, because the caller was cancelled or [revoked] returned at that moment, goes to [releaseWon]: an account's
 * lock must never be left held with nobody tracking it.
 */
internal suspend fun <T : Any> acquireUnlessRevoked(
    acquire: suspend () -> T,
    releaseWon: (T) -> Unit,
    revoked: suspend () -> Unit,
): T? {
    // Set where it is won, read only after every child has finished.
    var won: T? = null
    var handedOver = false
    try {
        val result = coroutineScope {
            val waiting = async { acquire().also { won = it } }
            val revocation = launch {
                revoked()
                waiting.cancel()
            }
            try {
                waiting.await()
            } catch (_: CancellationException) {
                currentCoroutineContext().ensureActive()
                null
            } finally {
                revocation.cancel()
            }
        }
        handedOver = result != null
        return result
    } finally {
        if (!handedOver) won?.let(releaseWon)
    }
}
