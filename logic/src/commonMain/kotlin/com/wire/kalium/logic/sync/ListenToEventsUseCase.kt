package com.wire.kalium.logic.sync

@Deprecated("Sync is triggered by other use cases as necessary and there is no need to call this UseCase directly anymore")
class ListenToEventsUseCase(
    private val syncManager: SyncManager,
) {

    suspend operator fun invoke() {
        syncManager.waitUntilLive()
    }

}
