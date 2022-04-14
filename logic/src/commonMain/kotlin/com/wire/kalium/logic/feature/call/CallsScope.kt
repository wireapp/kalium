package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.GetOngoingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.GetOngoingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
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

    val answerCall: AnswerCallUseCase get() = AnswerCallUseCaseImpl(callManager)

    val endCall: EndCallUseCase get() = EndCallUseCase(callManager)

    val rejectCall: RejectCallUseCase get() = RejectCallUseCase(callManager)
}
