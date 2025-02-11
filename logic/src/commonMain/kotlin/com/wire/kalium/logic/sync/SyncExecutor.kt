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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
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
import kotlinx.coroutines.launch

abstract class SyncExecutor internal constructor() {

    abstract fun startAndStopSyncAsNeeded()

    /**
     * Requests Sync to be performed, fetching new events, etc. bringing the user to an online status.
     * At the end of [executorAction], the request is released.
     *
     * Sync will keep ongoing if at least one request is still active (not released).
     */
    abstract suspend fun <T> request(executorAction: suspend Request.() -> T): T

    inner class Request internal constructor(
        private val syncStateFlow: StateFlow<SyncState>,
        private val job: Job,
        private val logger: KaliumLogger
    ) {

        /**
         * Indicates whether the sync request will run indefinitely.
         *
         * When set to `true`, the sync process continues without being automatically released,
         * ensuring synchronization persists as long as the sync scope lives. This is particularly useful
         * for services that do not care about the lifecycle, like TestService, CLI, etc.
         *
         * **Usage:**
         * - If `true`, the synchronization process will remain active unless explicitly stopped.
         * - If `false`, the request follows its standard lifecycle and can be released normally.
         */

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

        /**
         * Suspends execution until the specified [syncState] is reached or a failure occurs.
         *
         * @see
         * @param syncState The desired [SyncState] to wait for.
         * @return An [Either] containing [CoreFailure] if [SyncState.Failed] is encountered,
         * or [Unit] if the specified [syncState] is reached.
         */
        suspend fun waitUntilOrFailure(
            syncState: SyncState
        ): Either<CoreFailure, Unit> = syncStateFlow.map { state ->
            when (state) {
                is SyncState.Failed -> Either.Left(state.cause)
                syncState -> Either.Right(Unit)
                else -> null
            }
        }.filterNotNull().first()

        /**
         * Shortcut for [waitUntilOrFailure] with Live state.
         * @see waitUntilOrFailure
         */
        suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit> = waitUntilOrFailure(SyncState.Live)

        /**
         * When called, the sync process continues without being released.
         * This ensuring synchronization persists as long as the sync scope lives.
         * This is particularly useful for services that do not care about the lifecycle, like TestService, CLI, etc. and shouldn't
         * be used by applications that turn sync on/off, like Mobile apps.
         */
        @DelicateKaliumApi("By calling this, Sync will run indefinitely.")
        fun keepSyncAlwaysOn() {
            isEndless = true
        }
    }
}

internal class SyncExecutorImpl(
    private val syncStateObserver: SyncStateObserver,
    private val slowSyncManager: SlowSyncManager,
    private val incrementalSyncManager: IncrementalSyncManager,
    private val scope: CoroutineScope,
    userScopedLogger: KaliumLogger = kaliumLogger,
) : SyncExecutor() {

    private val syncStateFlow = MutableStateFlow<SyncState>(SyncState.Waiting)
    private val logger by lazy { userScopedLogger.withFeatureId(SYNC).withTextTag("SyncExecutor") }

    override fun startAndStopSyncAsNeeded() {
        scope.launch {
            syncStateObserver.syncState.collect { syncStateFlow.value = it }
        }
        scope.launch {
            syncStateFlow.subscriptionCount
                .map { count -> count > 0 }
                .distinctUntilChanged()
                .collectLatest { shouldSync ->
                    if (shouldSync) {
                        performSync()
                    } else {
                        logger.i("!! Stopping sync, as there are no requests for it. !!")
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
        requestAction: suspend Request.() -> T
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
