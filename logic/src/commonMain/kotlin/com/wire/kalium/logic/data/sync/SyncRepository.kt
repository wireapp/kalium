package com.wire.kalium.logic.data.sync

import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

interface SyncRepository {
    val syncState: Flow<SyncState>
    fun updateSyncState(updateBlock: (currentState: SyncState) -> SyncState): SyncState
}

internal class InMemorySyncRepository : SyncRepository {
    private val _syncState = MutableStateFlow<SyncState>(SyncState.Waiting)

    override val syncState: Flow<SyncState> get() = _syncState

    override fun updateSyncState(updateBlock: (currentState: SyncState) -> SyncState): SyncState =
        _syncState.updateAndGet { currentSyncState ->
            val newSyncState = updateBlock(currentSyncState)
            kaliumLogger.i("SyncStatus Updated FROM:$currentSyncState; TO: $newSyncState")
            newSyncState
        }
}
