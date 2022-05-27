package com.wire.kalium.logic.sync

class SyncPendingEventsUseCase(
    private val syncManager: SyncManager
) {

    /**
     * Syncing only Pending Events, to find out what did we miss
     */
    suspend operator fun invoke() {
        syncManager.waitForSyncToComplete()
    }
}
