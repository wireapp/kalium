package com.wire.kalium.logic.feature.call

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

    val answerCall: AnswerCallUseCase
        get() = AnswerCallUseCaseImpl(
            callManager = callManager
        )
}
