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

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.calling.callbacks.ConstantBitRateStateChangeHandler
import com.wire.kalium.calling.callbacks.MetricsHandler
import com.wire.kalium.calling.callbacks.ReadyHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallHelperImpl
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.TestVideoType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.mapper.ParticipantMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.scenario.CallingMessageSender
import com.wire.kalium.logic.feature.call.scenario.OnActiveSpeakers
import com.wire.kalium.logic.feature.call.scenario.OnAnsweredCall
import com.wire.kalium.logic.feature.call.scenario.OnClientsRequest
import com.wire.kalium.logic.feature.call.scenario.OnCloseCall
import com.wire.kalium.logic.feature.call.scenario.OnConfigRequest
import com.wire.kalium.logic.feature.call.scenario.OnEstablishedCall
import com.wire.kalium.logic.feature.call.scenario.OnIncomingCall
import com.wire.kalium.logic.feature.call.scenario.OnMissedCall
import com.wire.kalium.logic.feature.call.scenario.OnMuteStateForSelfUserChanged
import com.wire.kalium.logic.feature.call.scenario.OnNetworkQualityChanged
import com.wire.kalium.logic.feature.call.scenario.OnParticipantListChanged
import com.wire.kalium.logic.feature.call.scenario.OnParticipantsVideoStateChanged
import com.wire.kalium.logic.feature.call.scenario.OnRequestNewEpoch
import com.wire.kalium.logic.feature.call.scenario.OnSFTRequest
import com.wire.kalium.logic.feature.call.scenario.OnSendOTR
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.logic.util.ServerTimeHandlerImpl
import com.wire.kalium.logic.util.toInt
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.toInstant
import kotlinx.serialization.json.Json
import java.util.Collections

@Suppress("LongParameterList", "TooManyFunctions")
class CallManagerImpl internal constructor(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    selfConversationIdProvider: SelfConversationIdProvider,
    private val conversationRepository: ConversationRepository,
    messageSender: MessageSender,
    private val callMapper: CallMapper,
    private val federatedIdMapper: FederatedIdMapper,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val videoStateChecker: VideoStateChecker,
    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
    private val networkStateObserver: NetworkStateObserver,
    private val getCallConversationType: GetCallConversationTypeProvider,
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val mediaManagerService: MediaManagerService,
    private val flowManagerService: FlowManagerService,
    private val createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase,
    private val selfUserId: UserId,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val shouldRemoteMuteChecker: ShouldRemoteMuteChecker = ShouldRemoteMuteCheckerImpl(),
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    kaliumDispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : CallManager {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + kaliumDispatchers.io)
    private val deferredHandle: Deferred<Handle> = startHandleAsync()

    private val callingMessageSender = CallingMessageSender(
        deferredHandle,
        calling,
        messageSender,
        scope,
        selfConversationIdProvider
    )

    private val strongReferences = Collections.synchronizedList(mutableListOf<Any>())
    private fun <T : Any> T.keepingStrongReference(): T {
        strongReferences.add(this)
        return this
    }

    private val clientId: Deferred<ClientId> = scope.async(start = CoroutineStart.LAZY) {
        currentClientIdProvider().fold({
            TODO("adjust correct variable calling")
        }, {
            callingLogger.d("$TAG - clientId $it")
            it
        })
    }

    private val initializeServerTimeOffsetJob: Deferred<Unit> = scope.async(start = CoroutineStart.LAZY) {
        callRepository.fetchServerTime()?.let { serverTime ->
            callingLogger.d("$TAG - Computing server time offset: $serverTime")
            serverTimeHandler.computeTimeOffset(serverTime.toInstant().epochSeconds)
        } ?: run {
            callingLogger.w("$TAG: Failed to fetch server time for offset computation.")
        }
    }

    private suspend fun ensureServerTimeOffsetComputed() {
        if (!initializeServerTimeOffsetJob.isCompleted && !initializeServerTimeOffsetJob.isCancelled) {
            initializeServerTimeOffsetJob.await()
        }
    }

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    private val metricsHandler = MetricsHandler { conversationId: String, metricsJson: String, arg: Pointer? ->
        callingLogger.i("Calling metrics: $metricsJson")
    }.keepingStrongReference()

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    private val constantBitRateStateChangeHandler =
        ConstantBitRateStateChangeHandler { userId: String, clientId: String, isEnabled: Boolean, arg: Pointer? ->
            callingLogger.i(
                "$TAG -> constantBitRateStateChangeHandler for userId: ${userId.obfuscateId()} " +
                        "clientId: ${clientId.obfuscateId()}  isCbrEnabled: $isEnabled"
            )
            callRepository.updateIsCbrEnabled(isEnabled)
        }.keepingStrongReference()

    private fun startHandleAsync(): Deferred<Handle> {
        return scope.async(start = CoroutineStart.LAZY) {
            val mediaManagerStartJob = launch {
                callingLogger.i("$TAG: Starting MediaManager")
                mediaManagerService.startMediaManager()
            }
            val flowManagerStartJob = launch {
                callingLogger.i("$TAG: Starting FlowManager")
                flowManagerService.startFlowManager()
            }
            joinAll(flowManagerStartJob, mediaManagerStartJob)
            callingLogger.i("$TAG: Creating Handle")
            val selfUserId = federatedIdMapper.parseToFederatedId(selfUserId)
            val selfClientId = clientId.await().value

            val waitInitializationJob = Job()

            val handle = calling.wcall_create(
                userId = selfUserId,
                clientId = selfClientId,
                readyHandler = ReadyHandler { version: Int, arg: Pointer? ->
                    callingLogger.i("$TAG -> readyHandler; version=$version; arg=$arg")
                    onCallingReady()
                    waitInitializationJob.complete()
                    Unit
                }.keepingStrongReference(),
                // TODO(refactor): inject all of these CallbackHandlers in class constructor
                sendHandler = OnSendOTR(
                    qualifiedIdMapper = qualifiedIdMapper,
                    selfUserId = selfUserId,
                    selfClientId = selfClientId,
                    callMapper = callMapper,
                    callingMessageSender = callingMessageSender,
                ).keepingStrongReference(),
                sftRequestHandler = OnSFTRequest(deferredHandle, calling, callRepository, scope)
                    .keepingStrongReference(),
                incomingCallHandler = OnIncomingCall(callRepository, callMapper, qualifiedIdMapper, scope, kaliumConfigs)
                    .keepingStrongReference(),
                missedCallHandler = OnMissedCall,
                answeredCallHandler = OnAnsweredCall(callRepository, scope, qualifiedIdMapper)
                    .keepingStrongReference(),
                establishedCallHandler = OnEstablishedCall(callRepository, scope, qualifiedIdMapper)
                    .keepingStrongReference(),
                closeCallHandler = OnCloseCall(
                    callRepository = callRepository,
                    networkStateObserver = networkStateObserver,
                    scope = scope,
                    qualifiedIdMapper = qualifiedIdMapper,
                    createAndPersistRecentlyEndedCallMetadata = createAndPersistRecentlyEndedCallMetadata
                ).keepingStrongReference(),
                metricsHandler = metricsHandler,
                callConfigRequestHandler = OnConfigRequest(calling, callRepository, scope)
                    .keepingStrongReference(),
                constantBitRateStateChangeHandler = constantBitRateStateChangeHandler,
                videoReceiveStateHandler = OnParticipantsVideoStateChanged().keepingStrongReference(),
                arg = null
            )
            callingLogger.d("$TAG - wcall_create() called")
            // TODO(edge-case): Add a timeout. Perhaps make some functions (like onCallingMessageReceived) return Eithers.
            waitInitializationJob.join()
            handle
        }
    }

    private suspend fun <T> withCalling(action: suspend Calling.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return calling.action(handle)
    }

    override suspend fun onCallingMessageReceived(
        message: Message.Signaling,
        content: MessageContent.Calling,
    ) = withCalling {
        ensureServerTimeOffsetComputed() // make sure the offset is now computed and only process if was not computed before.
        callingLogger.i("$TAG - onCallingMessageReceived called: { \"message\" : ${message.toLogString()}}")
        val msg = content.value.toByteArray()

        val callingValue = json.decodeFromString<MessageContent.Calling.CallingValue>(content.value)
        val conversationMembers = conversationRepository.observeConversationMembers(message.conversationId).first()
        val shouldRemoteMute = shouldRemoteMuteChecker.check(
            senderUserId = message.senderUserId,
            selfUserId = selfUserId,
            selfClientId = clientId.await().value,
            targets = callingValue.targets,
            conversationMembers = conversationMembers
        )

        if (callingValue.type != REMOTE_MUTE_TYPE || shouldRemoteMute) {
            val targetConversationId = if (message.isSelfMessage) {
                content.conversationId ?: message.conversationId
            } else {
                message.conversationId
            }

            val callConversationType = getCallConversationType(targetConversationId)
            val type = callMapper.toConversationType(callConversationType)

            wcall_recv_msg(
                inst = deferredHandle.await(),
                msg = msg,
                len = msg.size,
                curr_time = Uint32_t(value = serverTimeHandler.toServerTimestamp()),
                msg_time = Uint32_t(value = message.date.epochSeconds),
                convId = federatedIdMapper.parseToFederatedId(targetConversationId),
                userId = federatedIdMapper.parseToFederatedId(message.senderUserId),
                clientId = message.senderClientId.value,
                convType = callMapper.toConversationTypeCalling(type).avsValue
            )
            callingLogger.i("$TAG - wcall_recv_msg() called")
        }
    }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationTypeCalling: ConversationTypeCalling,
        isAudioCbr: Boolean
    ) {
        callingLogger.d(
            "$TAG -> starting call for conversation = " +
                    "${conversationId.toLogString()}.."
        )
        val isCameraOn = callType == CallType.VIDEO

        val type = callMapper.toConversationType(conversationTypeCalling)

        callRepository.createCall(
            conversationId = conversationId,
            type = type,
            status = CallStatus.STARTED,
            isMuted = false,
            isCameraOn = isCameraOn,
            isCbrEnabled = isAudioCbr,
            callerId = selfUserId
        )

        withCalling {
            val avsCallType = callMapper.toCallTypeCalling(callType)

            if (callRepository.getCallMetadata(conversationId)?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.joinMlsConference(
                    conversationId = conversationId,
                    onJoined = {
                        wcall_start(
                            deferredHandle.await(),
                            federatedIdMapper.parseToFederatedId(conversationId),
                            avsCallType.avsValue,
                            conversationTypeCalling.avsValue,
                            isAudioCbr.toInt()
                        )
                    },
                    onEpochChange = { conversationId, epochInfo ->
                        updateEpochInfo(conversationId, epochInfo)
                    }
                )
            } else {
                // TODO: Handle response. Possible failure?
                wcall_start(
                    deferredHandle.await(),
                    federatedIdMapper.parseToFederatedId(conversationId),
                    avsCallType.avsValue,
                    conversationTypeCalling.avsValue,
                    isAudioCbr.toInt()
                )
                callingLogger.d(
                    "$TAG - wcall_start() called -> Call for conversation = " +
                            "${conversationId.toLogString()} started"
                )
            }
        }
    }

    override suspend fun answerCall(
        conversationId: ConversationId,
        isAudioCbr: Boolean,
        isVideoCall: Boolean
    ) {
        withCalling {
            callingLogger.d(
                "$TAG -> answering call for conversation = " +
                        "${conversationId.toLogString()}.."
            )
            val callType = if (isVideoCall) CallTypeCalling.VIDEO else CallTypeCalling.AUDIO

            if (callRepository.getCallMetadata(conversationId)?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.joinMlsConference(
                    conversationId = conversationId,
                    onJoined = {
                        wcall_answer(
                            inst = deferredHandle.await(),
                            conversationId = federatedIdMapper.parseToFederatedId(conversationId),
                            callType = callType.avsValue,
                            cbrEnabled = isAudioCbr
                        )
                        callingLogger.i(
                            "$TAG - wcall_answer() called -> Incoming call for conversation = " +
                                    "${conversationId.toLogString()} answered"
                        )
                    },
                    onEpochChange = { conversationId, epochInfo ->
                        updateEpochInfo(conversationId, epochInfo)
                    }
                )
            } else {
                wcall_answer(
                    inst = deferredHandle.await(),
                    conversationId = federatedIdMapper.parseToFederatedId(conversationId),
                    callType = callType.avsValue,
                    cbrEnabled = isAudioCbr
                )
                callingLogger.i(
                    "$TAG - wcall_answer() called -> Incoming call for conversation = " +
                            "${conversationId.toLogString()} answered"
                )
            }
        }
    }

    override suspend fun endCall(conversationId: ConversationId) = withCalling {
        callingLogger.d(
            "[$TAG][endCall] -> ConversationId: " +
                    "[${conversationId.toLogString()}]"
        )
        callingLogger.d("[$TAG][endCall] -> Calling wcall_end()")
        wcall_end(
            inst = deferredHandle.await(),
            conversationId = federatedIdMapper.parseToFederatedId(conversationId)
        )
    }

    override suspend fun rejectCall(conversationId: ConversationId) = withCalling {
        callingLogger.d(
            "[$TAG][rejectCall] -> ConversationId: " +
                    "[${conversationId.toLogString()}]"
        )
        callingLogger.d("[$TAG][rejectCall] -> Calling wcall_reject()")
        wcall_reject(
            inst = deferredHandle.await(),
            conversationId = federatedIdMapper.parseToFederatedId(conversationId)
        )
    }

    override suspend fun muteCall(shouldMute: Boolean) = withCalling {
        val logString = if (shouldMute) "muting" else "un-muting"
        callingLogger.d("$TAG -> $logString call..")
        wcall_set_mute(deferredHandle.await(), muted = shouldMute.toInt())
        callingLogger.d("$TAG - wcall_set_mute() called")
    }

    override suspend fun setTestVideoType(testVideoType: TestVideoType) = withCalling {
        val logString = when (testVideoType) {
            TestVideoType.NONE -> "NONE"
            TestVideoType.PLATFORM -> "PLATFORM"
            TestVideoType.FAKE -> "FAKE"
            else -> "????"
        }
        callingLogger.d("$TAG -> set test video to $logString")

        val selfUserId = federatedIdMapper.parseToFederatedId(selfUserId)
        val selfClientId = clientId.await().value
        val handle = deferredHandle.await()

        when (testVideoType) {
            TestVideoType.NONE -> kcall_close()
            TestVideoType.PLATFORM -> {
                kcall_init(0)
                kcall_set_local_user(selfUserId, selfClientId)
                kcall_set_wuser(handle)

            }

            TestVideoType.FAKE -> {
                kcall_init(1)
                kcall_set_local_user(selfUserId, selfClientId)
                kcall_set_wuser(handle)
            }
        }
    }

    override suspend fun setTestPreviewActive(shouldEnable: Boolean) = withCalling {
        val logString = if (shouldEnable) "enabling" else "disabling"
        callingLogger.d("$TAG -> $logString preview..")
        if (shouldEnable)
            kcall_preview_start()
        else
            kcall_preview_stop()
    }

    override suspend fun setTestRemoteVideoStates(conversationId: ConversationId, participants: List<Participant>) = withCalling {
        for (participant in participants) {
            val videoState = if (participant.isCameraOn) 1 else 0
            kcall_set_user_vidstate(
                federatedIdMapper.parseToFederatedId(conversationId),
                federatedIdMapper.parseToFederatedId(participant.id),
                clientid = participant.clientId,
                videoState
            )
        }
    }

    /**
     * This method should NOT be called while the call is still incoming or outgoing and not established yet.
     */
    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) {
        withCalling {
            callingLogger.d("$TAG -> changing video state to ${videoState.name}..")
            scope.launch {
                val videoStateCalling = callMapper.toVideoStateCalling(videoState)
                wcall_set_video_send_state(
                    deferredHandle.await(),
                    federatedIdMapper.parseToFederatedId(conversationId),
                    videoStateCalling.avsValue
                )
                callingLogger.d("$TAG -> wcall_set_video_send_state called..")
            }
        }
    }

    override suspend fun requestVideoStreams(
        conversationId: ConversationId,
        callClients: CallClientList
    ) {
        withCalling {
            // Mapping Needed to support calls between federated and non federated environments (domain separation)
            val clients = callClients.clients.map { callClient ->
                CallClient(
                    userId = federatedIdMapper.parseToFederatedId(callClient.userId),
                    clientId = callClient.clientId,
                    isMemberOfSubconversation = callClient.isMemberOfSubconversation,
                    quality = callClient.quality
                )
            }
            val clientsJson = CallClientList(clients).toJsonString()
            callingLogger.d(
                "$TAG - wcall_request_video_streams() called -> Requesting video streams for conversation = ${conversationId.toLogString()}"
            )
            val conversationIdString = federatedIdMapper.parseToFederatedId(conversationId)
            calling.wcall_request_video_streams(
                inst = it,
                conversationId = conversationIdString,
                mode = DEFAULT_REQUEST_VIDEO_STREAMS_MODE,
                json = clientsJson
            )
        }
    }

    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) {
        withCalling {
            callingLogger.d(
                "$TAG - wcall_set_epoch_info() called -> Updating epoch info call for conversation = " +
                        "${conversationId.toLogString()} for epoch = ${epochInfo.epoch}"
            )

            wcall_set_epoch_info(
                it,
                federatedIdMapper.parseToFederatedId(conversationId),
                Uint32_t(epochInfo.epoch.toLong()),
                epochInfo.members.toJsonString(),
                epochInfo.sharedSecret.encodeBase64()
            )
        }
    }

    override suspend fun updateConversationClients(
        conversationId: ConversationId,
        clients: String
    ) {
        if (callRepository.getCallMetadata(conversationId)?.protocol is Conversation.ProtocolInfo.Proteus) {
            withCalling {
                wcall_set_clients_for_conv(
                    it,
                    federatedIdMapper.parseToFederatedId(conversationId),
                    clients
                )
            }
        }
    }

    /**
     * onCallingReady
     * Will start the handlers for: ParticipantsChanged, NetworkQuality, ClientsRequest and ActiveSpeaker
     */
    private fun onCallingReady() {
        initParticipantsHandler()
        initNetworkHandler()
        initClientsHandler()
        initActiveSpeakersHandler()
        initRequestNewEpochHandler()
        initSelfUserMuteHandler()
        initCallingMessageSender()
    }

    private fun initCallingMessageSender() {
        scope.launch {
            callingMessageSender.processQueue()
        }
    }

    private fun initParticipantsHandler() {
        scope.launch {
            withCalling {
                val onParticipantListChanged = OnParticipantListChanged(
                    callRepository = callRepository,
                    qualifiedIdMapper = qualifiedIdMapper,
                    participantMapper = ParticipantMapperImpl(videoStateChecker, callMapper, qualifiedIdMapper),
                    userConfigRepository = userConfigRepository,
                    callHelper = CallHelperImpl(),
                    endCall = { endCall(it) },
                    callingScope = scope
                ).keepingStrongReference()

                wcall_set_participant_changed_handler(
                    inst = deferredHandle.await(),
                    wcall_participant_changed_h = onParticipantListChanged,
                    arg = null
                )
                callingLogger.d("$TAG - wcall_set_participant_changed_handler() called")
            }
        }
    }

    private fun initNetworkHandler() {
        scope.launch {
            withCalling {
                val onNetworkQualityChanged = OnNetworkQualityChanged()
                    .keepingStrongReference()

                wcall_set_network_quality_handler(
                    inst = deferredHandle.await(),
                    wcall_network_quality_h = onNetworkQualityChanged,
                    intervalInSeconds = NETWORK_QUALITY_INTERVAL_SECONDS,
                    arg = null
                )
                callingLogger.d("$TAG - wcall_set_network_quality_handler() called")
            }
        }
    }

    private fun initClientsHandler() {
        scope.launch {
            withCalling {
                val onClientsRequest = OnClientsRequest(
                    conversationClientsInCallUpdater = conversationClientsInCallUpdater,
                    qualifiedIdMapper = qualifiedIdMapper,
                    callingScope = scope
                ).keepingStrongReference()

                wcall_set_req_clients_handler(
                    inst = deferredHandle.await(),
                    wcall_req_clients_h = onClientsRequest
                )

                callingLogger.d("$TAG - wcall_set_req_clients_handler() called")
            }
        }
    }

    private fun initActiveSpeakersHandler() {
        scope.launch {
            withCalling {
                val activeSpeakersHandler = OnActiveSpeakers(
                    callRepository = callRepository,
                    qualifiedIdMapper = qualifiedIdMapper
                ).keepingStrongReference()

                wcall_set_active_speaker_handler(
                    inst = deferredHandle.await(),
                    activeSpeakersHandler = activeSpeakersHandler
                )

                callingLogger.d("$TAG - wcall_set_req_clients_handler() called")
            }
        }
    }

    private fun initRequestNewEpochHandler() {
        scope.launch {
            withCalling {
                val requestNewEpochHandler = OnRequestNewEpoch(
                    scope = scope,
                    callRepository = callRepository,
                    qualifiedIdMapper = qualifiedIdMapper
                ).keepingStrongReference()

                wcall_set_req_new_epoch_handler(
                    inst = deferredHandle.await(),
                    requestNewEpochHandler = requestNewEpochHandler
                )

                callingLogger.d("$TAG - wcall_set_req_new_epoch_handler() called")
            }
        }
    }

    private fun initSelfUserMuteHandler() {
        scope.launch {
            withCalling {
                val selfUserMuteHandler = OnMuteStateForSelfUserChanged(
                    scope = scope,
                    callRepository = callRepository
                ).keepingStrongReference()

                wcall_set_mute_handler(
                    inst = deferredHandle.await(),
                    selfUserMuteHandler = selfUserMuteHandler,
                    arg = null
                )

                callingLogger.d("$TAG - wcall_set_mute_handler() called")
            }
        }
    }

    override suspend fun reportProcessNotifications(isStarted: Boolean) {
        withCalling {
            wcall_process_notifications(it, isStarted)
        }
    }

    override suspend fun cancelJobs() {
        deferredHandle.cancel()
        scope.cancel()
        job.cancel()
    }

    companion object {
        private const val DEFAULT_REQUEST_VIDEO_STREAMS_MODE = 0
        const val TAG = "CallManager"
        const val NETWORK_QUALITY_INTERVAL_SECONDS = 5
        const val UTF8_ENCODING = "UTF-8"
        const val REMOTE_MUTE_TYPE = "REMOTEMUTE"
    }
}
