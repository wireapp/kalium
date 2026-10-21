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
import com.wire.kalium.logic.di.ConsumerWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    @Suppress("TooManyFunctions") // The existing account gate owns generation, observation and lifecycle admission together.
    internal class Account<T : Any>(
        private val invalidate: (T) -> Unit,
        private val cancelFailedStartup: (T) -> Unit,
    ) {
        private val state = Lock()
        private val construction = Lock()
        private val lifecycle = Mutex()
        private val changes = MutableStateFlow(0L)
        val revision: StateFlow<Long> get() = changes
        private var closing = false
        private var observationAllowed = true
        private var generation: Generation<T>? = null

        fun get(): T? = state.withLock { generation?.takeIf { !closing && it.ready }?.value }

        /** Cleanup must retain access to the outgoing scope, even after public admission is closed. */
        fun getRaw(): T? = state.withLock { generation?.value }

        fun isCurrent(origin: Any): Boolean = state.withLock { generation?.value === origin }

        fun hasFailedStartup(): Boolean = state.withLock { generation?.failedStartup == true }

        @Suppress("TooGenericExceptionCaught") // Failed startup retains ownership until explicit cleanup succeeds.
        fun getOrCreate(create: () -> T, start: (T) -> Unit, observationRevision: Long? = null): T {
            state.withLock { if (!canOpen(observationRevision)) throw SessionGenerationClosed() }
            // Construction/startup serialize here; cleanup can still invalidate without blocking.
            return construction.withLock {
                val cached = state.withLock {
                    if (!canOpen(observationRevision)) throw SessionGenerationClosed()
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
                        if (!canOpen(observationRevision)) throw SessionGenerationClosed()
                        Generation<T>().also { generation = it }
                    }
                    try {
                        val value = create()
                        val outgoing = state.withLock {
                            // Workers may request logout/preparation as soon as start launches them.
                            // Keep their actual owner private until all synchronous startup succeeds.
                            pending.value = value
                            !canOpen(observationRevision)
                        }
                        if (!outgoing) start(value)
                        val published = state.withLock {
                            val allowed = canOpen(observationRevision)
                            if (allowed) pending.ready = true
                            allowed
                        }
                        if (!published) throw SessionGenerationClosed("The user session generation was invalidated during creation")
                        value
                    } catch (failure: Throwable) {
                        val owner = state.withLock {
                            closing = true
                            pending.failedStartup = true
                            changes.value++
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

        /** Stops admission immediately; cancellation runs outside the state lock. */
        fun invalidate() { invalidate(null) }

        private fun invalidate(origin: Any?): Pair<Long, List<ConsumerWork>>? {
            val snapshot = state.withLock {
                if (origin != null && generation?.value !== origin) return@withLock null
                if (!closing) {
                    closing = true
                    changes.value++
                }
                Triple(changes.value, generation?.value, generation?.observations.orEmpty().toList())
            } ?: return null
            snapshot.third.forEach { it.close() }
            snapshot.second?.let(invalidate)
            return snapshot.first to snapshot.third
        }

        // Call only under state: observers cannot turn a stale acquisition into a manual open.
        private fun canOpen(observationRevision: Long?): Boolean {
            if (closing) return false
            return observationRevision == null || (observationAllowed && changes.value == observationRevision)
        }

        fun isAvailable(revision: Long): Boolean = state.withLock { canOpen(revision) }

        fun admit(revision: Long, value: T): Observation<T>? = state.withLock {
            val current = generation
            if (!observationAllowed) return@withLock null
            if (closing || changes.value != revision || current?.ready != true || current.value !== value) return@withLock null
            val revoked = CancellationException("User session observation revoked")
            val work = ConsumerWork(
                isGenerationActive = { isAvailable(revision) },
                onDrained = { drained -> state.withLock { current.observations.remove(drained) } },
                revocation = revoked,
            )
            current.observations.add(work)
            Observation(value, work, revoked) { isAvailable(revision) }
        }

        /**
         * Revoke/drain observers BEFORE taking the lifecycle/Apple ownership mutex and BEFORE legacy
         * shutdown snapshots its lazy resources. Another transition may have released/reopened while
         * we waited: in that case close and drain the new revision before entering serialization again.
         */
        suspend fun <R : Any> withClosed(
            origin: Any? = null,
            serialize: suspend (suspend () -> R?) -> R? = { action -> withLifecycle { action() } },
            block: suspend (T?) -> R,
        ): R? {
            while (true) {
                val (ticket, observations) = invalidate(origin) ?: return null
                observations.forEach { it.awaitClosed() }
                val result = serialize {
                    val matches = state.withLock {
                        closing && changes.value == ticket && (origin == null || generation?.value === origin)
                    }
                    if (matches) block(close()) else null
                }
                if (result != null) return result
            }
        }

        /** Manual open enables only its ready owner; Apple store enables after its whole transition. */
        fun allowObservation(origin: T? = null) {
            state.withLock {
                val current = generation
                val ready = origin == null || (current?.ready == true && current.value === origin)
                if (!closing && !observationAllowed && ready) {
                    observationAllowed = true
                    changes.value++
                }
            }
        }

        /** Call within [withLifecycle]; includes a constructor that was admitted before invalidation. */
        suspend fun close(): T? {
            invalidate()
            val outgoing = state.withLock { generation }
            outgoing?.constructed?.await()
            return getRaw()?.also(invalidate)
        }

        /** Successful cleanup permits manual open; only successful store may opt observers back in. */
        fun release(allowObservation: Boolean = false) {
            state.withLock {
                generation = null
                closing = false
                observationAllowed = allowObservation
                changes.value++
            }
        }

        // A failure/cancellation leaves closing set and retains the outgoing scope for cleanup.
        suspend fun <R> withLifecycle(block: suspend () -> R): R = lifecycle.withLock { block() }

        private class Generation<T> {
            var value: T? = null
            var ready = false
            var failedStartup = false
            val observations = mutableSetOf<ConsumerWork>()
            val constructed = CompletableDeferred<Unit>()
        }
    }
}

/** Only registry-owned closure is recoverable during acquisition; startup/programming failures escape. */
internal class SessionGenerationClosed(message: String = "The user session generation is closing") : IllegalStateException(message)

internal class Observation<T : Any>(
    val value: T,
    val work: ConsumerWork,
    val revocation: CancellationException,
    val isCurrent: () -> Boolean,
)

/** Stack recovery can copy an exception while retaining the original in its cause chain. */
internal fun Throwable.hasCauseIdentity(expected: Throwable): Boolean {
    val seen = mutableListOf<Throwable>()
    var next: Throwable? = this
    while (next != null) {
        val current = next
        if (current === expected) return true
        if (seen.any { it === current }) break
        seen.add(current)
        next = current.cause
    }
    return false
}
