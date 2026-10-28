/* Wire, Copyright (C) 2026 Wire Swiss GmbH. SPDX-License-Identifier: GPL-3.0-only */
package com.wire.kalium.logic.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Account-owned wake-up for retry waits. It neither adds sync demand nor starts concurrent attempts. */
internal class SyncRetryController {
    private val mutex = Mutex()
    private var generation = 0L
    private val waiters = mutableSetOf<CompletableDeferred<Unit>>()

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
