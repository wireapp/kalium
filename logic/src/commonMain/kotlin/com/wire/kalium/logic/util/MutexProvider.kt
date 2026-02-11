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
package com.wire.kalium.logic.util

import co.touchlab.stately.collections.ConcurrentMutableMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Creates a mutex associated with a key and locks it so that there's only one execution going for a given key at a given time.
 * When the lock for the given key is executed for the first time, it will create a new entry in the map and lock the mutex.
 * When another lock is executed for the same key while it's locked, it will increase the count and lock the mutex so that it'll wait
 * and execute after the first execution unlocks the mutex. The count is there to keep the mutex in the map as long as it's needed.
 * After the last unlock, the mutex is removed from the map.
 */
internal class MutexProvider<K> {
    private val mutexMap = ConcurrentMutableMap<K, CountedMutex>()

    // for testing purposes
    fun doesLockCurrentlyExist(key: K): Boolean = mutexMap.containsKey(key)

    suspend fun <T> withLock(
        key: K,
        onWaitingToUnlock: () -> Unit = {},
        action: suspend () -> T
    ): T =
        increaseCountAndGetMutex(key).let { (_, mutex) ->
            if (mutex.isLocked) onWaitingToUnlock()
            mutex.withLock {
                try {
                    action()
                } finally {
                    decreaseCountAndRemoveMutexIfNeeded(key)
                }
            }
        }

    private fun increaseCountAndGetMutex(key: K): CountedMutex =
        mutexMap.block { mutexMap ->
            ((mutexMap[key] ?: CountedMutex())).let { countedMutex ->
                countedMutex.copy(count = countedMutex.count + 1).also {
                    mutexMap.put(key, it)
                }
            }
        }

    private fun decreaseCountAndRemoveMutexIfNeeded(key: K) {
        mutexMap.block { mutexMap ->
            mutexMap[key]?.let { countedMutex ->
                when {
                    countedMutex.count <= 1 -> mutexMap.remove(key)
                    else -> mutexMap.put(key, countedMutex.copy(count = countedMutex.count - 1))
                }
            }
        }
    }
}

private data class CountedMutex(val count: Int = 0, val mutex: Mutex = Mutex())
