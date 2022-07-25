package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import kotlinx.coroutines.flow.Flow

class ObserveSyncStateUseCase internal constructor(
    private val syncRepository: SyncRepository
) {
    operator fun invoke(): Flow<SyncState> = syncRepository.syncStateState
}
