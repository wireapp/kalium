package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.feature.conversation.SyncConversationsUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SyncState {
    WAITING,
    SLOW_SYNC,
    FETCHING_CONVERSATIONS,
    COMPLETED,
}

class SyncManager(private val syncConversationsUseCase: SyncConversationsUseCase) {

    private val internalSyncState = MutableStateFlow(SyncState.WAITING)
    private var syncJob: Job? = null

    suspend fun performSlowSync(): Either<CoreFailure, Unit> {
        internalSyncState.update { SyncState.FETCHING_CONVERSATIONS }

        return syncConversationsUseCase()
            .onSuccess { internalSyncState.update { SyncState.COMPLETED } }
    }

    suspend fun waitForSlowSyncToComplete() {
        startSyncJobIfNotAlreadyRunning()
        startSlowSyncIfNotAlreadyCompletedOrRunning()

        internalSyncState.first { it == SyncState.COMPLETED }
    }

    private suspend fun startSyncJobIfNotAlreadyRunning() {
        if (syncJob != null) {
            return
        }

        // TODO should be executed by the WorkerManager(Android) / BackgroundTask(iOS)
        syncJob = GlobalScope.launch {
            internalSyncState.filter { it == SyncState.SLOW_SYNC }.collect {
                performSlowSync()
            }
        }
    }

    private fun startSlowSyncIfNotAlreadyCompletedOrRunning() {
        internalSyncState.compareAndSet(SyncState.WAITING, SyncState.SLOW_SYNC)
    }
}
