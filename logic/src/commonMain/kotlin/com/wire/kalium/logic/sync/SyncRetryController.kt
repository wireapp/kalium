/* Wire, Copyright (C) 2026 Wire Swiss GmbH. SPDX-License-Identifier: GPL-3.0-only */
package com.wire.kalium.logic.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/** Account-owned wake-up and delay limits for retry waits. It neither adds sync demand nor starts concurrent attempts. */
internal class SyncRetryController {
    private val mutex = Mutex()
    private var generation = 0L
    private val waiters = mutableSetOf<CompletableDeferred<Unit>>()

    // Identity keys, so two equal limits are released independently.
    private class RetryDelayLimit(val maxDelay: Duration)
    private val retryDelayLimits = MutableStateFlow<Set<RetryDelayLimit>>(emptySet())

    /** Caps the delay of retries scheduled from now on at [maxDelay], until the returned handle is disposed. */
    fun limitRetryDelay(maxDelay: Duration): DisposableHandle {
        val limit = RetryDelayLimit(maxDelay)
        retryDelayLimits.update { it + limit }
        return DisposableHandle { retryDelayLimits.update { it - limit } }
    }

    /** Returns [delay], shortened to the smallest active limit. */
    fun capRetryDelay(delay: Duration): Duration =
        retryDelayLimits.value.minOfOrNull { it.maxDelay }?.let { delay.coerceAtMost(it) } ?: delay

    suspend fun generation(): Long = mutex.withLock { generation }

    suspend fun retryNow(resetBackoff: () -> Unit): Unit = mutex.withLock {
        resetBackoff()
        generation++
        waiters.forEach { it.complete(Unit) }
    }

    suspend fun awaitRetry(
        failureGeneration: Long,
        onRegistered: suspend () -> Unit,
        waitForNetworkOrTimeout: suspend () -> Boolean,
    ): RetryTrigger = coroutineScope {
        val signal = CompletableDeferred<Unit>()
        mutex.withLock {
            if (failureGeneration != generation) signal.complete(Unit)
            waiters.add(signal)
        }
        try {
            onRegistered()
            val network = async(start = CoroutineStart.LAZY) { waitForNetworkOrTimeout() }
            try {
                select {
                    signal.onAwait { RetryTrigger.USER_REQUESTED }
                    network.onAwait { if (it) RetryTrigger.NETWORK_RECONNECTED else RetryTrigger.RETRY_DELAY_ELAPSED }
                }
            } finally {
                network.cancel()
            }
        } finally {
            withContext(NonCancellable) { mutex.withLock { waiters.remove(signal) } }
        }
    }
}

internal enum class RetryTrigger { USER_REQUESTED, NETWORK_RECONNECTED, RETRY_DELAY_ELAPSED }
