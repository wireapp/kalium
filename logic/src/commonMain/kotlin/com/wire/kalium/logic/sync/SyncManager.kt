package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncStatus
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
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

    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
}

internal class SyncManagerImpl(
    private val slowSyncRepository: SlowSyncRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
) : SyncManager {

    override suspend fun waitUntilLive() {
        incrementalSyncRepository.incrementalSyncState.first { it is IncrementalSyncStatus.Complete }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = slowSyncRepository.slowSyncStatus.value is SlowSyncStatus.Ongoing
    override suspend fun isSlowSyncCompleted(): Boolean =
        slowSyncRepository.slowSyncStatus.value is SlowSyncStatus.Complete
}
