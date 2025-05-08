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

package com.wire.kalium.logic.sync

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SyncState
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

@Deprecated("Use SyncStateObserver instead", ReplaceWith("SyncStateObserver"))
typealias SyncManager = SyncStateObserver

@Mockable
interface SyncStateObserver {

    /**
     * Allows observing of [SyncState].
     * A value is always available immediately for new observers.
     * Assumes that old SyncStates are not relevant anymore, so the available [Flow]
     * has a limited buffer size and will drop the oldest values as there's no point
     * in waiting for slow collectors.
     * In case a slow collector is interested in receiving all values, it should add a buffer of its own.
     */
    val syncState: StateFlow<SyncState>

    /**
     * Suspends the caller until all pending events are processed,
     * and the client has finished processing all pending events.
     *
     * Suitable for operations where the user is required to be online
     * and without any pending events to be processed, for example write operations, like:
     * - Creating a conversation
     * - Sending a connection request
     * - Editing conversation settings, etc.
     */
    suspend fun waitUntilLive()

    /**
     * If Sync is ongoing, suspends the caller until it reaches a terminal state.
     *
     * @return [CoreFailure] in case Sync was not started or reached a failure, or
     * [Unit] in case [IncrementalSyncStatus.Live] is reached.
     */
    suspend fun waitUntilLiveOrFailure(): Either<CoreFailure, Unit>

    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
    suspend fun waitUntilStartedOrFailure(): Either<NetworkFailure.NoNetworkConnection, Unit>
}

@Deprecated("Use SyncStateObserverImpl instead", ReplaceWith("SyncStateObserverImpl"))
internal typealias SyncManagerImpl = SyncStateObserverImpl

@Suppress("LongParameterList")
internal class SyncStateObserverImpl(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    syncScope: CoroutineScope,
    logger: KaliumLogger = kaliumLogger,
) : SyncStateObserver {

    private val mutableSyncState = MutableStateFlow<SyncState>(SyncState.Waiting)
    override val syncState = mutableSyncState.asStateFlow()
    private val logger by lazy { logger.withFeatureId(SYNC) }

    init {
        syncScope.launch { emitSyncStatus() }
    }

    private suspend fun emitSyncStatus() {
        val combinedStatusFlow = combine(
            slowSyncRepository.slowSyncStatus,
            incrementalSyncRepository.incrementalSyncState
        ) { slowStatus, incrementalStatus ->
            when (slowStatus) {
                is SlowSyncStatus.Failed -> SyncState.Failed(slowStatus.failure, slowStatus.retryDelay)
                is SlowSyncStatus.Ongoing -> SyncState.SlowSync
                SlowSyncStatus.Pending -> SyncState.Waiting
                SlowSyncStatus.Complete -> {
                    when (incrementalStatus) {
                        IncrementalSyncStatus.Live -> SyncState.Live
                        is IncrementalSyncStatus.Failed -> SyncState.Failed(incrementalStatus.failure, incrementalStatus.retryDelay)
                        IncrementalSyncStatus.FetchingPendingEvents -> SyncState.GatheringPendingEvents
                        IncrementalSyncStatus.Pending -> SyncState.GatheringPendingEvents
                    }
                }
            }
        }
        mutableSyncState.emitAll(combinedStatusFlow)
    }

    override suspend fun waitUntilLive() {
        incrementalSyncRepository.incrementalSyncState.first { it is IncrementalSyncStatus.Live }
    }

    override suspend fun waitUntilLiveOrFailure(): Either<NetworkFailure.NoNetworkConnection, Unit> =
        slowSyncRepository.slowSyncStatus.filter { it !is SlowSyncStatus.Pending }
            .combineTransform(incrementalSyncRepository.incrementalSyncState) { slowSyncState, incrementalSyncState ->
                logger.d("Waiting until or failure. Current status: slowSync: $slowSyncState; incrementalSync: $incrementalSyncState")
                val didSlowSyncFail = slowSyncState is SlowSyncStatus.Failed
                val didIncrementalSyncFail = incrementalSyncState is IncrementalSyncStatus.Failed
                val didSyncFail = didSlowSyncFail || didIncrementalSyncFail
                if (didSyncFail) {
                    emit(false)
                }

                val isSyncComplete = incrementalSyncState is IncrementalSyncStatus.Live
                if (isSyncComplete) {
                    emit(true)
                }
            }.first().let { didWaitingSucceed ->
                if (didWaitingSucceed) {
                    logger.d("Waiting until live or failure succeeded")
                    Either.Right(Unit)
                } else {
                    logger.d("Waiting until live or failure failed")
                    Either.Left(NetworkFailure.NoNetworkConnection(null))
                }
            }

    override suspend fun waitUntilStartedOrFailure(): Either<NetworkFailure.NoNetworkConnection, Unit> =
        slowSyncRepository.slowSyncStatus.transform { slowSyncState ->
            logger.d("Waiting until started or failure. Current status: slowSync: $slowSyncState")

            val didSlowSyncFail = slowSyncState is SlowSyncStatus.Failed
            if (didSlowSyncFail) {
                emit(false)
            }

            val isSyncStarted = slowSyncState is SlowSyncStatus.Ongoing || slowSyncState is SlowSyncStatus.Complete
            if (isSyncStarted) {
                emit(true)
            }
        }.first().let { didWaitingSucceed ->
            if (didWaitingSucceed) {
                logger.d("Waiting until started or failure succeeded")
                Either.Right(Unit)
            } else {
                logger.d("Waiting until started or failure failed")
                Either.Left(NetworkFailure.NoNetworkConnection(null))
            }
        }

    override suspend fun isSlowSyncOngoing(): Boolean = slowSyncRepository.slowSyncStatus.value is SlowSyncStatus.Ongoing

    override suspend fun isSlowSyncCompleted(): Boolean = slowSyncRepository.slowSyncStatus.value is SlowSyncStatus.Complete
}
