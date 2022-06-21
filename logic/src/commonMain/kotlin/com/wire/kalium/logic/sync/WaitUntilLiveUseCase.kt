package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.SyncState

/**
 * Starts sync (if not yet started) and suspends until all pending events were processed.
 * Will resume when all pending events are processed and the client is considered "online".
 * @see SyncState
 */
class WaitUntilLiveUseCase(
    private val syncManager: SyncManager
) {

    suspend operator fun invoke() {
        syncManager.waitUntilLive()
    }
}
