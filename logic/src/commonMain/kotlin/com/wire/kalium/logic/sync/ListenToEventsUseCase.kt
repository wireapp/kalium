package com.wire.kalium.logic.sync

class ListenToEventsUseCase(
    private val syncManager: SyncManager,
) {

    suspend operator fun invoke() {
        syncManager.waitForSyncToComplete()
    }

}
