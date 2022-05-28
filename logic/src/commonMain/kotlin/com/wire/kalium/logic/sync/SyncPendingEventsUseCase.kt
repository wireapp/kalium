package com.wire.kalium.logic.sync

@Deprecated(
    "Sync is triggered by other use cases as necessary and there is no need to call this UseCase directly anymore. " +
            "If necessary, use ObserveSyncStateUseCase to know when Pending Events are finished processing."
)
class SyncPendingEventsUseCase(
    private val syncManager: SyncManager
) {

    /**
     * Syncing only Pending Events, to find out what did we miss
     */
    suspend operator fun invoke() {
        syncManager.waitUntilLive()
    }
}
