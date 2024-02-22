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

package com.wire.kalium.logic.functional

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow

suspend inline fun <A, B> Collection<A>.flatMapFromIterable(
    crossinline block: suspend (A) -> Flow<B>
): Flow<List<B>> = flow {
    val result = mutableListOf<B>()

    if (isEmpty()) emit(result)

    forEach { a -> result.add(block(a).first()) }

    emit(result)
}

fun <T1, T2> Flow<T1>.combine(flow: Flow<T2>): Flow<Pair<T1, T2>> = combine(flow) { t1, t2 -> t1 to t2 }

fun <T> Flow<List<T>>.flatten() = flatMapConcat { it.asFlow() }

fun <T> Flow<T>.distinct(): Flow<T> {
    val past = mutableSetOf<T>()
    return filter { past.add(it) }
}

fun intervalFlow(periodMs: Long, initialDelayMs: Long = 0L, stopWhen: () -> Boolean = { false }) =
    flow {
        delay(initialDelayMs)
        while (!stopWhen()) {
            emit(Unit)
            delay(periodMs)
        }
    }
