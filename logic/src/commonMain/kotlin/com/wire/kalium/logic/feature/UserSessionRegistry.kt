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

import co.touchlab.stately.collections.ConcurrentMutableMap
import co.touchlab.stately.concurrency.Lock
import co.touchlab.stately.concurrency.withLock
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Retains per-qualified-account gates across generations, without changing provider cache ownership. */
internal class UserSessionRegistry<T : Any>(
    private val invalidate: (T) -> Unit,
    private val cancelFailedStartup: (T) -> Unit,
) {
    private val accounts = ConcurrentMutableMap<UserId, Account<T>>()

    fun account(userId: UserId): Account<T> = accounts.computeIfAbsent(userId) { Account(invalidate, cancelFailedStartup) }

    internal class Account<T : Any>(
        private val invalidate: (T) -> Unit,
        private val cancelFailedStartup: (T) -> Unit,
    ) {
        private val state = Lock()
        private val construction = Lock()
        private val lifecycle = Mutex()
        private var closing = false
        private var generation: Generation<T>? = null

        fun get(): T? = state.withLock { generation?.takeIf { !closing && it.ready }?.value }

        /** Cleanup must retain access to the outgoing scope, even after public admission is closed. */
        fun getRaw(): T? = state.withLock { generation?.value }

        fun isCurrent(origin: Any): Boolean = state.withLock { generation?.value === origin }

        fun hasFailedStartup(): Boolean = state.withLock { generation?.failedStartup == true }

        @Suppress("TooGenericExceptionCaught") // Failed startup retains ownership until explicit cleanup succeeds.
        fun getOrCreate(create: () -> T, start: (T) -> Unit): T {
            state.withLock { check(!closing) { "The user session generation is closing" } }
            // Construction/startup serialize here; cleanup can still invalidate without blocking.
            return construction.withLock {
                val cached = state.withLock {
                    check(!closing) { "The user session generation is closing" }
                    generation?.let {
                        // A reentrant request from synchronous startup must not create another owner.
                        check(it.ready) { "The user session generation is still starting" }
                        it.value
                    }
                }
                if (cached != null) {
                    cached
                } else {
                    val pending = state.withLock {
                        check(!closing) { "The user session generation is closing" }
                        Generation<T>().also { generation = it }
                    }
                    try {
                        val value = create()
                        val outgoing = state.withLock {
                            // Workers may request logout/preparation as soon as start launches them.
                            // Keep their actual owner private until all synchronous startup succeeds.
                            pending.value = value
                            closing
                        }
                        if (!outgoing) start(value)
                        val published = state.withLock {
                            if (!closing) pending.ready = true
                            !closing
                        }
                        check(published) { "The user session generation was invalidated during creation" }
                        value
                    } catch (failure: Throwable) {
                        val owner = state.withLock {
                            closing = true
                            pending.failedStartup = true
                            pending.value
                        }
                        owner?.let {
                            invalidate(it)
                            cancelFailedStartup(it)
                        }
                        throw failure
                    } finally {
                        // Cleanup waits for both construction and synchronous startup to unwind.
                        pending.constructed.complete(Unit)
                    }
                }
            }
        }

        /** Stops admission immediately; does not resolve any lazy scope or resource. */
        fun invalidate() {
            val outgoing = state.withLock {
                closing = true
                generation?.value
            }
            outgoing?.let(invalidate)
        }

        /** Call within [withLifecycle]; includes a constructor that was admitted before invalidation. */
        suspend fun close(): T? {
            invalidate()
            val outgoing = state.withLock { generation }
            outgoing?.constructed?.await()
            return getRaw()?.also(invalidate)
        }

        /** Call only after ALL identity-keyed provider removals and session mutations succeed. */
        fun release() {
            state.withLock {
                generation = null
                closing = false
            }
        }

        // A failure/cancellation leaves closing set and retains the outgoing scope for cleanup.
        suspend fun <R> withLifecycle(block: suspend () -> R): R = lifecycle.withLock { block() }

        private class Generation<T> {
            var value: T? = null
            var ready = false
            var failedStartup = false
            val constructed = CompletableDeferred<Unit>()
        }
    }
}
