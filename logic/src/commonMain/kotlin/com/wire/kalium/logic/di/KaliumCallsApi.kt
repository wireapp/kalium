/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.di

import com.wire.kalium.logic.feature.call.LegacyCallsDependencies
import com.wire.kalium.logic.feature.call.LegacyCallsGraph
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
import com.wire.kalium.logic.feature.call.usecase.ObserveCallModerationActionsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveCallQualityDataUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveConferenceCallingEnabledUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveInCallReactionsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveJoinableCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveLastActiveCallWithSortedParticipantsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOngoingCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveOutgoingCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveSpeakerUseCase
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.RequestVideoStreamsUseCase
import com.wire.kalium.logic.feature.call.usecase.SetCallQualityIntervalUseCase
import com.wire.kalium.logic.feature.call.usecase.SetUIRotationUseCase
import com.wire.kalium.logic.feature.call.usecase.SetVideoPreviewUseCase
import com.wire.kalium.logic.feature.call.usecase.StartCallUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOffUseCase
import com.wire.kalium.logic.feature.call.usecase.TurnLoudSpeakerOnUseCase
import com.wire.kalium.logic.feature.call.usecase.UnMuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.video.SetVideoSendStateUseCase
import com.wire.kalium.logic.feature.call.usecase.video.UpdateVideoStateUseCase

/** Individual bindings preserve their existing owner; this API does not create a consumer. */
public interface KaliumCallsApi {
    public val callsGetIncomingCalls: GetIncomingCallsUseCase
    public val callsRequestVideoStreams: RequestVideoStreamsUseCase
    public val callsIsLastCallClosed: IsLastCallClosedUseCase
    public val callsObserveOngoingCalls: ObserveOngoingCallsUseCase
    public val callsObserveJoinableCalls: ObserveJoinableCallsUseCase
    public val callsObserveEstablishedCallWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase
    public val callsObserveLastActiveCallWithSortedParticipants: ObserveLastActiveCallWithSortedParticipantsUseCase
    public val callsRejectCall: RejectCallUseCase
    public val callsAnswerCall: AnswerCallUseCase
    public val callsEstablishedCall: ObserveEstablishedCallsUseCase
    public val callsObserveOutgoingCall: ObserveOutgoingCallUseCase
    public val callsObserveActiveCalls: ObserveActiveCallsUseCase
    public val callsStartCall: StartCallUseCase
    public val callsEndCall: EndCallUseCase
    public val callsEndCallOnConversationChange: EndCallOnConversationChangeUseCase
    public val callsMuteCall: MuteCallUseCase
    public val callsUnMuteCall: UnMuteCallUseCase
    public val callsSetVideoPreview: SetVideoPreviewUseCase
    public val callsSetUIRotation: SetUIRotationUseCase
    public val callsFlipToBackCamera: FlipToBackCameraUseCase
    public val callsFlipToFrontCamera: FlipToFrontCameraUseCase
    public val callsTurnLoudSpeakerOff: TurnLoudSpeakerOffUseCase
    public val callsTurnLoudSpeakerOn: TurnLoudSpeakerOnUseCase
    public val callsObserveSpeaker: ObserveSpeakerUseCase
    public val callsUpdateVideoState: UpdateVideoStateUseCase
    public val callsSetVideoSendState: SetVideoSendStateUseCase
    public val callsIsCallRunning: IsCallRunningUseCase
    public val callsIsEligibleToStartCall: IsEligibleToStartCallUseCase
    public val callsObserveConferenceCallingEnabled: ObserveConferenceCallingEnabledUseCase
    public val callsObserveInCallReactions: ObserveInCallReactionsUseCase
    public val callsObserveCallQualityData: ObserveCallQualityDataUseCase
    public val callsSetCallQualityInterval: SetCallQualityIntervalUseCase
    public val callsObserveCallModerationActions: ObserveCallModerationActionsUseCase

    /** Every call creates a fresh graph with the original per-facade captures and caches. */
    public fun interface Factory {
        public fun create(): KaliumCallsApi
    }
}

/** Same generated graph/capture boundary as the compatibility facade. */
internal fun createCallsApi(
    factory: LegacyCallsGraph.Factory,
    dependencies: LegacyCallsDependencies,
): KaliumCallsApi = factory.create(dependencies)
