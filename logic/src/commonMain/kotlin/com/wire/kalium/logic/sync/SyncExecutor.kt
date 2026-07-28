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
package com.wire.kalium.logic.sync

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

public abstract class SyncExecutor {

    public abstract fun startAndStopSyncAsNeeded()

    /** Cancels the active sync lifecycle and returns only after its work has fully unwound. */
    public abstract suspend fun stopAndWait()

    /**
     * Requests Sync to be performed, fetching new events, etc. bringing the user to an online status.
     * At the end of [executorAction], the request is released.
     *
     * Sync will keep ongoing if at least one request is still active (not released).
     */
    public abstract suspend fun <T> request(executorAction: suspend SyncRequest.() -> T): T

    internal inner class Request internal constructor(
        private val syncStateFlow: StateFlow<SyncState>,
        private val job: Job,
        private val logger: KaliumLogger
    ) : SyncRequest {

        private var isEndless = false

        /**
         * Releases/Stops the Sync Request.
         * Sync will continue ongoing / the device will keep attempting to stay online while at least one [Request] is still ongoing.
         */
        internal fun release() {
            if (isEndless) {
                logger.w("Sync request was marked as endless, so it was not released and will keep running. Following the Sync Scope.")
                return
            }
            job.cancel()
        }

        override suspend fun waitUntilOrFailure(
            syncState: SyncState
        ): SyncRequestResult = syncStateFlow.map { state ->
            when (state) {
                is SyncState.Failed -> SyncRequestResult.Failure(state.cause)
                syncState -> SyncRequestResult.Success
                else -> null
            }
        }.filterNotNull().first()

        override suspend fun waitUntilLiveOrFailure(): SyncRequestResult = waitUntilOrFailure(SyncState.Live)

        override fun keepSyncAlwaysOn() {
            isEndless = true
        }
    }
}

@OptIn(ExperimentalAtomicApi::class)
internal class SyncExecutorImpl(
    private val syncStateObserver: SyncStateObserver,
    private val slowSyncManager: SlowSyncManager,
    private val incrementalSyncManager: IncrementalSyncManager,
    private val scope: CoroutineScope,
    userScopedLogger: KaliumLogger = kaliumLogger,
) : SyncExecutor() {

    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Waiting)
    private val logger by lazy { userScopedLogger.withFeatureId(SYNC).withTextTag("SyncExecutor") }
    private val syncLifecycleState = AtomicReference<SyncLifecycleState>(
        SyncLifecycleState.Stopped
    )

    override fun startAndStopSyncAsNeeded() {
        startSyncLifecycle()
    }

    override suspend fun stopAndWait(): Unit = withContext(NonCancellable) {
        stopSyncLifecycle()
    }

    private fun startSyncLifecycle() {
        while (true) {
            when (val state = syncLifecycleState.load()) {
                SyncLifecycleState.Stopped -> {
                    val job = createSyncLifecycleJob()
                    val running = SyncLifecycleState.Running(job)
                    job.invokeOnCompletion {
                        syncLifecycleState.compareAndSet(running, SyncLifecycleState.Stopped)
                    }
                    if (
                        syncLifecycleState.compareAndSet(
                            state,
                            running
                        )
                    ) {
                        job.start()
                        return
                    }
                    job.cancel()
                }

                is SyncLifecycleState.Running -> {
                    return
                }

                is SyncLifecycleState.Stopping -> {
                    state.startRequested.store(true)
                    if (syncLifecycleState.load() === state) return
                }
            }
        }
    }

    private suspend fun stopSyncLifecycle() {
        while (true) {
            when (val state = syncLifecycleState.load()) {
                SyncLifecycleState.Stopped -> return
                is SyncLifecycleState.Running -> {
                    val stopping = SyncLifecycleState.Stopping(
                        job = state.job,
                        startRequested = AtomicBoolean(false)
                    )
                    if (syncLifecycleState.compareAndSet(state, stopping)) {
                        state.job.cancelAndJoin()
                        finishStopping(stopping)
                        return
                    }
                }

                is SyncLifecycleState.Stopping -> {
                    state.job.cancelAndJoin()
                    finishStopping(state)
                    return
                }
            }
        }
    }

    private fun finishStopping(stopping: SyncLifecycleState.Stopping) {
        while (syncLifecycleState.load() === stopping) {
            if (stopping.startRequested.load()) {
                val job = createSyncLifecycleJob()
                val running = SyncLifecycleState.Running(job)
                job.invokeOnCompletion {
                    syncLifecycleState.compareAndSet(running, SyncLifecycleState.Stopped)
                }
                if (
                    syncLifecycleState.compareAndSet(
                        stopping,
                        running
                    )
                ) {
                    job.start()
                    return
                }
                job.cancel()
            } else if (
                syncLifecycleState.compareAndSet(
                    stopping,
                    SyncLifecycleState.Stopped
                )
            ) {
                return
            }
        }
    }

    private fun createSyncLifecycleJob(): Job = scope.launch(start = CoroutineStart.LAZY) {
        coroutineScope {
            launch {
                syncStateObserver.syncState.collect { syncStateFlow.value = it }
            }
            launch {
                syncStateFlow.subscriptionCount
                    .onEach {
                        logger.d("!! Sync requester count changed to $it")
                    }
                    .map { count -> count > 0 }
                    .distinctUntilChanged()
                    .collectLatest { shouldSync ->
                        if (shouldSync) {
                            logger.i("!! Starting Sync to fulfill requests !!")
                            performSync()
                        } else {
                            logger.i("!! Stopping sync, as there are no requests for it. !!")
                        }
                    }
            }
        }
    }

    private suspend fun performSync() {
        slowSyncManager.performSyncFlow()
            .cancellable()
            .collectLatest { slowSyncState ->
                if (slowSyncState == SlowSyncStatus.Complete) {
                    incrementalSyncManager.performSyncFlow()
                        .cancellable()
                        .collect()
                }
            }
    }

    /**
     * Launches and returns a SyncRequest, making sure sync attempts to stay live / "online".
     * The caller should eventually cancel the returned SyncRequest, in order to go offline.
     *
     * Sync will continue ongoing / will keep attempting to stay online while at least one [Request] is still active.
     *
     */
    private fun startNewSyncRequest(): Request {
        val syncJob = scope.launch {
            syncStateFlow.collect { state ->
                awaitCancellation()
            }
        }
        return Request(syncStateFlow, syncJob, logger)
    }

    override suspend fun <T> request(
        requestAction: suspend SyncRequest.() -> T
    ): T = coroutineScope {
        val request = startNewSyncRequest()
        val result = async {
            request.requestAction()
        }
        result.invokeOnCompletion {
            request.release()
        }
        result.await()
    }
}

@OptIn(ExperimentalAtomicApi::class)
private sealed interface SyncLifecycleState {
    data object Stopped : SyncLifecycleState
    class Running(val job: Job) : SyncLifecycleState
    class Stopping(
        val job: Job,
        val startRequested: AtomicBoolean
    ) : SyncLifecycleState
}
