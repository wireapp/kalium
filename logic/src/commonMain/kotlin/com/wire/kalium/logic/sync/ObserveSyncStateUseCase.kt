package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.data.sync.SyncState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveSyncStateUseCase internal constructor(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository
) {
    operator fun invoke(): Flow<SyncState> = slowSyncRepository.slowSyncStatus
        .combine(incrementalSyncRepository.incrementalSyncState) { slowStatus, incrementalStatus ->
            when (slowStatus) {
                is SlowSyncStatus.Ongoing -> SyncState.SlowSync
                SlowSyncStatus.Pending -> SyncState.Waiting
                SlowSyncStatus.Complete -> {
                    when (incrementalStatus) {
                        IncrementalSyncStatus.Live -> SyncState.Live
                        is IncrementalSyncStatus.Failed -> SyncState.Failed(incrementalStatus.failure)
                        IncrementalSyncStatus.FetchingPendingEvents -> SyncState.GatheringPendingEvents
                        IncrementalSyncStatus.Pending -> SyncState.GatheringPendingEvents
                    }
                }
            }
        }
}
