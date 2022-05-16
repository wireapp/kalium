package com.wire.kalium.logic.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet

interface SyncRepository {
    val syncState: StateFlow<SyncState>
    fun updateSyncState(updateBlock: (currentState: SyncState) -> SyncState): SyncState
}

class InMemorySyncRepository : SyncRepository {
    private val _syncState = MutableStateFlow(SyncState.WAITING)

    override val syncState: StateFlow<SyncState> get() = _syncState

    override fun updateSyncState(updateBlock: (currentState: SyncState) -> SyncState): SyncState =
        _syncState.updateAndGet(updateBlock)
}
