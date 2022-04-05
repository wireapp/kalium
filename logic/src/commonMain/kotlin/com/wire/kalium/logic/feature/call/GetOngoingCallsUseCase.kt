package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

interface GetOngoingCallsUseCase {
    suspend operator fun invoke(): Flow<List<Call>>
}

internal class GetOngoingCallsUseCaseImpl(
    private val callManagerImpl: CallManagerImpl,
    private val syncManager: SyncManager
): GetOngoingCallsUseCase {

    override suspend operator fun invoke(): Flow<List<Call>> {
        syncManager.waitForSlowSyncToComplete()
        return callManagerImpl.allCalls
    }
}
