package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.feature.call.usesCases.GetOngoingCallsUseCase
import com.wire.kalium.logic.feature.call.usesCases.GetOngoingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usesCases.StartCallUseCase
import com.wire.kalium.logic.sync.SyncManager

class CallsScope(
    private val callManager: CallManager,
    private val syncManager: SyncManager
) {

    val getOngoingCalls: GetOngoingCallsUseCase
        get() = GetOngoingCallsUseCaseImpl(
        callManager = callManager,
        syncManager = syncManager
    )

    val startCall: StartCallUseCase get() = StartCallUseCase(callManager)
}
