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

import com.wire.kalium.calling.AppleAvs
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.TestVideoType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.util.PlatformContext

import kotlinx.coroutines.CoroutineScope

internal actual class GlobalCallManager actual constructor(
    scope: CoroutineScope,
    networkStateObserver: NetworkStateObserver,
    platformContext: PlatformContext
) : CallNetworkChangeManager(scope, networkStateObserver) {

    private val flowManagerService by lazy { FlowManagerServiceImpl(platformContext) }
    private val mediaManagerService by lazy { MediaManagerServiceImpl(platformContext) }
    private var hasEnabledCallingClient = false

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
        epochInfoUpdater: EpochInfoUpdater,
        getCallConversationType: GetCallConversationTypeProvider,
        networkStateObserver: NetworkStateObserver,
        kaliumConfigs: KaliumConfigs,
        createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase
    ): CallManager {
        if (!kaliumConfigs.enableCalling) {
            kaliumLogger.w("Calls disabled by KaliumConfigs.enableCalling=false: using Apple no-op CallManager")
            return DisabledAppleCallManager
        }

        hasEnabledCallingClient = true
        return CallManagerImpl(
            callRepository = callRepository,
            currentClientIdProvider = currentClientIdProvider,
            selfConversationIdProvider = selfConversationIdProvider,
            messageSender = messageSender,
            callMapper = callMapper,
            federatedIdMapper = federatedIdMapper,
            qualifiedIdMapper = qualifiedIdMapper,
            videoStateChecker = videoStateChecker,
            conversationClientsInCallUpdater = conversationClientsInCallUpdater,
            epochInfoUpdater = epochInfoUpdater,
            networkStateObserver = networkStateObserver,
            getCallConversationType = getCallConversationType,
            userConfigRepository = userConfigRepository,
            kaliumConfigs = kaliumConfigs,
            mediaManagerService = mediaManagerService,
            flowManagerService = flowManagerService,
            createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata,
            selfUserId = userId
        )
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
        if (!hasEnabledCallingClient) {
            kaliumLogger.w("Calls disabled by KaliumConfigs.enableCalling=false: networkChanged ignored")
            return
        }

        if (!AppleAvs.bridge.notifyNetworkChangedIfAvailable()) {
            kaliumLogger.w("AVS iOS smoke: networkChanged ignored because AVS is unavailable")
        }
    }
}

@Suppress("TooManyFunctions")
private object DisabledAppleCallManager : CallManager {
    override suspend fun onCallingMessageReceived(message: Message.Signaling, content: MessageContent.Calling) = Unit

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationTypeCalling: ConversationTypeCalling,
        isAudioCbr: Boolean,
    ) = Unit

    override suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean, isVideoCall: Boolean) = Unit
    override suspend fun endCall(conversationId: ConversationId) = Unit
    override suspend fun rejectCall(conversationId: ConversationId) = Unit
    override suspend fun muteCall(shouldMute: Boolean) = Unit
    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) = Unit
    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) = Unit
    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) = Unit
    override suspend fun updateConversationClients(conversationId: ConversationId, clients: String) = Unit
    override suspend fun reportProcessNotifications(isStarted: Boolean) = Unit
    override suspend fun setTestVideoType(testVideoType: TestVideoType) = Unit
    override suspend fun setTestPreviewActive(shouldEnable: Boolean) = Unit
    override suspend fun setTestRemoteVideoStates(conversationId: ConversationId, participants: List<Participant>) = Unit
    override suspend fun setBackground(background: Boolean) = Unit
    override suspend fun setNetworkQualityInterval(intervalInSeconds: Int) = Unit
    override suspend fun cancelJobs() = Unit
}
