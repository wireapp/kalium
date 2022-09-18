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
