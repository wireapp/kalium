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
@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package com.wire.kalium.persistence.db

import com.wire.kalium.persistence.kaliumLogger
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.concurrent.atomics.decrementAndFetch
import kotlin.concurrent.atomics.update

/**
 * Controls SQLDelight query invalidations (`notifyListeners`).
 *
 * Purpose:
 * SQLDelight triggers `notifyListeners()` after most write operations,
 * which can cause excessive recompositions, Flow emissions and cache invalidations
 * when processing large batches of events.
 *
 * This controller allows temporarily *muting* invalidations and aggregating them:
 *
 * - While muted, all `notifyListeners` calls are intercepted.
 * - Query keys are accumulated and deduplicated.
 * - When the outermost mute scope finishes, all accumulated keys are flushed
 *   in a single `notifyListeners` call.
 *
 * This significantly reduces UI / Flow churn during batch processing
 * (e.g. Incremental Sync).
 */
class DbInvalidationController(
    private val enabled: Boolean,
    private val notifyKey: (String) -> Unit,
) {
    /**
     * Nesting depth of the mute scope.
     *
     * - `> 0` means invalidations are currently muted.
     * - Supports re-entrant usage (nested `runMuted {}` calls).
     * - Only when the depth returns to `0` are accumulated keys flushed.
     */
    private val muteDepth = AtomicInt(0)

    /**
     * Total number of `notifyListeners` calls intercepted by this controller.
     *
     * This counts *all* invalidation attempts coming from SQLDelight,
     * regardless of whether they were muted or passed through.
     *
     * Useful for understanding how "chatty" the database layer is.
     */
    private val notifyCalls = AtomicInt(0)

    /**
     * Total number of query keys received while muted.
     *
     * Each call to `notifyListeners(vararg queryKeys)` may contain multiple keys.
     * This counter reflects the *raw volume* of potential invalidations
     * before deduplication.
     *
     * Example:
     * - 300 notify calls
     * - each touching ~5 tables
     * -> notifyKeysTotal ≈ 1500
     */
    private val notifyKeysTotal = AtomicInt(0)

    /**
     * Number of `notifyListeners` calls that were muted
     * (i.e. intercepted and not forwarded immediately).
     *
     * High ratio of `notifyMutedCalls / notifyCalls`
     * indicates effective batching and reduced UI churn.
     */
    private val notifyMutedCalls = AtomicInt(0)

    // Accumulated keys while muted. We keep an immutable Set to update lock-free.
    private val pendingKeys = AtomicReference<Set<String>>(emptySet())

    fun isEnabled(): Boolean = enabled
    fun isMuted(): Boolean = enabled && muteDepth.load() > 0

    /**
     * Runs [block] while invalidations are muted.
     * Safe for nesting (re-entrant via muteDepth).
     */
    suspend fun <T> runMuted(block: suspend () -> T): T {
        if (!enabled) return block()

        enter()
        return try {
            block()
        } finally {
            exitAndFlushIfNeeded()
        }
    }

    internal fun onNotify(queryKeys: Array<out String>) {
        if (queryKeys.isEmpty()) return

        if (!enabled) {
            queryKeys.forEach(notifyKey)
            return
        }

        notifyCalls.incrementAndFetch()
        notifyKeysTotal.addAndFetch(queryKeys.size)
        if (isMuted()) notifyMutedCalls.incrementAndFetch()

        if (isMuted()) {
            pendingKeys.update { current ->
                var acc = current
                for (k in queryKeys) acc = acc + k
                acc
            }
        } else {
            queryKeys.forEach(notifyKey)
        }
    }

    private fun enter() {
        muteDepth.incrementAndFetch()
    }

    private fun exitAndFlushIfNeeded() {
        val depth = muteDepth.decrementAndFetch()
        if (depth != 0) return

        val calls = notifyCalls.exchange(0)
        val mutedCalls = notifyMutedCalls.exchange(0)
        val keysTotal = notifyKeysTotal.exchange(0)

        val keysToFlush = pendingKeys.exchange(emptySet())
        kaliumLogger.d(
            "[DbInvalidationController][mute] DONE " +
                    "notifyCalls=$calls " +
                    "mutedCalls=$mutedCalls " +
                    "keysTotal=$keysTotal " +
                    "flushKeys=${keysToFlush.size}"
        )

        if (keysToFlush.isNotEmpty()) {
            keysToFlush.forEach(notifyKey)
        }
    }
}
