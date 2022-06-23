package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

interface ObserveOngoingCallsUseCase {
    suspend operator fun invoke(): Flow<List<Call>>
}

/**
 *
 * @param callRepository CallRepository for getting all the ongoing calls.
 * @param syncManager SyncManager to sync the data before checking the calls.
 *
 * @return Flow<List<Call>> - Flow of Calls List that should be shown to the user.
 * That Flow emits everytime when the list is changed
 */
internal class ObserveOngoingCallsUseCaseImpl(
    private val callRepository: CallRepository,
    private val syncManager: SyncManager
) : ObserveOngoingCallsUseCase {

    override suspend fun invoke(): Flow<List<Call>> {
        syncManager.startSyncIfIdle()

        return callRepository.ongoingCallsFlow()
    }
}
