/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.ConnectionPolicy.KEEP_ALIVE
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncExceptionHandler
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.ExponentialDurationHelperImpl
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Starts and Stops Incremental Sync once SlowSync is performed.
 *
 * Incremental Sync consists of receiving events, such as:
 * - Messages
 * - User Updates (like name, email, avatar)
 * - Conversation Updates (name, add/remove members)
 * - Team Updates (new member, name change)
 * - Feature Flags Updates
 * And many more.
 *
 * Events come from an [EventSource]. Because [EventSource.LIVE] requires
 * a constant Websocket connection, connectivity changes may drop the
 * [IncrementalSyncStatus] down to [IncrementalSyncStatus.Failed].
 *
 * If an [Event] is lost, _e.g._ when this client becomes offline for
 * too long, SlowSync will be invalidated and [SlowSyncManager] should
 * perform a fresh SlowSync.
 *
 * This Manager retries automatically in case of failures,
 * but still doesn't actively monitor connectivity changes in general,
 * like when a mobile phone changes from Wi-Fi to Mobile Data, etc.
 *
 * @see Event
 * @see SlowSyncManager
 */
@Suppress("LongParameterList")
internal class IncrementalSyncManager(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncWorker: IncrementalSyncWorker,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val incrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandler,
    private val networkStateObserver: NetworkStateObserver,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val exponentialDurationHelper: ExponentialDurationHelper = ExponentialDurationHelperImpl(
        MIN_RETRY_DELAY,
        MAX_RETRY_DELAY
    )
) {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val eventProcessingDispatcher = kaliumDispatcher.default.limitedParallelism(1)

    private val coroutineExceptionHandler = SyncExceptionHandler(
        onCancellation = {
            kaliumLogger.i("Cancellation exception handled in SyncExceptionHandler for IncrementalSyncManager")
            syncScope.launch {
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
            }
        },
        onFailure = { failure ->
            kaliumLogger.i("$TAG ExceptionHandler error $failure")
            syncScope.launch {
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Failed(failure))

                incrementalSyncRecoveryHandler.recover(failure = failure) {
                    val delay = exponentialDurationHelper.next()
                    kaliumLogger.i("$TAG Triggering delay($delay) and waiting for reconnection")
                    delayUntilConnectedOrPolicyUpgrade(delay)
                    kaliumLogger.i("$TAG Delay and waiting for connection finished - retrying")
                    startMonitoringForSync()
                }
            }
        }
    )

    private suspend fun delayUntilConnectedOrPolicyUpgrade(delay: Duration): Unit = coroutineScope {
        select {
            async {
                incrementalSyncRepository
                    .connectionPolicyState
                    .drop(1)
                    .first { it == KEEP_ALIVE }
            }.onAwait {
                kaliumLogger.i("$TAG backoff timer short-circuited as Policy was upgraded")
            }
            async {
                networkStateObserver.delayUntilConnectedWithInternetAgain(delay)
            }.onAwait {
                kaliumLogger.i("$TAG wait whole timer, as there was no policy upgrade until now")
            }
        }.also { coroutineContext.cancelChildren() }
    }

    private val syncScope = CoroutineScope(SupervisorJob() + eventProcessingDispatcher)

    init {
        startMonitoringForSync()
    }

    private fun startMonitoringForSync() {
        syncScope.launch(coroutineExceptionHandler) {
            kaliumLogger.i("$TAG started monitoring for SlowSync")
            slowSyncRepository.slowSyncStatus.collectLatest { status ->
                if (status is SlowSyncStatus.Complete) {
                    // START SYNC. The ConnectionPolicy doesn't matter the first time
                    kaliumLogger.i("$TAG Starting IncrementalSync, as SlowSync is completed")
                    doIncrementalSyncWhilePolicyAllows()
                    kaliumLogger.i("$TAG IncrementalSync finished normally. Starting to observe ConnectionPolicy upgrade")
                    observeConnectionPolicyUpgrade()
                }
                incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
            }
        }
    }

    private suspend fun observeConnectionPolicyUpgrade() {
        incrementalSyncRepository.connectionPolicyState
            .filter { it == KEEP_ALIVE }
            .cancellable()
            .collect {
                exponentialDurationHelper.reset()
                kaliumLogger.i("$TAG Re-starting IncrementalSync, as ConnectionPolicy was upgraded to KEEP_ALIVE")
                doIncrementalSyncWhilePolicyAllows()
            }
    }

    private suspend fun doIncrementalSyncWhilePolicyAllows() {
        incrementalSyncWorker
            .processEventsWhilePolicyAllowsFlow()
            .cancellable()
            .collect {
                val newState = when (it) {
                    EventSource.PENDING -> IncrementalSyncStatus.FetchingPendingEvents
                    EventSource.LIVE -> {
                        exponentialDurationHelper.reset()
                        IncrementalSyncStatus.Live
                    }
                }
                incrementalSyncRepository.updateIncrementalSyncState(newState)
            }
        incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        kaliumLogger.i("$TAG IncrementalSync stopped.")
    }

    private companion object {
        val MIN_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 10.minutes
        private const val TAG = "IncrementalSyncManager"
    }
}
