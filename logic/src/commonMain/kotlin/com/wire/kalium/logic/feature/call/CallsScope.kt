package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.GetAllCallsWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsOnceUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsOnceUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.IsLastCallClosedUseCase
import com.wire.kalium.logic.feature.call.usecase.IsLastCallClosedUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveSpeakerUseCase
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.RequestVideoStreamsUseCase
import com.wire.kalium.logic.feature.call.usecase.SetVideoPreviewUseCase
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOffUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOnUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateVideoStateUseCase
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.KaliumDispatcherImpl

@Suppress("LongParameterList")
class CallsScope internal constructor(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val flowManagerService: FlowManagerService,
    private val mediaManagerService: MediaManagerService,
    private val syncManager: SyncManager
) {

    val allCallsWithSortedParticipants: GetAllCallsWithSortedParticipantsUseCase
        get() = GetAllCallsWithSortedParticipantsUseCase(callRepository, participantsOrder)

    val establishedCall: ObserveEstablishedCallsUseCase
        get() = ObserveEstablishedCallsUseCase(
            callRepository = callRepository,
        )

    val getIncomingCalls: GetIncomingCallsUseCase
        get() = GetIncomingCallsUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            userRepository = userRepository
        )

    val getIncomingCallsOnce: GetIncomingCallsOnceUseCase
        get() = GetIncomingCallsOnceUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            userRepository = userRepository
        )

    val observeOngoingCalls: ObserveOngoingCallsUseCase
        get() = ObserveOngoingCallsUseCaseImpl(
            callRepository = callRepository,
        )

    val startCall: StartCallUseCase get() = StartCallUseCase(callManager, syncManager)

    val answerCall: AnswerCallUseCase get() = AnswerCallUseCaseImpl(callManager)

    val endCall: EndCallUseCase get() = EndCallUseCase(callManager, callRepository, KaliumDispatcherImpl)

    val rejectCall: RejectCallUseCase get() = RejectCallUseCase(callManager)

    val muteCall: MuteCallUseCase get() = MuteCallUseCase(callManager, callRepository)

    val unMuteCall: UnMuteCallUseCase get() = UnMuteCallUseCase(callManager, callRepository)

    val updateVideoState: UpdateVideoStateUseCase get() = UpdateVideoStateUseCase(callManager, callRepository)

    val setVideoPreview: SetVideoPreviewUseCase get() = SetVideoPreviewUseCase(flowManagerService)

    val turnLoudSpeakerOff: TurnLoudSpeakerOffUseCase get() = TurnLoudSpeakerOffUseCase(mediaManagerService)

    val turnLoudSpeakerOn: TurnLoudSpeakerOnUseCase get() = TurnLoudSpeakerOnUseCase(mediaManagerService)

    val observeSpeaker: ObserveSpeakerUseCase get() = ObserveSpeakerUseCase(mediaManagerService)

    val participantsOrder: ParticipantsOrder get() = ParticipantsOrderImpl()

    val isLastCallClosed: IsLastCallClosedUseCase get() = IsLastCallClosedUseCaseImpl(callRepository)

    val requestVideoStreams: RequestVideoStreamsUseCase get() = RequestVideoStreamsUseCase(callManager, KaliumDispatcherImpl)
}
