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

import com.wire.kalium.logic.feature.call.usecase.AnswerCallUseCase
import com.wire.kalium.logic.feature.call.usecase.EndCallOnConversationChangeUseCase
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.FlipToBackCameraUseCase
import com.wire.kalium.logic.feature.call.usecase.FlipToFrontCameraUseCase
import com.wire.kalium.logic.feature.call.usecase.GetIncomingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.IsCallRunningUseCase
import com.wire.kalium.logic.feature.call.usecase.IsEligibleToStartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.IsLastCallClosedUseCase
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveActiveCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveCallModerationActionsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveCallQualityDataUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveConferenceCallingEnabledUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEndCallDueToConversationDegradationUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveInCallReactionsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveJoinableCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveLastActiveCallWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingAndIncomingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOutgoingCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveSpeakerUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveStaleOpenCallsCleanupUseCase
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.RequestVideoStreamsUseCase
import com.wire.kalium.logic.feature.call.usecase.SetBackgroundUseCase
import com.wire.kalium.logic.feature.call.usecase.SetCallQualityIntervalUseCase
import com.wire.kalium.logic.feature.call.usecase.SetTestPreviewActiveUseCase
import com.wire.kalium.logic.feature.call.usecase.SetTestRemoteVideoStatesUseCase
import com.wire.kalium.logic.feature.call.usecase.SetTestVideoTypeUseCase
import com.wire.kalium.logic.feature.call.usecase.SetUIRotationUseCase
import com.wire.kalium.logic.feature.call.usecase.SetVideoPreviewUseCase
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOffUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOnUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.UpdateConversationClientsForCurrentCallUseCase
import com.wire.kalium.logic.feature.call.usecase.observeAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.call.usecase.video.SetVideoSendStateUseCase
import com.wire.kalium.logic.feature.call.usecase.video.UpdateVideoStateUseCase
import com.wire.kalium.logic.feature.user.UpdateNextTimeCallFeedbackUseCase
import com.wire.kalium.util.KaliumDispatcher

/** Compatibility facade over its own fresh operation graph. */
public class CallsScope internal constructor(private val api: LegacyCallsApi) {
    public val allCallsWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase
        get() = api.callsAllCallsWithSortedParticipants

    public val establishedCall: ObserveEstablishedCallsUseCase
        get() = api.callsEstablishedCall

    public val getIncomingCalls: GetIncomingCallsUseCase
        get() = api.callsGetIncomingCalls

    public val observeOutgoingCall: ObserveOutgoingCallUseCase
        get() = api.callsObserveOutgoingCall

    public val isCallRunning: IsCallRunningUseCase
        get() = api.callsIsCallRunning

    public val observeOngoingCalls: ObserveOngoingCallsUseCase
        get() = api.callsObserveOngoingCalls

    public val observeJoinableCalls: ObserveJoinableCallsUseCase
        get() = api.callsObserveJoinableCalls

    internal val observeOngoingAndIncomingCalls: ObserveOngoingAndIncomingCallsUseCase
        get() = api.callsObserveOngoingAndIncomingCalls

    public val observeEstablishedCallWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase
        get() = api.callsObserveEstablishedCallWithSortedParticipants

    public val observeActiveCalls: ObserveActiveCallsUseCase
        get() = api.callsObserveActiveCalls

    public val startCall: StartCallUseCase
        get() = api.callsStartCall

    public val answerCall: AnswerCallUseCase
        get() = api.callsAnswerCall

    public val endCall: EndCallUseCase
        get() = api.callsEndCall

    public val endCallOnConversationChange: EndCallOnConversationChangeUseCase
        get() = api.callsEndCallOnConversationChange

    internal val updateConversationClientsForCurrentCallUseCase: UpdateConversationClientsForCurrentCallUseCase
        get() = api.callsUpdateConversationClientsForCurrentCallUseCase

    public val rejectCall: RejectCallUseCase
        get() = api.callsRejectCall

    public val muteCall: MuteCallUseCase
        get() = api.callsMuteCall

    public val unMuteCall: UnMuteCallUseCase
        get() = api.callsUnMuteCall

    public val updateVideoState: UpdateVideoStateUseCase
        get() = api.callsUpdateVideoState

    public val setVideoSendState: SetVideoSendStateUseCase
        get() = api.callsSetVideoSendState

    public val setVideoPreview: SetVideoPreviewUseCase
        get() = api.callsSetVideoPreview

    public val setUIRotation: SetUIRotationUseCase
        get() = api.callsSetUIRotation

    public val flipToFrontCamera: FlipToFrontCameraUseCase
        get() = api.callsFlipToFrontCamera

    public val flipToBackCamera: FlipToBackCameraUseCase
        get() = api.callsFlipToBackCamera

    public val setTestVideoType: SetTestVideoTypeUseCase
        get() = api.callsSetTestVideoType

    public val setTestPreviewActive: SetTestPreviewActiveUseCase
        get() = api.callsSetTestPreviewActive

    public val setTestRemoteVideoStates: SetTestRemoteVideoStatesUseCase
        get() = api.callsSetTestRemoteVideoStates

    public val turnLoudSpeakerOff: TurnLoudSpeakerOffUseCase
        get() = api.callsTurnLoudSpeakerOff

    public val turnLoudSpeakerOn: TurnLoudSpeakerOnUseCase
        get() = api.callsTurnLoudSpeakerOn

    public val observeSpeaker: ObserveSpeakerUseCase
        get() = api.callsObserveSpeaker

    public val isLastCallClosed: IsLastCallClosedUseCase
        get() = api.callsIsLastCallClosed

    public val requestVideoStreams: RequestVideoStreamsUseCase
        get() = api.callsRequestVideoStreams

    public val isEligibleToStartCall: IsEligibleToStartCallUseCase
        get() = api.callsIsEligibleToStartCall

    public val observeConferenceCallingEnabled: ObserveConferenceCallingEnabledUseCase
        get() = api.callsObserveConferenceCallingEnabled

    public val observeEndCallDueToDegradationDialog: ObserveEndCallDueToConversationDegradationUseCase
        get() = api.callsObserveEndCallDueToDegradationDialog

    public val observeAskCallFeedbackUseCase: ObserveAskCallFeedbackUseCase
        get() = api.callsObserveAskCallFeedbackUseCase

    public val updateNextTimeCallFeedback: UpdateNextTimeCallFeedbackUseCase
        get() = api.callsUpdateNextTimeCallFeedback

    public val observeRecentlyEndedCallMetadata: ObserveRecentlyEndedCallMetadataUseCase
        get() = api.callsObserveRecentlyEndedCallMetadata

    public val observeInCallReactions: ObserveInCallReactionsUseCase
        get() = api.callsObserveInCallReactions

    public val observeLastActiveCallWithSortedParticipants: ObserveLastActiveCallWithSortedParticipantsUseCase
        get() = api.callsObserveLastActiveCallWithSortedParticipants

    public val setBackground: SetBackgroundUseCase
        get() = api.callsSetBackground

    public val observeCallQualityData: ObserveCallQualityDataUseCase
        get() = api.callsObserveCallQualityData

    public val setCallQualityInterval: SetCallQualityIntervalUseCase
        get() = api.callsSetCallQualityInterval

    public val observeCallModerationActions: ObserveCallModerationActionsUseCase
        get() = api.callsObserveCallModerationActions

    public val observeStaleOpenCallsCleanup: ObserveStaleOpenCallsCleanupUseCase
        get() = api.callsObserveStaleOpenCallsCleanup

    internal val dispatcher: KaliumDispatcher
        get() = api.callsDispatcher

}
