package com.wire.kalium.logic.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

enum class SyncState {
    WAITING,
    SLOW_SYNC,
    COMPLETED,
}

interface SyncManager {
    fun completeSlowSync()
    suspend fun waitForSlowSyncToComplete()
    suspend fun isSlowSyncOngoing(): Boolean
    suspend fun isSlowSyncCompleted(): Boolean
}

class SyncManagerImpl(private val userSessionWorkScheduler: WorkScheduler.UserSession) : SyncManager {

    private val internalSyncState = MutableStateFlow(SyncState.WAITING)

    override fun completeSlowSync() {
        internalSyncState.update { SyncState.COMPLETED }
    }

    override suspend fun waitForSlowSyncToComplete() {
        startSlowSyncIfNotAlreadyCompletedOrRunning()
        internalSyncState.first { it == SyncState.COMPLETED }
    }

    private fun startSlowSyncIfNotAlreadyCompletedOrRunning() {
        val syncState = internalSyncState.getAndUpdate {
            when (it) {
                SyncState.WAITING -> SyncState.SLOW_SYNC
                else -> it
            }
        }

        if (syncState == SyncState.WAITING) {
            userSessionWorkScheduler.scheduleSlowSync()
        }
    }

    override suspend fun isSlowSyncOngoing(): Boolean = internalSyncState.first() == SyncState.SLOW_SYNC
    override suspend fun isSlowSyncCompleted(): Boolean = internalSyncState.first() == SyncState.COMPLETED
}
