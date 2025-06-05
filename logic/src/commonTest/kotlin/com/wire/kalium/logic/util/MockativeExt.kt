/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.util

import io.mockative.ResultBuilder
import io.mockative.SuspendResultBuilder

/**
 * Sets up the mock to return the given results sequentially. That is,
 * the first call to the mock will return the first value provided in [results], the second
 * call will return the second value, and so on.
 */
fun <R> ResultBuilder<R>.thenReturnSequentially(vararg results: R) {
    var index = -1
    return invokes {
        index += 1
        require(index <= results.lastIndex) {
            "Function called more times than expected. No result set for index $index"
        }
        results[index++]
    }
}

/**
 * Sets up the mock to return the given results sequentially. That is,
 * the first call to the mock will return the first value provided in [results], the second
 * call will return the second value, and so on.
 */
fun <R> SuspendResultBuilder<R>.thenReturnSequentially(vararg results: R) {
    var index = -1
    return invokes {
        index += 1
        require(index <= results.lastIndex) {
            "Function called more times than expected. No result set for index $index"
        }
        results[index]
    }
}
