package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.flow.first

interface SyncManager {
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
}

internal class SyncManagerImpl(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
) : SyncManager {

    override suspend fun waitUntilLive() {
        incrementalSyncRepository.incrementalSyncState.first { it is IncrementalSyncStatus.Live }
    }

    override suspend fun waitUntilLiveOrFailure(): Either<NetworkFailure.NoNetworkConnection, Unit> = slowSyncRepository.slowSyncStatus
        .combineTransform(incrementalSyncRepository.incrementalSyncState) { slowSyncState, incrementalSyncState ->
            val didSlowSyncFail = slowSyncState is SlowSyncStatus.Pending || slowSyncState is SlowSyncStatus.Failed
            val didIncrementalSyncFail = incrementalSyncState is IncrementalSyncStatus.Failed
            val didSyncFail = didSlowSyncFail || didIncrementalSyncFail
            if (didSyncFail) { emit(false) }

            val isSyncComplete = incrementalSyncState is IncrementalSyncStatus.Live
            if (isSyncComplete) { emit(true) }
        }.first().let { didWaitingSucceed ->
            if (didWaitingSucceed) {
                Either.Right(Unit)
            } else {
                Either.Left(NetworkFailure.NoNetworkConnection(null))
            }
        }

    override suspend fun isSlowSyncOngoing(): Boolean = slowSyncRepository.slowSyncStatus.value is SlowSyncStatus.Ongoing
    override suspend fun isSlowSyncCompleted(): Boolean =
        slowSyncRepository.slowSyncStatus.value is SlowSyncStatus.Complete
}
