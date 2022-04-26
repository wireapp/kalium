package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.ongoingCall
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetOngoingCallUseCase(
    private val callManager: CallManager,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(): Flow<List<Call>> {
        syncManager.waitForSlowSyncToComplete()
        return callManager.ongoingCall
    }
}
