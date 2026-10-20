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
@file:OptIn(InternalKaliumApi::class, DelicateKaliumApi::class)

package com.wire.kalium.logic.di

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallModerationActionsRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.InCallReactionsRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.CallBackgroundManager
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.feature.call.LegacyCallsApi
import com.wire.kalium.logic.feature.call.LegacyCallsDependencies
import com.wire.kalium.logic.feature.call.LegacyCallsGraph
import com.wire.kalium.logic.feature.call.MediaManagerService
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.MuteCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.util.DelicateKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides

/** Fresh operation ownership only: no CallsScope construction and no use-case forwarding port.
 * Eager capture order is the original public calls getter's argument order. A second occurrence
 * resolves a second operation graph, even within one root constructor (for example logout).
 */
@Suppress("LongParameterList")
internal interface UserSessionCallOperationBindings {
    @Provides
    @UserSessionBinding("freshCallsOperation")
    fun userSessionProvideFreshCallsOperation(
        factory: LegacyCallsGraph.Factory,
        @UserSessionBinding("callManager")
        callManager: Provider<UserSessionValue<Lazy<CallManager>>>,
        @UserSessionBinding("callBackgroundManager")
        callBackgroundManager: Provider<CallBackgroundManager>,
        @UserSessionBinding("callRepository")
        callRepository: Provider<CallRepository>,
        @UserSessionBinding("conversationRepository")
        conversationRepository: Provider<ConversationRepository>,
        @UserSessionBinding("flowManagerService")
        flowManagerService: Provider<FlowManagerService>,
        @UserSessionBinding("mediaManagerService")
        mediaManagerService: Provider<MediaManagerService>,
        @UserSessionBinding("syncStateObserver")
        syncStateObserver: Provider<UserSessionValue<Lazy<SyncStateObserver>>>,
        @UserSessionBinding("qualifiedIdMapper")
        qualifiedIdMapper: Provider<QualifiedIdMapper>,
        @UserSessionBinding("clientIdProvider")
        clientIdProvider: Provider<CurrentClientIdProvider>,
        @UserSessionBinding("userConfigRepository")
        userConfigRepository: Provider<UserConfigRepository>,
        @UserSessionBinding("getCallConversationType")
        getCallConversationType: Provider<GetCallConversationTypeProvider>,
        @UserSessionBinding("conversationClientsInCallUpdater")
        conversationClientsInCallUpdater: Provider<ConversationClientsInCallUpdater>,
        @UserSessionBinding("kaliumConfigs")
        kaliumConfigs: Provider<KaliumConfigs>,
        @UserSessionBinding("inCallReactionsRepository")
        inCallReactionsRepository: Provider<InCallReactionsRepository>,
        @UserSessionBinding("callModerationActionsRepository")
        callModerationActionsRepository: Provider<CallModerationActionsRepository>,
        @UserSessionBinding("userId")
        userId: Provider<UserId>,
        @UserSessionBinding("userRepository")
        userRepository: Provider<UserRepository>,
    ): LegacyCallsApi =
        factory.create(object : LegacyCallsDependencies {
            override val callManager: Lazy<CallManager> = callManager().value
            override val callBackgroundManager: CallBackgroundManager = callBackgroundManager()
            override val callRepository: CallRepository = callRepository()
            override val conversationRepository: ConversationRepository = conversationRepository()
            override val flowManagerService: FlowManagerService = flowManagerService()
            override val mediaManagerService: MediaManagerService = mediaManagerService()
            override val syncStateObserver: Lazy<SyncStateObserver> = syncStateObserver().value
            override val qualifiedIdMapper: QualifiedIdMapper = qualifiedIdMapper()
            override val currentClientIdProvider: CurrentClientIdProvider = clientIdProvider()
            override val userConfigRepository: UserConfigRepository = userConfigRepository()
            override val getCallConversationType: GetCallConversationTypeProvider = getCallConversationType()
            override val conversationClientsInCallUpdater: ConversationClientsInCallUpdater = conversationClientsInCallUpdater()
            override val kaliumConfigs: KaliumConfigs = kaliumConfigs()
            override val inCallReactionsRepository: InCallReactionsRepository = inCallReactionsRepository()
            override val callModerationActionsRepository: CallModerationActionsRepository = callModerationActionsRepository()
            override val selfUserId: UserId = userId()
            override val userRepository: UserRepository = userRepository()
        })

    @Provides
    @UserSessionBinding("freshMuteCall")
    fun userSessionProvideFreshMuteCall(
        @UserSessionBinding("freshCallsOperation")
        operation: Provider<LegacyCallsApi>,
    ): MuteCallUseCase = operation().callsMuteCall

    @Provides
    @UserSessionBinding("freshEstablishedCalls")
    fun userSessionProvideFreshEstablishedCalls(
        @UserSessionBinding("freshCallsOperation")
        operation: Provider<LegacyCallsApi>,
    ): ObserveEstablishedCallsUseCase = operation().callsEstablishedCall

    @Provides
    @UserSessionBinding("freshEndCall")
    fun userSessionProvideFreshEndCall(
        @UserSessionBinding("freshCallsOperation")
        operation: Provider<LegacyCallsApi>,
    ): EndCallUseCase = operation().callsEndCall

}
