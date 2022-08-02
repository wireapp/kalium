package com.wire.kalium.logic.data.sync

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface IncrementalSyncRepository {
    val incrementalSyncState: StateFlow<IncrementalSyncStatus>
    val connectionPolicyState: StateFlow<ConnectionPolicy>
    fun updateIncrementalSyncState(newState: IncrementalSyncStatus)
    fun setConnectionPolicy(connectionPolicy: ConnectionPolicy)
}

internal class InMemoryIncrementalSyncRepository : IncrementalSyncRepository {
    private val _syncState = MutableStateFlow<IncrementalSyncStatus>(IncrementalSyncStatus.Pending)
    override val incrementalSyncState get() = _syncState.asStateFlow()

    private val _connectionPolicy = MutableStateFlow(ConnectionPolicy.KEEP_ALIVE)
    override val connectionPolicyState get() = _connectionPolicy.asStateFlow()

    override fun updateIncrementalSyncState(newState: IncrementalSyncStatus) {
        kaliumLogger.withFeatureId(SYNC).i("IncrementalSyncStatus Updated FROM:${_syncState.value}; TO: $newState")
        _syncState.value = newState
    }

    override fun setConnectionPolicy(connectionPolicy: ConnectionPolicy) {
        kaliumLogger.withFeatureId(SYNC).i("IncrementalSync Connection Policy changed: $connectionPolicy")
        _connectionPolicy.value = connectionPolicy
    }
}
