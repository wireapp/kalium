package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.sync.SyncManager

class CallsScope(
    private val callManagerImpl: CallManagerImpl,
    private val syncManager: SyncManager
) {

    val getOngoingCalls: GetOngoingCallsUseCase get() = GetOngoingCallsUseCaseImpl(
        callManagerImpl = callManagerImpl,
        syncManager = syncManager
    )

    val answerCall: AnswerCallUseCase get() = AnswerCallUseCaseImpl(
        callManagerImpl = callManagerImpl
    )
}
