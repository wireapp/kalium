/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallingParticipantsOrder
import com.wire.kalium.logic.data.call.CallingParticipantsOrderImpl
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.call.ParticipantsFilterImpl
import com.wire.kalium.logic.data.call.ParticipantsOrderByNameImpl
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.usecase.AnswerCallUseCase
import com.wire.kalium.logic.feature.call.usecase.AnswerCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.EndCallOnConversationChangeUseCase
import com.wire.kalium.logic.feature.call.usecase.EndCallOnConversationChangeUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.EndCallResultListenerImpl
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.FlipToBackCameraUseCase
import com.wire.kalium.logic.feature.call.usecase.FlipToFrontCameraUseCase
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.IsCallRunningUseCase
import com.wire.kalium.logic.feature.call.usecase.IsEligibleToStartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.IsEligibleToStartCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.IsLastCallClosedUseCase
import com.wire.kalium.logic.feature.call.usecase.IsLastCallClosedUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveConferenceCallingEnabledUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveConferenceCallingEnabledUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveEndCallDueToConversationDegradationUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEndCallDueToConversationDegradationUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallWithSortedParticipantsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveInCallReactionsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveInCallReactionsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveLastActiveCallWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveLastActiveCallWithSortedParticipantsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingAndIncomingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingAndIncomingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveOutgoingCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOutgoingCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveRecentlyEndedCallMetadataUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveSpeakerUseCase
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.RequestVideoStreamsUseCase
import com.wire.kalium.logic.feature.call.usecase.SetTestPreviewActiveUseCase
import com.wire.kalium.logic.feature.call.usecase.SetTestRemoteVideoStatesUseCase
import com.wire.kalium.logic.feature.call.usecase.SetTestVideoTypeUseCase
import com.wire.kalium.logic.feature.call.usecase.SetUIRotationUseCase
import com.wire.kalium.logic.feature.call.usecase.SetVideoPreviewUseCase
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOffUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOnUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.observeAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.call.usecase.video.SetVideoSendStateUseCase
import com.wire.kalium.logic.feature.call.usecase.video.UpdateVideoStateUseCase
import com.wire.kalium.logic.feature.user.ShouldAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.user.UpdateNextTimeCallFeedbackUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl

@Suppress("LongParameterList")
class CallsScope internal constructor(
    private val callManager: Lazy<CallManager>,
    private val callRepository: CallRepository,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val flowManagerService: FlowManagerService,
    private val mediaManagerService: MediaManagerService,
    private val syncManager: Lazy<SyncManager>,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val userConfigRepository: UserConfigRepository,
    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
    private val getCallConversationType: GetCallConversationTypeProvider,
    private val kaliumConfigs: KaliumConfigs,
    private val inCallReactionsRepository: InCallReactionsRepository,
    private val selfUserId: UserId,
    internal val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    val allCallsWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase
        get() = ObserveEstablishedCallWithSortedParticipantsUseCaseImpl(callRepository, callingParticipantsOrder)

    val establishedCall: ObserveEstablishedCallsUseCase
        get() = ObserveEstablishedCallsUseCaseImpl(
            callRepository = callRepository,
        )

    val getIncomingCalls: GetIncomingCallsUseCase
        get() = GetIncomingCallsUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
        )
    val observeOutgoingCall: ObserveOutgoingCallUseCase
        get() = ObserveOutgoingCallUseCaseImpl(
            callRepository = callRepository
        )

    val isCallRunning: IsCallRunningUseCase
        get() = IsCallRunningUseCase(
            callRepository = callRepository
        )

    val observeOngoingCalls: ObserveOngoingCallsUseCase
        get() = ObserveOngoingCallsUseCaseImpl(
            callRepository = callRepository,
        )

    val observeOngoingAndIncomingCalls: ObserveOngoingAndIncomingCallsUseCase by lazy {
        ObserveOngoingAndIncomingCallsUseCaseImpl(callRepository = callRepository)
    }

    val observeEstablishedCallWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase
        get() = ObserveEstablishedCallWithSortedParticipantsUseCaseImpl(
            callRepository = callRepository,
            callingParticipantsOrder = callingParticipantsOrder
        )

    val startCall: StartCallUseCase
        get() = StartCallUseCase(
            callManager = callManager,
            syncManager = syncManager.value,
            callRepository = callRepository,
            answerCall = answerCall,
            getCallConversationType = getCallConversationType,
            kaliumConfigs = kaliumConfigs
        )

    val answerCall: AnswerCallUseCase
        get() = AnswerCallUseCaseImpl(
            observeOngoingAndIncomingCalls,
            callManager,
            muteCall,
            kaliumConfigs
        )

    val endCall: EndCallUseCase
        get() = EndCallUseCaseImpl(
            callManager = callManager,
            callRepository = callRepository,
            endCallListener = EndCallResultListenerImpl,
            shouldAskCallFeedback = shouldAskCallFeedback
        )

    val endCallOnConversationChange: EndCallOnConversationChangeUseCase
        get() = EndCallOnConversationChangeUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            endCallUseCase = endCall,
            endCallListener = EndCallResultListenerImpl
        )

    val updateConversationClientsForCurrentCallUseCase: UpdateConversationClientsForCurrentCallUseCase
        get() = UpdateConversationClientsForCurrentCallUseCaseImpl(
            callRepository,
            conversationClientsInCallUpdater
        )

    val rejectCall: RejectCallUseCase get() = RejectCallUseCase(callManager, callRepository, KaliumDispatcherImpl)

    val muteCall: MuteCallUseCase get() = MuteCallUseCaseImpl(callManager, callRepository)

    val unMuteCall: UnMuteCallUseCase get() = UnMuteCallUseCaseImpl(callManager, callRepository)

    val updateVideoState: UpdateVideoStateUseCase get() = UpdateVideoStateUseCase(callRepository)
    val setVideoSendState: SetVideoSendStateUseCase get() = SetVideoSendStateUseCase(callManager)

    val setVideoPreview: SetVideoPreviewUseCase get() = SetVideoPreviewUseCase(flowManagerService)
    val setUIRotation: SetUIRotationUseCase get() = SetUIRotationUseCase(flowManagerService)
    val flipToFrontCamera: FlipToFrontCameraUseCase get() = FlipToFrontCameraUseCase(flowManagerService)
    val flipToBackCamera: FlipToBackCameraUseCase get() = FlipToBackCameraUseCase(flowManagerService)

    val setTestVideoType: SetTestVideoTypeUseCase get() = SetTestVideoTypeUseCase(callManager)
    val setTestPreviewActive: SetTestPreviewActiveUseCase get() = SetTestPreviewActiveUseCase(callManager)
    val setTestRemoteVideoStates: SetTestRemoteVideoStatesUseCase get() = SetTestRemoteVideoStatesUseCase(callManager)

    val turnLoudSpeakerOff: TurnLoudSpeakerOffUseCase get() = TurnLoudSpeakerOffUseCase(mediaManagerService)

    val turnLoudSpeakerOn: TurnLoudSpeakerOnUseCase get() = TurnLoudSpeakerOnUseCase(mediaManagerService)

    val observeSpeaker: ObserveSpeakerUseCase get() = ObserveSpeakerUseCase(mediaManagerService)

    private val callingParticipantsOrder: CallingParticipantsOrder
        get() = CallingParticipantsOrderImpl(
            currentClientIdProvider = currentClientIdProvider,
            participantsFilter = ParticipantsFilterImpl(qualifiedIdMapper),
            participantsOrderByName = ParticipantsOrderByNameImpl(),
            selfUserId = selfUserId
        )

    val isLastCallClosed: IsLastCallClosedUseCase get() = IsLastCallClosedUseCaseImpl(callRepository)

    val requestVideoStreams: RequestVideoStreamsUseCase get() = RequestVideoStreamsUseCase(callManager, KaliumDispatcherImpl)

    val isEligibleToStartCall: IsEligibleToStartCallUseCase get() = IsEligibleToStartCallUseCaseImpl(userConfigRepository, callRepository)

    val observeConferenceCallingEnabled: ObserveConferenceCallingEnabledUseCase
        get() = ObserveConferenceCallingEnabledUseCaseImpl(userConfigRepository)

    val observeEndCallDueToDegradationDialog: ObserveEndCallDueToConversationDegradationUseCase
        get() = ObserveEndCallDueToConversationDegradationUseCaseImpl(EndCallResultListenerImpl)
    val observeAskCallFeedbackUseCase: ObserveAskCallFeedbackUseCase
        get() = observeAskCallFeedbackUseCase(EndCallResultListenerImpl)

    private val shouldAskCallFeedback: ShouldAskCallFeedbackUseCase by lazy {
        ShouldAskCallFeedbackUseCase(userConfigRepository)
    }

    val updateNextTimeCallFeedback: UpdateNextTimeCallFeedbackUseCase by lazy {
        UpdateNextTimeCallFeedbackUseCase(userConfigRepository)
    }

    val observeRecentlyEndedCallMetadata: ObserveRecentlyEndedCallMetadataUseCase
        get() = ObserveRecentlyEndedCallMetadataUseCaseImpl(
            callRepository = callRepository
        )

    val observeInCallReactions: ObserveInCallReactionsUseCase
        get() = ObserveInCallReactionsUseCaseImpl(inCallReactionsRepository)

    val observeLastActiveCallWithSortedParticipants: ObserveLastActiveCallWithSortedParticipantsUseCase
        get() = ObserveLastActiveCallWithSortedParticipantsUseCaseImpl(callRepository, callingParticipantsOrder)
}
