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

package com.wire.kalium.persistence.util

import kotlinx.coroutines.delay

internal fun Throwable.isSqliteBusy(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val message = current.message.orEmpty()
        if (message.contains("SQLITE_BUSY") || message.contains("database is locked")) {
            return true
        }
        current = current.cause
    }
    return false
}

internal fun sqliteBusyRetryDelayMs(attempt: Long): Long {
    val boundedAttempt = attempt.coerceIn(0, 6)
    return 100L * (1L shl boundedAttempt.toInt())
}

internal suspend inline fun <T> sqliteBusyRetry(
    maxRetries: Int = 5,
    crossinline block: suspend () -> T,
): T {
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (throwable: Throwable) {
            if (!throwable.isSqliteBusy() || attempt >= maxRetries) {
                throw throwable
            }
            delay(sqliteBusyRetryDelayMs(attempt.toLong()))
            attempt++
        }
    }
}
