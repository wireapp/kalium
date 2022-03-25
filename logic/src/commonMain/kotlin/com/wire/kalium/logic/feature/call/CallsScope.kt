package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.sync.SyncManager

class CallsScope(
    private val callManager: CallManager,
    private val syncManager: SyncManager
) {

    val getOngoingCallsUseCase: GetOngoingCallsUseCase get() = GetOngoingCallsUseCaseImpl(
        callManager = callManager,
        syncManager = syncManager
    )
}
