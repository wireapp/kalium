package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal interface IncrementalSyncRepository {
    /**
     * Buffered flow of [IncrementalSyncStatus].
     * - Has a replay size of 1, so the latest
     * value is always immediately available for new observers.
     * - Doesn't emit repeated values.
     */
    val incrementalSyncState: Flow<IncrementalSyncStatus>

    /**
     * Buffered flow of [ConnectionPolicy].
     * - Has a replay size of 1, so the latest
     * value is always immediately available for new observers.
     * - Doesn't emit repeated values.
     */
    val connectionPolicyState: Flow<ConnectionPolicy>
    suspend fun updateIncrementalSyncState(newState: IncrementalSyncStatus)
    suspend fun setConnectionPolicy(connectionPolicy: ConnectionPolicy)
}

internal class InMemoryIncrementalSyncRepository : IncrementalSyncRepository {
    private val _syncState = MutableSharedFlow<IncrementalSyncStatus>(
        replay = 1,
        extraBufferCapacity = BUFFER_SIZE
    )

    override val incrementalSyncState = _syncState
        .asSharedFlow()
        .distinctUntilChanged()

    private val _connectionPolicy = MutableSharedFlow<ConnectionPolicy>(
        replay = 1,
        extraBufferCapacity = BUFFER_SIZE
    )

    override val connectionPolicyState = _connectionPolicy
        .asSharedFlow()
        .distinctUntilChanged()

    init {
        _syncState.tryEmit(IncrementalSyncStatus.Pending)
        _connectionPolicy.tryEmit(ConnectionPolicy.KEEP_ALIVE)
    }

    override suspend fun updateIncrementalSyncState(newState: IncrementalSyncStatus) {
        kaliumLogger.withFeatureId(SYNC).i("IncrementalSyncStatus Updated FROM:${_syncState.first()}; TO: $newState")
        _syncState.emit(newState)
    }

    override suspend fun setConnectionPolicy(connectionPolicy: ConnectionPolicy) {
        kaliumLogger.withFeatureId(SYNC).i("IncrementalSync Connection Policy changed: $connectionPolicy")
        _connectionPolicy.emit(connectionPolicy)
    }

    private companion object {
        // The same default buffer size used by Coroutines channels
        const val BUFFER_SIZE = 64
    }
}
