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
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
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
import com.wire.kalium.logic.di.KaliumCallsApi
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
import com.wire.kalium.logic.feature.call.usecase.ObserveActiveCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveActiveCallsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveAskCallFeedbackUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveCallModerationActionsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveCallModerationActionsUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.ObserveCallQualityDataUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveCallQualityDataUseCaseImpl
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
import com.wire.kalium.logic.feature.call.usecase.ObserveJoinableCallsUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveJoinableCallsUseCaseImpl
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
import com.wire.kalium.logic.feature.call.usecase.ObserveStaleOpenCallsCleanupUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveStaleOpenCallsCleanupUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.RejectCallUseCase
import com.wire.kalium.logic.feature.call.usecase.RequestVideoStreamsUseCase
import com.wire.kalium.logic.feature.call.usecase.SetBackgroundUseCase
import com.wire.kalium.logic.feature.call.usecase.SetBackgroundUseCaseImpl
import com.wire.kalium.logic.feature.call.usecase.SetCallQualityIntervalUseCase
import com.wire.kalium.logic.feature.call.usecase.SetCallQualityIntervalUseCaseImpl
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
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.Qualifier
import dev.zacsweers.metro.SingleIn

// Wrappers preserve the exact captured Lazy instances without asking Metro to synthesize Lazy<T>.
internal class CapturedCallManager(val value: Lazy<CallManager>)
internal class CapturedCallSyncObserver(val value: Lazy<SyncStateObserver>)

internal abstract class CallsFacadeLifetime private constructor()

@Qualifier
internal annotation class LegacyCallsBinding(val value: String)

internal interface LegacyCallsDependencies {
    val callManager: Lazy<CallManager>
    val callBackgroundManager: CallBackgroundManager
    val callRepository: CallRepository
    val conversationRepository: ConversationRepository
    val userRepository: UserRepository
    val flowManagerService: FlowManagerService
    val mediaManagerService: MediaManagerService
    val syncStateObserver: Lazy<SyncStateObserver>
    val qualifiedIdMapper: QualifiedIdMapper
    val currentClientIdProvider: CurrentClientIdProvider
    val userConfigRepository: UserConfigRepository
    val conversationClientsInCallUpdater: ConversationClientsInCallUpdater
    val getCallConversationType: GetCallConversationTypeProvider
    val kaliumConfigs: KaliumConfigs
    val inCallReactionsRepository: InCallReactionsRepository
    val callModerationActionsRepository: CallModerationActionsRepository
    val selfUserId: UserId
    val dispatcher: KaliumDispatcher get() = KaliumDispatcherImpl
}

internal interface LegacyCallsApi : KaliumCallsApi {
    @get:LegacyCallsBinding("callManager")
    val callsCallManager: CapturedCallManager

    @get:LegacyCallsBinding("callBackgroundManager")
    val callsCallBackgroundManager: CallBackgroundManager

    @get:LegacyCallsBinding("callRepository")
    val callsCallRepository: CallRepository

    @get:LegacyCallsBinding("conversationRepository")
    val callsConversationRepository: ConversationRepository

    @get:LegacyCallsBinding("userRepository")
    val callsUserRepository: UserRepository

    @get:LegacyCallsBinding("flowManagerService")
    val callsFlowManagerService: FlowManagerService

    @get:LegacyCallsBinding("mediaManagerService")
    val callsMediaManagerService: MediaManagerService

    @get:LegacyCallsBinding("syncStateObserver")
    val callsSyncStateObserver: CapturedCallSyncObserver

    @get:LegacyCallsBinding("qualifiedIdMapper")
    val callsQualifiedIdMapper: QualifiedIdMapper

    @get:LegacyCallsBinding("currentClientIdProvider")
    val callsCurrentClientIdProvider: CurrentClientIdProvider

    @get:LegacyCallsBinding("userConfigRepository")
    val callsUserConfigRepository: UserConfigRepository

    @get:LegacyCallsBinding("conversationClientsInCallUpdater")
    val callsConversationClientsInCallUpdater: ConversationClientsInCallUpdater

    @get:LegacyCallsBinding("getCallConversationType")
    val callsGetCallConversationType: GetCallConversationTypeProvider

    @get:LegacyCallsBinding("kaliumConfigs")
    val callsKaliumConfigs: KaliumConfigs

    @get:LegacyCallsBinding("inCallReactionsRepository")
    val callsInCallReactionsRepository: InCallReactionsRepository

    @get:LegacyCallsBinding("callModerationActionsRepository")
    val callsCallModerationActionsRepository: CallModerationActionsRepository

    @get:LegacyCallsBinding("selfUserId")
    val callsSelfUserId: UserId

    @get:LegacyCallsBinding("dispatcher")
    val callsDispatcher: KaliumDispatcher

    @get:LegacyCallsBinding("allCallsWithSortedParticipants")
    val callsAllCallsWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase

    @get:LegacyCallsBinding("establishedCall")
    override val callsEstablishedCall: ObserveEstablishedCallsUseCase

    @get:LegacyCallsBinding("getIncomingCalls")
    override val callsGetIncomingCalls: GetIncomingCallsUseCase

    @get:LegacyCallsBinding("observeOutgoingCall")
    override val callsObserveOutgoingCall: ObserveOutgoingCallUseCase

    @get:LegacyCallsBinding("isCallRunning")
    override val callsIsCallRunning: IsCallRunningUseCase

    @get:LegacyCallsBinding("observeOngoingCalls")
    override val callsObserveOngoingCalls: ObserveOngoingCallsUseCase

    @get:LegacyCallsBinding("observeJoinableCalls")
    override val callsObserveJoinableCalls: ObserveJoinableCallsUseCase

    @get:LegacyCallsBinding("observeOngoingAndIncomingCalls")
    val callsObserveOngoingAndIncomingCalls: ObserveOngoingAndIncomingCallsUseCase

    @get:LegacyCallsBinding("observeEstablishedCallWithSortedParticipants")
    override val callsObserveEstablishedCallWithSortedParticipants: ObserveEstablishedCallWithSortedParticipantsUseCase

    @get:LegacyCallsBinding("observeActiveCalls")
    override val callsObserveActiveCalls: ObserveActiveCallsUseCase

    @get:LegacyCallsBinding("startCall")
    override val callsStartCall: StartCallUseCase

    @get:LegacyCallsBinding("answerCall")
    override val callsAnswerCall: AnswerCallUseCase

    @get:LegacyCallsBinding("endCall")
    override val callsEndCall: EndCallUseCase

    @get:LegacyCallsBinding("endCallOnConversationChange")
    override val callsEndCallOnConversationChange: EndCallOnConversationChangeUseCase

    @get:LegacyCallsBinding("updateConversationClientsForCurrentCallUseCase")
    val callsUpdateConversationClientsForCurrentCallUseCase: UpdateConversationClientsForCurrentCallUseCase

    @get:LegacyCallsBinding("rejectCall")
    override val callsRejectCall: RejectCallUseCase

    @get:LegacyCallsBinding("muteCall")
    override val callsMuteCall: MuteCallUseCase

    @get:LegacyCallsBinding("unMuteCall")
    override val callsUnMuteCall: UnMuteCallUseCase

    @get:LegacyCallsBinding("updateVideoState")
    override val callsUpdateVideoState: UpdateVideoStateUseCase

    @get:LegacyCallsBinding("setVideoSendState")
    override val callsSetVideoSendState: SetVideoSendStateUseCase

    @get:LegacyCallsBinding("setVideoPreview")
    override val callsSetVideoPreview: SetVideoPreviewUseCase

    @get:LegacyCallsBinding("setUIRotation")
    override val callsSetUIRotation: SetUIRotationUseCase

    @get:LegacyCallsBinding("flipToFrontCamera")
    override val callsFlipToFrontCamera: FlipToFrontCameraUseCase

    @get:LegacyCallsBinding("flipToBackCamera")
    override val callsFlipToBackCamera: FlipToBackCameraUseCase

    @get:LegacyCallsBinding("setTestVideoType")
    val callsSetTestVideoType: SetTestVideoTypeUseCase

    @get:LegacyCallsBinding("setTestPreviewActive")
    val callsSetTestPreviewActive: SetTestPreviewActiveUseCase

    @get:LegacyCallsBinding("setTestRemoteVideoStates")
    val callsSetTestRemoteVideoStates: SetTestRemoteVideoStatesUseCase

    @get:LegacyCallsBinding("turnLoudSpeakerOff")
    override val callsTurnLoudSpeakerOff: TurnLoudSpeakerOffUseCase

    @get:LegacyCallsBinding("turnLoudSpeakerOn")
    override val callsTurnLoudSpeakerOn: TurnLoudSpeakerOnUseCase

    @get:LegacyCallsBinding("observeSpeaker")
    override val callsObserveSpeaker: ObserveSpeakerUseCase

    @get:LegacyCallsBinding("callingParticipantsOrder")
    val callsCallingParticipantsOrder: CallingParticipantsOrder

    @get:LegacyCallsBinding("isLastCallClosed")
    override val callsIsLastCallClosed: IsLastCallClosedUseCase

    @get:LegacyCallsBinding("requestVideoStreams")
    override val callsRequestVideoStreams: RequestVideoStreamsUseCase

    @get:LegacyCallsBinding("isEligibleToStartCall")
    override val callsIsEligibleToStartCall: IsEligibleToStartCallUseCase

    @get:LegacyCallsBinding("observeConferenceCallingEnabled")
    override val callsObserveConferenceCallingEnabled: ObserveConferenceCallingEnabledUseCase

    @get:LegacyCallsBinding("observeEndCallDueToDegradationDialog")
    val callsObserveEndCallDueToDegradationDialog: ObserveEndCallDueToConversationDegradationUseCase

    @get:LegacyCallsBinding("observeAskCallFeedbackUseCase")
    val callsObserveAskCallFeedbackUseCase: ObserveAskCallFeedbackUseCase

    @get:LegacyCallsBinding("shouldAskCallFeedback")
    val callsShouldAskCallFeedback: ShouldAskCallFeedbackUseCase

    @get:LegacyCallsBinding("updateNextTimeCallFeedback")
    val callsUpdateNextTimeCallFeedback: UpdateNextTimeCallFeedbackUseCase

    @get:LegacyCallsBinding("observeRecentlyEndedCallMetadata")
    val callsObserveRecentlyEndedCallMetadata: ObserveRecentlyEndedCallMetadataUseCase

    @get:LegacyCallsBinding("observeInCallReactions")
    override val callsObserveInCallReactions: ObserveInCallReactionsUseCase

    @get:LegacyCallsBinding("observeLastActiveCallWithSortedParticipants")
    override val callsObserveLastActiveCallWithSortedParticipants: ObserveLastActiveCallWithSortedParticipantsUseCase

    @get:LegacyCallsBinding("setBackground")
    val callsSetBackground: SetBackgroundUseCase

    @get:LegacyCallsBinding("observeCallQualityData")
    override val callsObserveCallQualityData: ObserveCallQualityDataUseCase

    @get:LegacyCallsBinding("setCallQualityInterval")
    override val callsSetCallQualityInterval: SetCallQualityIntervalUseCase

    @get:LegacyCallsBinding("observeCallModerationActions")
    override val callsObserveCallModerationActions: ObserveCallModerationActionsUseCase

    @get:LegacyCallsBinding("observeStaleOpenCallsCleanup")
    val callsObserveStaleOpenCallsCleanup: ObserveStaleOpenCallsCleanupUseCase
}

/** Individual bindings retained only by an existing fresh facade owner. */
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal interface LegacyCallsBindings : LegacyCallsApi {
    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("callManager")
    fun provideCallsCallManager(dependencies: LegacyCallsDependencies): CapturedCallManager =
        CapturedCallManager(dependencies.callManager)

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("callBackgroundManager")
    fun provideCallsCallBackgroundManager(dependencies: LegacyCallsDependencies): CallBackgroundManager =
        dependencies.callBackgroundManager

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("callRepository")
    fun provideCallsCallRepository(dependencies: LegacyCallsDependencies): CallRepository =
        dependencies.callRepository

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("conversationRepository")
    fun provideCallsConversationRepository(dependencies: LegacyCallsDependencies): ConversationRepository =
        dependencies.conversationRepository

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("userRepository")
    fun provideCallsUserRepository(dependencies: LegacyCallsDependencies): UserRepository =
        dependencies.userRepository

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("flowManagerService")
    fun provideCallsFlowManagerService(dependencies: LegacyCallsDependencies): FlowManagerService =
        dependencies.flowManagerService

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("mediaManagerService")
    fun provideCallsMediaManagerService(dependencies: LegacyCallsDependencies): MediaManagerService =
        dependencies.mediaManagerService

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("syncStateObserver")
    fun provideCallsSyncStateObserver(dependencies: LegacyCallsDependencies): CapturedCallSyncObserver =
        CapturedCallSyncObserver(dependencies.syncStateObserver)

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("qualifiedIdMapper")
    fun provideCallsQualifiedIdMapper(dependencies: LegacyCallsDependencies): QualifiedIdMapper =
        dependencies.qualifiedIdMapper

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("currentClientIdProvider")
    fun provideCallsCurrentClientIdProvider(dependencies: LegacyCallsDependencies): CurrentClientIdProvider =
        dependencies.currentClientIdProvider

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("userConfigRepository")
    fun provideCallsUserConfigRepository(dependencies: LegacyCallsDependencies): UserConfigRepository =
        dependencies.userConfigRepository

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("conversationClientsInCallUpdater")
    fun provideCallsConversationClientsInCallUpdater(dependencies: LegacyCallsDependencies): ConversationClientsInCallUpdater =
        dependencies.conversationClientsInCallUpdater

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("getCallConversationType")
    fun provideCallsGetCallConversationType(dependencies: LegacyCallsDependencies): GetCallConversationTypeProvider =
        dependencies.getCallConversationType

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("kaliumConfigs")
    fun provideCallsKaliumConfigs(dependencies: LegacyCallsDependencies): KaliumConfigs =
        dependencies.kaliumConfigs

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("inCallReactionsRepository")
    fun provideCallsInCallReactionsRepository(dependencies: LegacyCallsDependencies): InCallReactionsRepository =
        dependencies.inCallReactionsRepository

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("callModerationActionsRepository")
    fun provideCallsCallModerationActionsRepository(dependencies: LegacyCallsDependencies): CallModerationActionsRepository =
        dependencies.callModerationActionsRepository

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("selfUserId")
    fun provideCallsSelfUserId(dependencies: LegacyCallsDependencies): UserId =
        dependencies.selfUserId

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("dispatcher")
    fun provideCallsDispatcher(dependencies: LegacyCallsDependencies): KaliumDispatcher =
        dependencies.dispatcher

    @Provides
    @LegacyCallsBinding("allCallsWithSortedParticipants")
    fun provideCallsAllCallsWithSortedParticipants(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("callingParticipantsOrder")
        callingParticipantsOrder: Lazy<CallingParticipantsOrder>,
    ): ObserveEstablishedCallWithSortedParticipantsUseCase =
        ObserveEstablishedCallWithSortedParticipantsUseCaseImpl(callRepository, callingParticipantsOrder.value)

    @Provides
    @LegacyCallsBinding("establishedCall")
    fun provideCallsEstablishedCall(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveEstablishedCallsUseCase =
        ObserveEstablishedCallsUseCaseImpl(
            callRepository = callRepository,
        )

    @Provides
    @LegacyCallsBinding("getIncomingCalls")
    fun provideCallsGetIncomingCalls(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("conversationRepository")
        conversationRepository: ConversationRepository,
        @LegacyCallsBinding("userRepository")
        userRepository: UserRepository,
    ): GetIncomingCallsUseCase =
        GetIncomingCallsUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            userRepository = userRepository,
        )

    @Provides
    @LegacyCallsBinding("observeOutgoingCall")
    fun provideCallsObserveOutgoingCall(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveOutgoingCallUseCase =
        ObserveOutgoingCallUseCaseImpl(
            callRepository = callRepository
        )

    @Provides
    @LegacyCallsBinding("isCallRunning")
    fun provideCallsIsCallRunning(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): IsCallRunningUseCase =
        IsCallRunningUseCase(
            callRepository = callRepository
        )

    @Provides
    @LegacyCallsBinding("observeOngoingCalls")
    fun provideCallsObserveOngoingCalls(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveOngoingCallsUseCase =
        ObserveOngoingCallsUseCaseImpl(
            callRepository = callRepository,
        )

    @Provides
    @LegacyCallsBinding("observeJoinableCalls")
    fun provideCallsObserveJoinableCalls(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveJoinableCallsUseCase =
        ObserveJoinableCallsUseCaseImpl(
            callRepository = callRepository,
        )

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("observeOngoingAndIncomingCalls")
    fun provideCallsObserveOngoingAndIncomingCalls(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveOngoingAndIncomingCallsUseCase =
        ObserveOngoingAndIncomingCallsUseCaseImpl(callRepository = callRepository)

    @Provides
    @LegacyCallsBinding("observeEstablishedCallWithSortedParticipants")
    fun provideCallsObserveEstablishedCallWithSortedParticipants(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("callingParticipantsOrder")
        callingParticipantsOrder: Lazy<CallingParticipantsOrder>,
    ): ObserveEstablishedCallWithSortedParticipantsUseCase =
        ObserveEstablishedCallWithSortedParticipantsUseCaseImpl(
            callRepository = callRepository,
            callingParticipantsOrder = callingParticipantsOrder.value
        )

    @Provides
    @LegacyCallsBinding("observeActiveCalls")
    fun provideCallsObserveActiveCalls(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveActiveCallsUseCase =
        ObserveActiveCallsUseCaseImpl(
            callRepository = callRepository
        )

    @Provides
    @LegacyCallsBinding("startCall")
    fun provideCallsStartCall(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("syncStateObserver")
        syncStateObserver: CapturedCallSyncObserver,
        @LegacyCallsBinding("getCallConversationType")
        getCallConversationType: GetCallConversationTypeProvider,
        @LegacyCallsBinding("kaliumConfigs")
        kaliumConfigs: KaliumConfigs,
        @LegacyCallsBinding("answerCall")
        answerCall: Lazy<AnswerCallUseCase>,
    ): StartCallUseCase =
        StartCallUseCase(
            callManager = callManager.value,
            syncStateObserver = syncStateObserver.value.value,
            callRepository = callRepository,
            answerCall = answerCall.value,
            getCallConversationType = getCallConversationType,
            kaliumConfigs = kaliumConfigs
        )

    @Provides
    @LegacyCallsBinding("answerCall")
    fun provideCallsAnswerCall(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("kaliumConfigs")
        kaliumConfigs: KaliumConfigs,
        @LegacyCallsBinding("observeOngoingAndIncomingCalls")
        observeOngoingAndIncomingCalls: Lazy<ObserveOngoingAndIncomingCallsUseCase>,
        @LegacyCallsBinding("muteCall")
        muteCall: Lazy<MuteCallUseCase>,
    ): AnswerCallUseCase =
        AnswerCallUseCaseImpl(
            observeOngoingAndIncomingCalls.value,
            callManager.value,
            muteCall.value,
            kaliumConfigs
        )

    @Provides
    @LegacyCallsBinding("endCall")
    fun provideCallsEndCall(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("shouldAskCallFeedback")
        shouldAskCallFeedback: Lazy<ShouldAskCallFeedbackUseCase>,
    ): EndCallUseCase =
        EndCallUseCaseImpl(
            callManager = callManager.value,
            callRepository = callRepository,
            endCallListener = EndCallResultListenerImpl,
            shouldAskCallFeedback = shouldAskCallFeedback.value
        )

    @Provides
    @LegacyCallsBinding("endCallOnConversationChange")
    fun provideCallsEndCallOnConversationChange(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("conversationRepository")
        conversationRepository: ConversationRepository,
        @LegacyCallsBinding("endCall")
        endCall: Lazy<EndCallUseCase>,
    ): EndCallOnConversationChangeUseCase =
        EndCallOnConversationChangeUseCaseImpl(
            callRepository = callRepository,
            conversationRepository = conversationRepository,
            endCallUseCase = endCall.value,
            endCallListener = EndCallResultListenerImpl
        )

    @Provides
    @LegacyCallsBinding("updateConversationClientsForCurrentCallUseCase")
    fun provideCallsUpdateConversationClientsForCurrentCallUseCase(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("conversationClientsInCallUpdater")
        conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
    ): UpdateConversationClientsForCurrentCallUseCase =
        UpdateConversationClientsForCurrentCallUseCaseImpl(
            callRepository,
            conversationClientsInCallUpdater
        )

    @Provides
    @LegacyCallsBinding("rejectCall")
    fun provideCallsRejectCall(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): RejectCallUseCase =
        RejectCallUseCase(callManager.value, callRepository, KaliumDispatcherImpl)

    @Provides
    @LegacyCallsBinding("muteCall")
    fun provideCallsMuteCall(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): MuteCallUseCase =
        MuteCallUseCaseImpl(callManager.value, callRepository)

    @Provides
    @LegacyCallsBinding("unMuteCall")
    fun provideCallsUnMuteCall(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): UnMuteCallUseCase =
        UnMuteCallUseCaseImpl(callManager.value, callRepository)

    @Provides
    @LegacyCallsBinding("updateVideoState")
    fun provideCallsUpdateVideoState(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): UpdateVideoStateUseCase =
        UpdateVideoStateUseCase(callRepository)

    @Provides
    @LegacyCallsBinding("setVideoSendState")
    fun provideCallsSetVideoSendState(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
    ): SetVideoSendStateUseCase =
        SetVideoSendStateUseCase(callManager.value)

    @Provides
    @LegacyCallsBinding("setVideoPreview")
    fun provideCallsSetVideoPreview(
        @LegacyCallsBinding("flowManagerService")
        flowManagerService: FlowManagerService,
    ): SetVideoPreviewUseCase =
        SetVideoPreviewUseCase(flowManagerService)

    @Provides
    @LegacyCallsBinding("setUIRotation")
    fun provideCallsSetUIRotation(
        @LegacyCallsBinding("flowManagerService")
        flowManagerService: FlowManagerService,
    ): SetUIRotationUseCase =
        SetUIRotationUseCase(flowManagerService)

    @Provides
    @LegacyCallsBinding("flipToFrontCamera")
    fun provideCallsFlipToFrontCamera(
        @LegacyCallsBinding("flowManagerService")
        flowManagerService: FlowManagerService,
    ): FlipToFrontCameraUseCase =
        FlipToFrontCameraUseCase(flowManagerService)

    @Provides
    @LegacyCallsBinding("flipToBackCamera")
    fun provideCallsFlipToBackCamera(
        @LegacyCallsBinding("flowManagerService")
        flowManagerService: FlowManagerService,
    ): FlipToBackCameraUseCase =
        FlipToBackCameraUseCase(flowManagerService)

    @Provides
    @LegacyCallsBinding("setTestVideoType")
    fun provideCallsSetTestVideoType(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
    ): SetTestVideoTypeUseCase =
        SetTestVideoTypeUseCase(callManager.value)

    @Provides
    @LegacyCallsBinding("setTestPreviewActive")
    fun provideCallsSetTestPreviewActive(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
    ): SetTestPreviewActiveUseCase =
        SetTestPreviewActiveUseCase(callManager.value)

    @Provides
    @LegacyCallsBinding("setTestRemoteVideoStates")
    fun provideCallsSetTestRemoteVideoStates(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
    ): SetTestRemoteVideoStatesUseCase =
        SetTestRemoteVideoStatesUseCase(callManager.value)

    @Provides
    @LegacyCallsBinding("turnLoudSpeakerOff")
    fun provideCallsTurnLoudSpeakerOff(
        @LegacyCallsBinding("mediaManagerService")
        mediaManagerService: MediaManagerService,
    ): TurnLoudSpeakerOffUseCase =
        TurnLoudSpeakerOffUseCase(mediaManagerService)

    @Provides
    @LegacyCallsBinding("turnLoudSpeakerOn")
    fun provideCallsTurnLoudSpeakerOn(
        @LegacyCallsBinding("mediaManagerService")
        mediaManagerService: MediaManagerService,
    ): TurnLoudSpeakerOnUseCase =
        TurnLoudSpeakerOnUseCase(mediaManagerService)

    @Provides
    @LegacyCallsBinding("observeSpeaker")
    fun provideCallsObserveSpeaker(
        @LegacyCallsBinding("mediaManagerService")
        mediaManagerService: MediaManagerService,
    ): ObserveSpeakerUseCase =
        ObserveSpeakerUseCase(mediaManagerService)

    @Provides
    @LegacyCallsBinding("callingParticipantsOrder")
    fun provideCallsCallingParticipantsOrder(
        @LegacyCallsBinding("qualifiedIdMapper")
        qualifiedIdMapper: QualifiedIdMapper,
        @LegacyCallsBinding("currentClientIdProvider")
        currentClientIdProvider: CurrentClientIdProvider,
        @LegacyCallsBinding("selfUserId")
        selfUserId: UserId,
    ): CallingParticipantsOrder =
        CallingParticipantsOrderImpl(
            currentClientIdProvider = currentClientIdProvider,
            participantsFilter = ParticipantsFilterImpl(qualifiedIdMapper),
            participantsOrderByName = ParticipantsOrderByNameImpl(),
            selfUserId = selfUserId
        )

    @Provides
    @LegacyCallsBinding("isLastCallClosed")
    fun provideCallsIsLastCallClosed(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): IsLastCallClosedUseCase =
        IsLastCallClosedUseCaseImpl(callRepository)

    @Provides
    @LegacyCallsBinding("requestVideoStreams")
    fun provideCallsRequestVideoStreams(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
    ): RequestVideoStreamsUseCase =
        RequestVideoStreamsUseCase(callManager.value, KaliumDispatcherImpl)

    @Provides
    @LegacyCallsBinding("isEligibleToStartCall")
    fun provideCallsIsEligibleToStartCall(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("userConfigRepository")
        userConfigRepository: UserConfigRepository,
    ): IsEligibleToStartCallUseCase =
        IsEligibleToStartCallUseCaseImpl(
            userConfigRepository,
            callRepository
        )

    @Provides
    @LegacyCallsBinding("observeConferenceCallingEnabled")
    fun provideCallsObserveConferenceCallingEnabled(
        @LegacyCallsBinding("userConfigRepository")
        userConfigRepository: UserConfigRepository,
    ): ObserveConferenceCallingEnabledUseCase =
        ObserveConferenceCallingEnabledUseCaseImpl(userConfigRepository)

    @Provides
    @LegacyCallsBinding("observeEndCallDueToDegradationDialog")
    fun provideCallsObserveEndCallDueToDegradationDialog(): ObserveEndCallDueToConversationDegradationUseCase =
        ObserveEndCallDueToConversationDegradationUseCaseImpl(EndCallResultListenerImpl)

    @Provides
    @LegacyCallsBinding("observeAskCallFeedbackUseCase")
    fun provideCallsObserveAskCallFeedbackUseCase(): ObserveAskCallFeedbackUseCase =
        observeAskCallFeedbackUseCase(EndCallResultListenerImpl)

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("shouldAskCallFeedback")
    fun provideCallsShouldAskCallFeedback(
        @LegacyCallsBinding("userConfigRepository")
        userConfigRepository: UserConfigRepository,
    ): ShouldAskCallFeedbackUseCase =
        ShouldAskCallFeedbackUseCase(userConfigRepository)

    @Provides
    @SingleIn(CallsFacadeLifetime::class)
    @LegacyCallsBinding("updateNextTimeCallFeedback")
    fun provideCallsUpdateNextTimeCallFeedback(
        @LegacyCallsBinding("userConfigRepository")
        userConfigRepository: UserConfigRepository,
    ): UpdateNextTimeCallFeedbackUseCase =
        UpdateNextTimeCallFeedbackUseCase(userConfigRepository)

    @Provides
    @LegacyCallsBinding("observeRecentlyEndedCallMetadata")
    fun provideCallsObserveRecentlyEndedCallMetadata(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveRecentlyEndedCallMetadataUseCase =
        ObserveRecentlyEndedCallMetadataUseCaseImpl(
            callRepository = callRepository
        )

    @Provides
    @LegacyCallsBinding("observeInCallReactions")
    fun provideCallsObserveInCallReactions(
        @LegacyCallsBinding("inCallReactionsRepository")
        inCallReactionsRepository: InCallReactionsRepository,
    ): ObserveInCallReactionsUseCase =
        ObserveInCallReactionsUseCaseImpl(inCallReactionsRepository)

    @Provides
    @LegacyCallsBinding("observeLastActiveCallWithSortedParticipants")
    fun provideCallsObserveLastActiveCallWithSortedParticipants(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
        @LegacyCallsBinding("callingParticipantsOrder")
        callingParticipantsOrder: Lazy<CallingParticipantsOrder>,
    ): ObserveLastActiveCallWithSortedParticipantsUseCase =
        ObserveLastActiveCallWithSortedParticipantsUseCaseImpl(callRepository, callingParticipantsOrder.value)

    @Provides
    @LegacyCallsBinding("setBackground")
    fun provideCallsSetBackground(
        @LegacyCallsBinding("callBackgroundManager")
        callBackgroundManager: CallBackgroundManager,
    ): SetBackgroundUseCase =
        SetBackgroundUseCaseImpl(callBackgroundManager = callBackgroundManager)

    @Provides
    @LegacyCallsBinding("observeCallQualityData")
    fun provideCallsObserveCallQualityData(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveCallQualityDataUseCase =
        ObserveCallQualityDataUseCaseImpl(callRepository = callRepository)

    @Provides
    @LegacyCallsBinding("setCallQualityInterval")
    fun provideCallsSetCallQualityInterval(
        @LegacyCallsBinding("callManager")
        callManager: CapturedCallManager,
        @LegacyCallsBinding("dispatcher")
        dispatcher: KaliumDispatcher,
    ): SetCallQualityIntervalUseCase =
        SetCallQualityIntervalUseCaseImpl(callManager = callManager.value, dispatchers = dispatcher)

    @Provides
    @LegacyCallsBinding("observeCallModerationActions")
    fun provideCallsObserveCallModerationActions(
        @LegacyCallsBinding("callModerationActionsRepository")
        callModerationActionsRepository: CallModerationActionsRepository,
    ): ObserveCallModerationActionsUseCase =
        ObserveCallModerationActionsUseCaseImpl(callModerationActionsRepository = callModerationActionsRepository)

    @Provides
    @LegacyCallsBinding("observeStaleOpenCallsCleanup")
    fun provideCallsObserveStaleOpenCallsCleanup(
        @LegacyCallsBinding("callRepository")
        callRepository: CallRepository,
    ): ObserveStaleOpenCallsCleanupUseCase =
        ObserveStaleOpenCallsCleanupUseCaseImpl(callRepository = callRepository)
}

@GraphExtension(CallsFacadeLifetime::class)
internal interface LegacyCallsGraph : LegacyCallsBindings {
    @GraphExtension.Factory
    interface Factory {
        fun create(@Provides dependencies: LegacyCallsDependencies): LegacyCallsGraph
    }
}
