@file:OptIn(com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi::class)
@file:Suppress("TooGenericExceptionCaught")

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.wire.kalium.logic.service

import com.wire.kalium.logic.service.api.ExperimentalKaliumServiceApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Identity-local durable scheduler matching the client's first-proposal timer semantics. */
@ExperimentalKaliumServiceApi
public class WirePendingMlsCommitScheduler(
    private val crypto: WireServiceCryptoRuntime,
    private val store: PendingMlsCommitStore,
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
    private val onFatalFailure: suspend (EncryptedServiceStateResult.Failure) -> Unit = {},
) {
    private val lifecycleMutex = Mutex()
    private val operationMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var worker: Job? = null
    private var closed: Boolean = false
    private var fatalFailure: EncryptedServiceStateResult.Failure? = null

    public suspend fun start(): EncryptedServiceStateResult<Unit> = lifecycleMutex.withLock {
        if (closed) return@withLock EncryptedServiceStateResult.Failure("Pending MLS commit scheduler is closed")
        if (worker != null) return@withLock EncryptedServiceStateResult.Success(Unit)
        when (val due = processDue()) {
            is EncryptedServiceStateResult.Failure -> return@withLock due
            is EncryptedServiceStateResult.Success -> Unit
        }
        worker = scope.launch { runScheduler() }
        EncryptedServiceStateResult.Success(Unit)
    }

    public suspend fun schedule(groupId: String, commitAtEpochSeconds: Long): EncryptedServiceStateResult<Unit> =
        operationMutex.withLock {
            if (closed) return@withLock EncryptedServiceStateResult.Failure("Pending MLS commit scheduler is closed")
            fatalFailure?.let { return@withLock it }
            when (val scheduled = store.schedulePendingMlsCommit(groupId, commitAtEpochSeconds)) {
                is EncryptedServiceStateResult.Failure -> scheduled
                is EncryptedServiceStateResult.Success -> {
                    wake.trySend(Unit)
                    EncryptedServiceStateResult.Success(Unit)
                }
            }
        }

    public suspend fun cancel(groupId: String): EncryptedServiceStateResult<Unit> = operationMutex.withLock {
        if (closed) return@withLock EncryptedServiceStateResult.Failure("Pending MLS commit scheduler is closed")
        fatalFailure?.let { return@withLock it }
        val deadline = when (val pending = store.loadPendingMlsCommits()) {
            is EncryptedServiceStateResult.Failure -> return@withLock pending
            is EncryptedServiceStateResult.Success -> pending.value[groupId]
        } ?: return@withLock EncryptedServiceStateResult.Success(Unit)
        store.removePendingMlsCommit(groupId, deadline)
    }

    public suspend fun close(): EncryptedServiceStateResult<Unit> = lifecycleMutex.withLock {
        if (closed) return@withLock EncryptedServiceStateResult.Success(Unit)
        closed = true
        worker?.cancelAndJoin()
        worker = null
        wake.close()
        scope.cancel()
        EncryptedServiceStateResult.Success(Unit)
    }

    private suspend fun runScheduler() {
        while (currentCoroutineContext().isActive) {
            val pending = when (val result = store.loadPendingMlsCommits()) {
                is EncryptedServiceStateResult.Failure -> {
                    latchFailure(result)
                    return
                }
                is EncryptedServiceStateResult.Success -> result.value
            }
            val next = pending.values.minOrNull()
            if (next == null) {
                wake.receive()
                continue
            }
            val waitMillis = ((next - currentEpochSeconds()).coerceAtLeast(0) * MILLIS_PER_SECOND)
                .coerceAtMost(MAX_WAIT_MILLIS)
            val woken = withTimeoutOrNull(waitMillis.coerceAtLeast(1)) {
                wake.receive()
                true
            } ?: false
            if (!woken) {
                val result = processDue()
                if (result is EncryptedServiceStateResult.Failure) {
                    latchFailure(result)
                    return
                }
            }
        }
    }

    private suspend fun processDue(): EncryptedServiceStateResult<Unit> = operationMutex.withLock {
        val pending = when (val result = store.loadPendingMlsCommits()) {
            is EncryptedServiceStateResult.Failure -> return@withLock result
            is EncryptedServiceStateResult.Success -> result.value
        }
        pending.entries.sortedBy { it.value }.forEach { (groupId, deadline) ->
            if (deadline > currentEpochSeconds()) return@forEach
            try {
                crypto.withMls("service-commit-pending-proposals") { context ->
                    context.commitPendingProposals(groupId)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Throwable) {
                return@withLock EncryptedServiceStateResult.Failure(
                    "Unable to commit pending MLS proposals for the scheduled group",
                    failure,
                )
            }
            when (val removed = store.removePendingMlsCommit(groupId, deadline)) {
                is EncryptedServiceStateResult.Failure -> return@withLock removed
                is EncryptedServiceStateResult.Success -> Unit
            }
        }
        EncryptedServiceStateResult.Success(Unit)
    }

    private suspend fun latchFailure(failure: EncryptedServiceStateResult.Failure) {
        operationMutex.withLock { fatalFailure = failure }
        onFatalFailure(failure)
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val MAX_WAIT_MILLIS = 60_000L
    }
}
