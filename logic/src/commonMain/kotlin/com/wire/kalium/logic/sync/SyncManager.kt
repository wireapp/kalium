package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import kotlinx.coroutines.flow.first

interface SyncManager {
    fun completeSlowSync()
    suspend fun waitForSlowSyncToComplete()
    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
}

class SyncManagerImpl(
    private val userSessionWorkScheduler: WorkScheduler.UserSession,
    private val syncRepository: SyncRepository
) : SyncManager {

    override fun completeSlowSync() {
        syncRepository.updateSyncState { SyncState.COMPLETED }
    }

    override suspend fun waitForSlowSyncToComplete() {
        startSlowSyncIfNotAlreadyCompletedOrRunning()
        syncRepository.syncState.first { it == SyncState.COMPLETED }
    }

    private fun startSlowSyncIfNotAlreadyCompletedOrRunning() {
        val syncState = syncRepository.updateSyncState {
            when (it) {
                SyncState.WAITING -> SyncState.SLOW_SYNC
                else -> it
            }
        }

        if (syncState == SyncState.SLOW_SYNC) {
            userSessionWorkScheduler.enqueueImmediateWork(SlowSyncWorker::class, SlowSyncWorker.name)
        }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = syncRepository.syncState.first() == SyncState.SLOW_SYNC
    override suspend fun isSlowSyncCompleted(): Boolean = syncRepository.syncState.first() == SyncState.COMPLETED
}
