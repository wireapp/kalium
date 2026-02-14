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

@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.call

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformRotation
import com.wire.kalium.logic.util.PlatformView

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf

internal actual class GlobalCallManager(
    scope: CoroutineScope,
    networkStateObserver: NetworkStateObserver
) : CallNetworkChangeManager(scope, networkStateObserver) {
    private val flowManagerService = object : FlowManagerService {
        override suspend fun setVideoPreview(conversationId: ConversationId, view: PlatformView) {
            kaliumLogger.w("Calls not supported on iOS: setVideoPreview ignored")
        }
        override suspend fun flipToFrontCamera(conversationId: ConversationId) {
            kaliumLogger.w("Calls not supported on iOS: flipToFrontCamera ignored")
        }
        override suspend fun flipToBackCamera(conversationId: ConversationId) {
            kaliumLogger.w("Calls not supported on iOS: flipToBackCamera ignored")
        }
        override suspend fun setUIRotation(rotation: PlatformRotation) {
            kaliumLogger.w("Calls not supported on iOS: setUIRotation ignored")
        }
        override suspend fun startFlowManager() {
            kaliumLogger.w("Calls not supported on iOS: startFlowManager ignored")
        }
    }

    private val mediaManagerService = object : MediaManagerService {
        override suspend fun turnLoudSpeakerOn() {
            kaliumLogger.w("Calls not supported on iOS: turnLoudSpeakerOn ignored")
        }
        override suspend fun turnLoudSpeakerOff() {
            kaliumLogger.w("Calls not supported on iOS: turnLoudSpeakerOff ignored")
        }
        override fun observeSpeaker() = flowOf(false)
        override suspend fun startMediaManager() {
            kaliumLogger.w("Calls not supported on iOS: startMediaManager ignored")
        }
    }

    @Suppress("LongParameterList")
    internal actual fun getCallManagerForClient(
        userId: QualifiedID,
        callRepository: CallRepository,
        currentClientIdProvider: CurrentClientIdProvider,
        selfConversationIdProvider: SelfConversationIdProvider,
        conversationRepository: ConversationRepository,
        userConfigRepository: UserConfigRepository,
        messageSender: MessageSender,
        callMapper: CallMapper,
        federatedIdMapper: FederatedIdMapper,
        qualifiedIdMapper: QualifiedIdMapper,
        videoStateChecker: VideoStateChecker,
        conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
        getCallConversationType: GetCallConversationTypeProvider,
        networkStateObserver: NetworkStateObserver,
        kaliumConfigs: KaliumConfigs,
        createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase
    ): CallManager {
        return CallManagerImpl()
    }

    actual suspend fun removeInMemoryCallingManagerForUser(userId: UserId) {
        kaliumLogger.w("Calls not supported on iOS: removeInMemoryCallingManagerForUser ignored")
    }

    actual fun getFlowManager(): FlowManagerService {
        return flowManagerService
    }

    actual fun getMediaManager(): MediaManagerService {
        return mediaManagerService
    }

    actual override fun networkChanged() {
        kaliumLogger.w("Calls not supported on iOS: networkChanged ignored")
    }
}
