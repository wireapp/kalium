package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
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
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val syncManager: Lazy<SyncManager>
) {

    val allCalls: GetAllCallsUseCase
        get() = GetAllCallsUseCase(
            callRepository = callRepository,
            syncManager = syncManager.value
        )

    val onGoingCall: GetOngoingCallUseCase
        get() = GetOngoingCallUseCase(
            callRepository = callRepository,
            syncManager = syncManager.value
        )

    val getIncomingCalls: GetIncomingCallsUseCase
        get() = GetIncomingCallsUseCaseImpl(
            callRepository = callRepository,
            syncManager = syncManager.value
        )

    val startCall: StartCallUseCase get() = StartCallUseCase(callManager.value)

    val answerCall: AnswerCallUseCase get() = AnswerCallUseCaseImpl(callManager.value)

    val endCall: EndCallUseCase get() = EndCallUseCase(callManager.value)

    val rejectCall: RejectCallUseCase get() = RejectCallUseCase(callManager.value)

    val muteCall: MuteCallUseCase get() = MuteCallUseCase(callManager.value)

    val unMuteCall: UnMuteCallUseCase get() = UnMuteCallUseCase(callManager.value)
}
