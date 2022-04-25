package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.GetAllCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.GetOngoingCallUseCase
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCase
import com.wire.kalium.logic.sync.SyncManager

class CallsScope(
    private val callManager: CallManager,
    private val syncManager: SyncManager
) {

    val onGoingCall: GetOngoingCallUseCase
        get() = GetOngoingCallUseCase(
            callManager = callManager,
            syncManager = syncManager
        )

    val allCalls: GetAllCallsUseCase
        get() = GetAllCallsUseCase(
            callManager = callManager,
            syncManager = syncManager
        )

    val getIncomingCalls: GetIncomingCallsUseCase
        get() = GetIncomingCallsUseCaseImpl(
            callManager = callManager,
            syncManager = syncManager
        )

    val startCall: StartCallUseCase get() = StartCallUseCase(callManager)

    val answerCall: AnswerCallUseCase get() = AnswerCallUseCaseImpl(callManager)

    val endCall: EndCallUseCase get() = EndCallUseCase(callManager)

    val rejectCall: RejectCallUseCase get() = RejectCallUseCase(callManager)

    val muteCall: MuteCallUseCase get() = MuteCallUseCase(callManager)

    val unMuteCall: UnMuteCallUseCase get() = UnMuteCallUseCase(callManager)
}
