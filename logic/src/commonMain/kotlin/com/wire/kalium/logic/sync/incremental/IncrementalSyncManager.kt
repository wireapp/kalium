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

import com.benasher44.uuid.uuid4
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.sync.SyncExceptionHandler
import com.wire.kalium.logic.sync.SyncType
import com.wire.kalium.logic.sync.provideNewSyncManagerLogger
import com.wire.kalium.logic.sync.slow.SlowSyncManager
import com.wire.kalium.logic.util.ExponentialDurationHelper
import com.wire.kalium.logic.util.ExponentialDurationHelperImpl
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Starts and Stops Incremental Sync.
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
internal interface IncrementalSyncManager {

    /**
     * While collected, performs IncrementalSync, by fetching
     * and processing events, etc.
     * Emits the current [IncrementalSyncStatus] as it progresses.
     * In case of failure, will retry as needed.
     * Does not end.
     */
    fun performSyncFlow(): Flow<IncrementalSyncStatus>

    companion object {
        val MIN_RETRY_DELAY = 1.seconds
        val MAX_RETRY_DELAY = 10.minutes
    }
}

@Suppress("LongParameterList", "FunctionNaming")
internal fun IncrementalSyncManager(
    incrementalSyncWorker: IncrementalSyncWorker,
    incrementalSyncRepository: IncrementalSyncRepository,
    incrementalSyncRecoveryHandler: IncrementalSyncRecoveryHandler,
    networkStateObserver: NetworkStateObserver,
    userScopedLogger: KaliumLogger,
    exponentialDurationHelper: ExponentialDurationHelper = ExponentialDurationHelperImpl(
        IncrementalSyncManager.MIN_RETRY_DELAY,
        IncrementalSyncManager.MAX_RETRY_DELAY
    )
) = object : IncrementalSyncManager {

    private val logger = userScopedLogger.withFeatureId(SYNC).withTextTag("IncrementalSyncManager")

    private fun coroutineExceptionHandler(onRetry: suspend () -> Unit) = SyncExceptionHandler(
        onCancellation = {
            logger.i("Cancellation exception handled in SyncExceptionHandler for IncrementalSyncManager")
            incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        },
        onFailure = { failure ->
            logger.i("ExceptionHandler error $failure")
            val delay = exponentialDurationHelper.next()
            incrementalSyncRepository.updateIncrementalSyncState(
                IncrementalSyncStatus.Failed(failure, delay)
            )

            incrementalSyncRecoveryHandler.recover(failure = failure) {
                logger.i("Triggering delay($delay) and waiting for reconnection")
                networkStateObserver.delayUntilConnectedWithInternetAgain(delay)
                logger.i("Delay and waiting for connection finished - retrying")
                onRetry()
            }
        }
    )

    private val exceptionHandler = coroutineExceptionHandler { doIncrementalSync() }

    override fun performSyncFlow(): Flow<IncrementalSyncStatus> = channelFlow {
        coroutineScope {
            launch {
                // TODO: Instead of forwarding repository state, we could just emit within the flow. Killing the repository completely.
                incrementalSyncRepository.incrementalSyncState.collect {
                    send(it)
                }
            }
            // Always start sync with a fresh retry delay
            exponentialDurationHelper.reset()
            launch { doIncrementalSync() }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun doIncrementalSync(): Unit = try {
        incrementalSyncWorker
            .processEventsFlow()
            .cancellable()
            .runningFold(uuid4().toString() to Clock.System.now()) { syncData, eventSource ->
                val syncLogger = kaliumLogger.provideNewSyncManagerLogger(SyncType.INCREMENTAL, syncData.first)
                val newState = when (eventSource) {
                    EventSource.PENDING -> {
                        syncLogger.logSyncStarted()
                        IncrementalSyncStatus.FetchingPendingEvents
                    }

                    EventSource.LIVE -> {
                        syncLogger.logSyncCompleted(duration = Clock.System.now() - syncData.second)
                        exponentialDurationHelper.reset()
                        IncrementalSyncStatus.Live
                    }
                }
                incrementalSyncRepository.updateIncrementalSyncState(newState)

                // when the source is LIVE, we need to generate a new syncId since it means the previous one is done
                if (eventSource == EventSource.LIVE) uuid4().toString() to Clock.System.now() else syncData
            }.collect()
        incrementalSyncRepository.updateIncrementalSyncState(IncrementalSyncStatus.Pending)
        logger.i("IncrementalSync stopped.")
    } catch (t: Throwable) {
        exceptionHandler.handleException(t)
    }
}
