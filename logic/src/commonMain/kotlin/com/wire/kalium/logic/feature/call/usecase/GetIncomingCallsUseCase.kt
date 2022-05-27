package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

interface GetIncomingCallsUseCase {
    suspend operator fun invoke(): Flow<List<Call>>
}

internal class GetIncomingCallsUseCaseImpl(
    private val callRepository: CallRepository,
    private val syncManager: SyncManager
) : GetIncomingCallsUseCase {

    override suspend operator fun invoke(): Flow<List<Call>> {
        syncManager.waitForSyncToComplete()
        return callRepository.incomingCallsFlow()
    }
}
