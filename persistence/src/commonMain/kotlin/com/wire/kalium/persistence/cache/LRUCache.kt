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

package com.wire.kalium.persistence.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface Cache<Key : Any, Value> {
    suspend fun get(key: Key, producer: suspend (key: Key) -> Value): Value
    suspend fun remove(key: Key)
}

internal class LRUCache<Key : Any, Value : Any>(
    private val maxSize: Int
) : Cache<Key, Value> {

    init {
        require(maxSize > 0) { "Can't initialize a LRUCache with a negative max size" }
    }

    private val mutex = Mutex()

    private val storage = hashMapOf<Key, Value>()

    /**
     * Stores the order in which keys to access values were used.
     * The first value contains the key accessed the longest ago, while
     * the last value contains the most recently accessed key.
     */
    private val keyAccessLogbook = mutableSetOf<Key>()

    override suspend fun get(key: Key, producer: suspend (key: Key) -> Value): Value =
        mutex.withLock {
            val value = storage.getOrPut(key) { producer(key) }
            recordAccess(key)
            while (storage.size > maxSize) {
                val leastRecentlyAccessedKey = keyAccessLogbook.first()
                storage.remove(leastRecentlyAccessedKey)
                keyAccessLogbook.remove(leastRecentlyAccessedKey)
            }
            value
        }

    private fun recordAccess(key: Key) {
        keyAccessLogbook.insertOrMoveToLastPosition(key)
    }

    override suspend fun remove(key: Key): Unit = mutex.withLock {
        storage.remove(key)
    }

    /**
     * Adds or reorders an element to the set,
     * putting it into the last position.
     */
    private fun <T> MutableSet<T>.insertOrMoveToLastPosition(element: T) {
        remove(element)
        add(element)
    }
}
