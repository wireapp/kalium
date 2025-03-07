/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.common.functional

import kotlinx.coroutines.delay

suspend fun <L, R> retry(
    times: Int = Int.MAX_VALUE,
    delay: Long = 0,
    maxDelay: Long = 1000,
    factor: Double = 1.0,
    action: suspend () -> Either<L, R>
): Either<L, R> {

    var currentDelay = delay

    repeat(times - 1) {
        action()
            .onSuccess { return it.right() }
            .onFailure {
                delay(delay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
            }
    }

    return action()
}
