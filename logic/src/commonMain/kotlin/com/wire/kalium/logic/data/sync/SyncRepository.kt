package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

internal interface SyncRepository {
    val syncStateState: StateFlow<SyncState>
    val connectionPolicyState: StateFlow<ConnectionPolicy>
    fun updateSyncState(updateBlock: (currentState: SyncState) -> SyncState): SyncState
    fun setConnectionPolicy(connectionPolicy: ConnectionPolicy)
}

internal class InMemorySyncRepository : SyncRepository {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Waiting)
    override val syncStateState get() = _syncState.asStateFlow()

    private val _connectionPolicy = MutableStateFlow(ConnectionPolicy.KEEP_ALIVE)
    override val connectionPolicyState get() = _connectionPolicy.asStateFlow()

    override fun updateSyncState(updateBlock: (currentState: SyncState) -> SyncState): SyncState =
        _syncState.updateAndGet { currentSyncState ->
            val newSyncState = updateBlock(currentSyncState)
            kaliumLogger.withFeatureId(SYNC).i("SyncStatus Updated FROM:$currentSyncState; TO: $newSyncState")
            newSyncState
        }

    override fun setConnectionPolicy(connectionPolicy: ConnectionPolicy) {
        kaliumLogger.withFeatureId(SYNC).i("Sync Connection Policy changed: $connectionPolicy")
        _connectionPolicy.value = connectionPolicy
    }
}
