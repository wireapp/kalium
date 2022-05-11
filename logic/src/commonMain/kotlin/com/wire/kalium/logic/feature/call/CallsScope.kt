package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.conversation.ConversationRepository
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
    private val callManager: CallManager,
    private val callRepository: CallRepository,
    private val syncManager: SyncManager,
    private val conversationRepository: ConversationRepository
) {

    val allCalls: GetAllCallsUseCase
        get() = GetAllCallsUseCase(
            callRepository = callRepository,
            syncManager = syncManager
        )

    val onGoingCall: GetOngoingCallUseCase
        get() = GetOngoingCallUseCase(
            callRepository = callRepository,
            syncManager = syncManager
        )

    val getIncomingCalls: GetIncomingCallsUseCase
        get() = GetIncomingCallsUseCaseImpl(
            callRepository = callRepository,
            syncManager = syncManager,
            conversationRepository = conversationRepository
        )

    val startCall: StartCallUseCase get() = StartCallUseCase(callManager)

    val answerCall: AnswerCallUseCase get() = AnswerCallUseCaseImpl(callManager)

    val endCall: EndCallUseCase get() = EndCallUseCase(callManager)

    val rejectCall: RejectCallUseCase get() = RejectCallUseCase(callManager)

    val muteCall: MuteCallUseCase get() = MuteCallUseCase(callManager)

    val unMuteCall: UnMuteCallUseCase get() = UnMuteCallUseCase(callManager)
}
