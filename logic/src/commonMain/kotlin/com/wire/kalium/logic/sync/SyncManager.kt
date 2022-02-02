package com.wire.kalium.logic.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

enum class SyncState {
    WAITING,
    SLOW_SYNC,
    COMPLETED,
}

class SyncManager(private val workScheduler: WorkScheduler) {

    private val internalSyncState = MutableStateFlow(SyncState.WAITING)

    fun completeSlowSync() {
        internalSyncState.update { SyncState.COMPLETED }
    }

    suspend fun waitForSlowSyncToComplete() {
        startSlowSyncIfNotAlreadyCompletedOrRunning()
        internalSyncState.first { it == SyncState.COMPLETED }
    }

    private fun startSlowSyncIfNotAlreadyCompletedOrRunning() {
        val syncState = internalSyncState.updateAndGet {
            when (it) {
                SyncState.WAITING -> SyncState.SLOW_SYNC
                else -> it
            }
        }

        if (syncState == SyncState.SLOW_SYNC) {
            workScheduler.schedule(SlowSyncWorker::class, SlowSyncWorker.name)
        }
    }
}
