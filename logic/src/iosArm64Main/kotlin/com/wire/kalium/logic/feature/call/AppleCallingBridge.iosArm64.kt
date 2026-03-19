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

import avs.wcall_library_version
import avs.wcall_network_changed
import avs.wcall_run
import avs.wcall_setup
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.callingLogger
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
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.network.NetworkStateObserver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal actual fun createAppleCallingBridge(
    scope: CoroutineScope,
    networkStateObserver: NetworkStateObserver
): AppleCallingBridge = IosArm64AppleCallingBridge(scope, networkStateObserver)

internal class IosArm64AppleCallingBridge(
    scope: CoroutineScope,
    networkStateObserver: NetworkStateObserver
) : AppleCallingBridge {
    private val stubBridge = StubAppleCallingBridge()
    private val callManagers = mutableMapOf<QualifiedID, CallManager>()
    private val initMutex = Mutex()
    private var initialized = false

    private suspend fun ensureAvsInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            wcall_setup()
            wcall_run()
            val version = runCatching { wcall_library_version() }.getOrNull()
            callingLogger.i("iOS AVS experimental bridge initialized${version?.let { " (version=$it)" } ?: ""}")
            initialized = true
        }
    }

    @Suppress("LongParameterList")
    override fun getCallManagerForClient(
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
        return callManagers.getOrPut(userId) {
            IosArm64ExperimentalCallManager(
                ensureAvsInitialized = ::ensureAvsInitialized,
                selfUserId = userId,
                currentClientIdProvider = currentClientIdProvider,
                federatedIdMapper = federatedIdMapper
            )
        }
    }

    override suspend fun removeInMemoryCallingManagerForUser(userId: UserId) {
        callManagers.remove(userId)?.cancelJobs()
    }

    override fun getFlowManager(): FlowManagerService = stubBridge.getFlowManager()

    override fun getMediaManager(): MediaManagerService = stubBridge.getMediaManager()

    override fun networkChanged() {
        if (initialized) {
            wcall_network_changed()
            callingLogger.i("iOS AVS experimental bridge networkChanged forwarded")
        } else {
            callingLogger.i("iOS AVS experimental bridge networkChanged ignored before initialization")
        }
    }
}

internal class IosArm64ExperimentalCallManager(
    private val ensureAvsInitialized: suspend () -> Unit,
    private val selfUserId: QualifiedID,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val federatedIdMapper: FederatedIdMapper
) : CallManager {
    private val cancelled = CompletableDeferred<Unit>()
    private val handleDeferred: Deferred<UInt> = CompletableDeferred()
    private val handleInitMutex = Mutex()

    private suspend fun ensureHandle(): UInt {
        val deferred = handleDeferred as CompletableDeferred<UInt>
        if (deferred.isCompleted) return deferred.await()
        handleInitMutex.withLock {
            if (deferred.isCompleted) return deferred.await()
            ensureAvsInitialized()
            val clientId = currentClientIdProvider().fold(
                {
                    callingLogger.w("iOS AVS experimental bridge could not resolve client id, using empty client id")
                    ""
                },
                { it.value }
            )
            val handle = avs.wcall_create(
                userid = federatedIdMapper.parseToFederatedId(selfUserId),
                clientid = clientId,
                readyh = null,
                sendh = null,
                sfth = null,
                incomingh = null,
                missedh = null,
                answerh = null,
                estabh = null,
                closeh = null,
                metricsh = null,
                cfg_reqh = null,
                acbrh = null,
                vstateh = null,
                arg = null
            )
            deferred.complete(handle)
            callingLogger.i("iOS AVS experimental bridge created call handle")
        }
        return deferred.await()
    }

    override suspend fun onCallingMessageReceived(message: Message.Signaling, content: MessageContent.Calling) {
        callingLogger.w("iOS AVS experimental bridge has no signaling callback wiring yet; ignoring incoming calling message")
    }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationTypeCalling: com.wire.kalium.calling.ConversationTypeCalling,
        isAudioCbr: Boolean
    ) {
        val handle = ensureHandle()
        val result = avs.wcall_start(
            handle,
            federatedIdMapper.parseToFederatedId(conversationId),
            if (callType == CallType.VIDEO) 1 else 0,
            conversationTypeCalling.avsValue,
            if (isAudioCbr) 1 else 0,
            0
        )
        callingLogger.i("iOS AVS experimental bridge startCall result=$result")
    }

    override suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean, isVideoCall: Boolean) {
        avs.wcall_answer(
            ensureHandle(),
            federatedIdMapper.parseToFederatedId(conversationId),
            if (isVideoCall) 1 else 0,
            if (isAudioCbr) 1 else 0
        )
        callingLogger.i("iOS AVS experimental bridge answerCall forwarded")
    }

    override suspend fun endCall(conversationId: ConversationId) {
        avs.wcall_end(ensureHandle(), federatedIdMapper.parseToFederatedId(conversationId))
        callingLogger.i("iOS AVS experimental bridge endCall forwarded")
    }

    override suspend fun rejectCall(conversationId: ConversationId) {
        avs.wcall_reject(ensureHandle(), federatedIdMapper.parseToFederatedId(conversationId))
        callingLogger.i("iOS AVS experimental bridge rejectCall forwarded")
    }

    override suspend fun muteCall(shouldMute: Boolean) {
        avs.wcall_set_mute(ensureHandle(), if (shouldMute) 1 else 0)
        callingLogger.i("iOS AVS experimental bridge muteCall forwarded")
    }

    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) {
        avs.wcall_set_video_send_state(
            ensureHandle(),
            federatedIdMapper.parseToFederatedId(conversationId),
            when (videoState) {
                VideoState.STOPPED -> 0
                VideoState.STARTED -> 1
                VideoState.BAD_CONNECTION -> 2
                VideoState.PAUSED -> 3
                VideoState.SCREENSHARE -> 4
                VideoState.UNKNOWN -> 5
            }
        )
        callingLogger.i("iOS AVS experimental bridge setVideoSendState forwarded")
    }

    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) {
        callingLogger.w("iOS AVS experimental bridge requestVideoStreams not wired yet")
    }

    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) {
        callingLogger.w("iOS AVS experimental bridge updateEpochInfo not wired yet")
    }

    override suspend fun updateConversationClients(conversationId: ConversationId, clients: String) {
        callingLogger.w("iOS AVS experimental bridge updateConversationClients not wired yet")
    }

    override suspend fun reportProcessNotifications(isStarted: Boolean) {
        avs.wcall_process_notifications(ensureHandle(), if (isStarted) 1 else 0)
        callingLogger.i("iOS AVS experimental bridge reportProcessNotifications forwarded")
    }

    override suspend fun setTestVideoType(testVideoType: TestVideoType) {
        callingLogger.w("iOS AVS experimental bridge test video hooks not wired yet")
    }

    override suspend fun setTestPreviewActive(shouldEnable: Boolean) {
        callingLogger.w("iOS AVS experimental bridge preview hooks not wired yet")
    }

    override suspend fun setTestRemoteVideoStates(conversationId: ConversationId, participants: List<Participant>) {
        callingLogger.w("iOS AVS experimental bridge remote video test hooks not wired yet")
    }

    override suspend fun setBackground(background: Boolean) {
        avs.wcall_set_background(ensureHandle(), if (background) 1 else 0)
        callingLogger.i("iOS AVS experimental bridge setBackground forwarded")
    }

    override suspend fun setNetworkQualityInterval(intervalInSeconds: Int) {
        callingLogger.w("iOS AVS experimental bridge network quality callback wiring not implemented yet")
    }

    override suspend fun cancelJobs() {
        if (!cancelled.isCompleted) {
            cancelled.complete(Unit)
        }
    }
}
