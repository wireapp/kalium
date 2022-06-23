package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetAllCallsUseCase(
    private val callRepository: CallRepository,
    private val syncManager: SyncManager
) {
    operator fun invoke(): Flow<List<Call>> {
        syncManager.startSyncIfIdle()
        return callRepository.callsFlow()
    }
}
