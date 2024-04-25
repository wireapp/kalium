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

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.time.Duration

class ExpirableCache<K, V>(private val timeToLive: Duration, private val currentTime: CurrentTimeProvider) {
    private val mutex = Mutex()
    private val map = mutableMapOf<K, Pair<Instant, V>>()

    init {
        if (timeToLive.isNegative()) throw IllegalArgumentException("timeToLive must be positive")
    }

    suspend fun getOrPut(key: K, create: suspend () -> V): V = mutex.withLock {
        val currentValue = map[key]?.let { (addedAt, value) ->
            if (addedAt.plus(timeToLive) >= currentTime()) value else null
        }
        currentValue ?: create().also { map[key] = currentTime() to it }
    }
}

typealias CurrentTimeProvider = () -> Instant
