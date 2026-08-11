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
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.sync.incremental.IncrementalSyncManager
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch

public abstract class SyncExecutor {

    public abstract fun startAndStopSyncAsNeeded()

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
                logger.logSyncTelemetry(
                    event = SyncTelemetryEvent.REQUEST_RETAINED,
                    component = SyncTelemetryComponent.EXECUTOR,
                    level = KaliumLogLevel.WARN,
                    data = mapOf("reason" to "KEEP_SYNC_ALWAYS_ON")
                )
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

        override suspend fun waitUntilNextLiveOrFailure(): SyncRequestResult {
            var hasObservedSyncProgress = false
            return syncStateFlow.drop(1).map { state ->
                if (!hasObservedSyncProgress && state !is SyncState.Failed) {
                    hasObservedSyncProgress = true
                }
                when (state) {
                    is SyncState.Failed -> if (hasObservedSyncProgress) SyncRequestResult.Failure(state.cause) else null
                    SyncState.Live -> SyncRequestResult.Success
                    else -> null
                }
            }.filterNotNull().first()
        }

        override fun keepSyncAlwaysOn() {
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

    private enum class DemandAction(val startsExecution: Boolean = false) {
        /** First request arrived — start sync. */
        START(startsExecution = true),

        /** New request while recovery is backing off — restart immediately with fresh backoff. */
        RESTART(startsExecution = true),

        /** New request while an attempt is in flight — keep it running, but a later failure retries from the minimum delay. */
        RESET_BACKOFF,

        /** Requests changed while sync is already running as needed. */
        KEEP_RUNNING,

        /** Last request released — stop sync. */
        STOP,

        /** No requests before or after the change. */
        IDLE,
    }

    private data class SyncDemand(
        val requesterCount: Int = 0,
        val restartVersion: Long = 0,
        val lastStartAction: DemandAction? = null,
    )

    private data class SyncExecution(
        val shouldSync: Boolean,
        val restartVersion: Long,
        val trigger: DemandAction?,
    )

    // Keep logical request lifetimes separate from transient collectors that only wait for a sync state.
    // Never emits; its subscriptionCount (non-conflated) is the requester count.
    private val syncRequestDemandFlow = MutableSharedFlow<Unit>()
    private val logger by lazy { userScopedLogger.withFeatureId(SYNC).withTextTag("SyncExecutor") }

    override fun startAndStopSyncAsNeeded() {
        scope.launch {
            syncRequestDemandFlow.subscriptionCount
                .runningFold(SyncDemand()) { previous, requesterCount ->
                    val syncState = syncStateObserver.syncState.value
                    val action = decideDemandAction(previous.requesterCount, requesterCount, syncState)
                    if (action == DemandAction.RESET_BACKOFF) {
                        slowSyncManager.resetRetryBackoff()
                        incrementalSyncManager.resetRetryBackoff()
                    }
                    logRequesterCountChanged(previous.requesterCount, requesterCount, syncState, action)
                    SyncDemand(
                        requesterCount = requesterCount,
                        restartVersion = previous.restartVersion + if (action.startsExecution) 1 else 0,
                        lastStartAction = if (action.startsExecution) action else previous.lastStartAction,
                    )
                }
                .map { demand ->
                    SyncExecution(
                        shouldSync = demand.requesterCount > 0,
                        restartVersion = demand.restartVersion,
                        trigger = demand.lastStartAction,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { execution ->
                    if (execution.shouldSync) {
                        logger.logSyncTelemetry(
                            event = SyncTelemetryEvent.EXECUTION_STARTED,
                            component = SyncTelemetryComponent.EXECUTOR,
                            data = mapOf(
                                "restartVersion" to execution.restartVersion,
                                "trigger" to execution.trigger?.name,
                            )
                        )
                        performSync()
                    } else {
                        logger.logSyncTelemetry(
                            event = SyncTelemetryEvent.EXECUTION_STOPPED,
                            component = SyncTelemetryComponent.EXECUTOR,
                            data = mapOf(
                                "restartVersion" to execution.restartVersion,
                                "reason" to "NO_ACTIVE_REQUESTS",
                            )
                        )
                    }
                }
        }
    }

    /**
     * The demand policy: the first request starts sync. A later request restarts it only when
     * recovery is backing off; an in-flight or live connection is left undisturbed.
     */
    private fun decideDemandAction(
        previousCount: Int,
        requesterCount: Int,
        syncState: SyncState,
    ): DemandAction {
        val requestAdded = requesterCount > previousCount
        return when {
            requestAdded && previousCount == 0 -> DemandAction.START
            requestAdded && syncState is SyncState.Failed -> DemandAction.RESTART
//             requestAdded && syncState != SyncState.Live -> DemandAction.RESET_BACKOFF // for now disabled need a bit more testing
            requesterCount == 0 && previousCount > 0 -> DemandAction.STOP
            requesterCount > 0 -> DemandAction.KEEP_RUNNING
            else -> DemandAction.IDLE
        }
    }

    private fun logRequesterCountChanged(
        previousCount: Int,
        requesterCount: Int,
        syncState: SyncState,
        action: DemandAction,
    ) {
        logger.logSyncTelemetry(
            event = SyncTelemetryEvent.REQUEST_COUNT_CHANGED,
            component = SyncTelemetryComponent.EXECUTOR,
            level = KaliumLogLevel.DEBUG,
            data = buildMap {
                put("previousRequesterCount", previousCount)
                put("requesterCount", requesterCount)
                put("syncState", syncState.telemetryName())
                put("action", action.name)
                if (syncState is SyncState.Failed) {
                    put("retryDelayInMillis", syncState.retryDelay.inWholeMilliseconds)
                    putAll(syncState.cause.telemetryData())
                }
            }
        )
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
            syncRequestDemandFlow.collect()
        }
        return Request(syncStateObserver.syncState, syncJob, logger)
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
