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

import com.wire.kalium.calling.CallClosedReason
import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.ConversationTypeCalling
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.foldToEitherWhileRight
import com.wire.kalium.common.functional.nullableFold
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallActiveSpeakers
import com.wire.kalium.logic.data.call.CallClient
import com.wire.kalium.logic.data.call.CallClientList
import com.wire.kalium.logic.data.call.CallHelperImpl
import com.wire.kalium.logic.data.call.CallMetadata
import com.wire.kalium.logic.data.call.CallParticipants
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallStatus
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationTypeForCall
import com.wire.kalium.logic.data.call.EpochInfo
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.call.TestVideoType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.call.VideoStateChecker
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.call.mapper.ParticipantMapperImpl
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.usecase.ConversationClientsInCallUpdater
import com.wire.kalium.logic.feature.call.usecase.CreateAndPersistRecentlyEndedCallMetadataUseCase
import com.wire.kalium.logic.feature.call.usecase.EpochInfoUpdater
import com.wire.kalium.logic.feature.call.usecase.GetCallConversationTypeProvider
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.logic.util.ServerTimeHandlerImpl
import com.wire.kalium.messaging.sending.MessageSender
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.network.NetworkState
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.util.encodeBase64
import kotlinx.cinterop.COpaquePointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

@Suppress("LongParameterList", "TooManyFunctions")
internal class CallManagerImpl internal constructor(
    private val callRepository: CallRepository,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val messageSender: MessageSender,
    private val callMapper: CallMapper,
    private val federatedIdMapper: FederatedIdMapper,
    private val qualifiedIdMapper: QualifiedIdMapper,
    videoStateChecker: VideoStateChecker,
    private val conversationClientsInCallUpdater: ConversationClientsInCallUpdater,
    private val epochInfoUpdater: EpochInfoUpdater,
    private val networkStateObserver: NetworkStateObserver,
    private val getCallConversationType: GetCallConversationTypeProvider,
    private val userConfigRepository: UserConfigRepository,
    private val kaliumConfigs: KaliumConfigs,
    private val mediaManagerService: MediaManagerService,
    private val flowManagerService: FlowManagerService,
    private val createAndPersistRecentlyEndedCallMetadata: CreateAndPersistRecentlyEndedCallMetadataUseCase,
    private val selfUserId: UserId,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    kaliumDispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : CallManager {
    private val tagWithUserId = "$TAG(${selfUserId.toLogString()})"
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + kaliumDispatchers.io)
    private val participantMapper = ParticipantMapperImpl(videoStateChecker, callMapper, qualifiedIdMapper)
    private val callHelper = CallHelperImpl(userConfigRepository, callRepository)
    private val callbacks = AppleCallbacks()
    private val deferredHandle: Deferred<UInt> = startHandleAsync()

    private val clientId: Deferred<ClientId> = scope.async(start = CoroutineStart.LAZY) {
        currentClientIdProvider().fold({ failure ->
            error("Cannot initialize iOS calling without self client id: $failure")
        }, {
            callingLogger.d("$tagWithUserId: clientId: $it")
            it
        })
    }

    private val initializeServerTimeOffsetJob: Deferred<Unit> = scope.async(start = CoroutineStart.LAZY) {
        callRepository.fetchServerTime()?.let { serverTime ->
            callingLogger.d("$tagWithUserId: Computing server time offset: $serverTime")
            serverTimeHandler.computeTimeOffset(Instant.parse(serverTime).epochSeconds)
        } ?: callingLogger.w("$tagWithUserId: Failed to fetch server time for offset computation.")
    }

    private suspend fun ensureServerTimeOffsetComputed() {
        if (!initializeServerTimeOffsetJob.isCompleted && !initializeServerTimeOffsetJob.isCancelled) {
            initializeServerTimeOffsetJob.await()
        }
    }

    private fun startHandleAsync(): Deferred<UInt> = scope.async(start = CoroutineStart.LAZY) {
        launch {
            callingLogger.i("$tagWithUserId: Starting MediaManager")
            mediaManagerService.startMediaManager()
        }.join()
        launch {
            callingLogger.i("$tagWithUserId: Starting FlowManager")
            flowManagerService.startFlowManager()
        }.join()
        callingLogger.i("$tagWithUserId: Creating iOS AVS Handle")
        AppleAvsInterop.userHandle(
            selfUserId = federatedIdMapper.parseToFederatedId(selfUserId),
            selfClientId = clientId.await().value,
            callbacks = callbacks
        ) ?: error("Failed to create iOS AVS wcall user")
    }

    private suspend fun <T> withCalling(action: suspend (handle: UInt) -> T): T = action(deferredHandle.await())

    override suspend fun onCallingMessageReceived(message: Message.Signaling, content: MessageContent.Calling) = withCalling { handle ->
        ensureServerTimeOffsetComputed()
        callingLogger.i("$tagWithUserId: onCallingMessageReceived called: { \"message\" : ${message.toLogString()}}")
        val targetConversationId = if (message.isSelfMessage) {
            content.conversationId ?: message.conversationId
        } else {
            message.conversationId
        }
        val callConversationType = getCallConversationType(targetConversationId)
        val type = callMapper.toConversationType(callConversationType)
        val received = AppleAvsInterop.receiveCallingMessage(
            handle = handle,
            payload = content.value.encodeToByteArray(),
            currentTimeSeconds = serverTimeHandler.toServerTimestamp().toUInt(),
            messageTimeSeconds = message.date.epochSeconds.toUInt(),
            conversationId = federatedIdMapper.parseToFederatedId(targetConversationId),
            senderUserId = federatedIdMapper.parseToFederatedId(message.senderUserId),
            senderClientId = message.senderClientId.value,
            conversationType = callMapper.toConversationTypeCalling(type).avsValue
        )
        callingLogger.i("$tagWithUserId: wcall_recv_msg() forwarded=$received")
    }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationTypeCalling: ConversationTypeCalling,
        isAudioCbr: Boolean
    ) {
        callingLogger.d("$tagWithUserId: starting call for conversationId: ${conversationId.toLogString()}..")
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

        withCalling { handle ->
            val avsCallType = callMapper.toCallTypeCalling(callType)
            val startAvs = suspend {
                AppleAvsInterop.startCall(
                    handle = handle,
                    conversationId = federatedIdMapper.parseToFederatedId(conversationId),
                    callType = avsCallType.avsValue,
                    conversationType = conversationTypeCalling.avsValue,
                    audioCbr = isAudioCbr
                )
                callingLogger.d("$tagWithUserId: wcall_start() called -> ${conversationId.toLogString()}")
            }

            if (callRepository.getCallMetadata(conversationId)?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.joinMlsConference(
                    conversationId = conversationId,
                    onJoined = startAvs,
                    onEpochChange = { conversation, epochInfo -> updateEpochInfo(conversation, epochInfo) }
                )
            } else {
                startAvs()
            }
        }
    }

    override suspend fun answerCall(conversationId: ConversationId, isAudioCbr: Boolean, isVideoCall: Boolean) = withCalling { handle ->
        callingLogger.d("$tagWithUserId: answering call for conversationId: ${conversationId.toLogString()}..")
        val callType = if (isVideoCall) CallTypeCalling.VIDEO else CallTypeCalling.AUDIO
        val answerAvs = suspend {
            AppleAvsInterop.answerCall(
                handle = handle,
                conversationId = federatedIdMapper.parseToFederatedId(conversationId),
                callType = callType.avsValue,
                audioCbr = isAudioCbr
            )
            callingLogger.i("$tagWithUserId: wcall_answer() called -> ${conversationId.toLogString()}")
        }

        if (callRepository.getCallMetadata(conversationId)?.protocol is Conversation.ProtocolInfo.MLS) {
            callRepository.joinMlsConference(
                conversationId = conversationId,
                onJoined = answerAvs,
                onEpochChange = { conversation, epochInfo -> updateEpochInfo(conversation, epochInfo) }
            )
        } else {
            answerAvs()
        }
        Unit
    }

    override suspend fun endCall(conversationId: ConversationId) = withCalling { handle ->
        callingLogger.d("$tagWithUserId: endCall -> ${conversationId.toLogString()}")
        AppleAvsInterop.endCall(handle, federatedIdMapper.parseToFederatedId(conversationId))
        Unit
    }

    override suspend fun rejectCall(conversationId: ConversationId) = withCalling { handle ->
        callingLogger.d("$tagWithUserId: rejectCall -> ${conversationId.toLogString()}")
        AppleAvsInterop.rejectCall(handle, federatedIdMapper.parseToFederatedId(conversationId))
        Unit
    }

    override suspend fun muteCall(shouldMute: Boolean) = withCalling { handle ->
        AppleAvsInterop.setMute(handle, shouldMute)
        callingLogger.d("$tagWithUserId: wcall_set_mute() called")
        Unit
    }

    override suspend fun setVideoSendState(conversationId: ConversationId, videoState: VideoState) = withCalling { handle ->
        val videoStateCalling = callMapper.toVideoStateCalling(videoState)
        AppleAvsInterop.setVideoSendState(handle, federatedIdMapper.parseToFederatedId(conversationId), videoStateCalling.avsValue)
        Unit
    }

    override suspend fun requestVideoStreams(conversationId: ConversationId, callClients: CallClientList) = withCalling { handle ->
        val clients = callClients.clients.map { callClient ->
            CallClient(
                userId = federatedIdMapper.parseToFederatedId(callClient.userId),
                clientId = callClient.clientId,
                isMemberOfSubconversation = callClient.isMemberOfSubconversation,
                quality = callClient.quality
            )
        }
        AppleAvsInterop.requestVideoStreams(
            handle = handle,
            conversationId = federatedIdMapper.parseToFederatedId(conversationId),
            mode = DEFAULT_REQUEST_VIDEO_STREAMS_MODE,
            json = CallClientList(clients).toJsonString()
        )
        Unit
    }

    override suspend fun updateEpochInfo(conversationId: ConversationId, epochInfo: EpochInfo) = withCalling { handle ->
        callingLogger.d("$tagWithUserId: wcall_set_epoch_info() called -> ${conversationId.toLogString()} epoch=${epochInfo.epoch}")
        AppleAvsInterop.setEpochInfo(
            handle = handle,
            conversationId = federatedIdMapper.parseToFederatedId(conversationId),
            epoch = epochInfo.epoch.toUInt(),
            clientsJson = epochInfo.members.toJsonString(),
            keyBase64 = epochInfo.sharedSecret.encodeBase64()
        )
        Unit
    }

    override suspend fun updateConversationClients(conversationId: ConversationId, clients: String) {
        if (callRepository.getCallMetadata(conversationId)?.protocol is Conversation.ProtocolInfo.Proteus) {
            withCalling { handle ->
                AppleAvsInterop.setClientsForConversation(handle, federatedIdMapper.parseToFederatedId(conversationId), clients)
            }
        }
    }

    override suspend fun reportProcessNotifications(isStarted: Boolean) = withCalling { handle ->
        AppleAvsInterop.processNotifications(handle, isStarted)
        Unit
    }

    override suspend fun setTestVideoType(testVideoType: TestVideoType) {
        callingLogger.w("Calls partially supported on iOS: setTestVideoType ignored")
    }

    override suspend fun setTestPreviewActive(shouldEnable: Boolean) {
        callingLogger.w("Calls partially supported on iOS: setTestPreviewActive ignored")
    }

    override suspend fun setTestRemoteVideoStates(conversationId: ConversationId, participants: List<Participant>) {
        callingLogger.w("Calls partially supported on iOS: setTestRemoteVideoStates ignored")
    }

    override suspend fun setBackground(background: Boolean) = withCalling { handle ->
        AppleAvsInterop.setBackground(handle, background)
        Unit
    }

    override suspend fun setNetworkQualityInterval(intervalInSeconds: Int) = withCalling { handle ->
        AppleAvsInterop.setNetworkQualityInterval(handle, callbacks, intervalInSeconds)
        Unit
    }

    override suspend fun cancelJobs() {
        deferredHandle.cancel()
        scope.cancel()
        job.cancel()
    }

    private inner class AppleCallbacks : AppleAvsInterop.Callbacks {
        override fun onReady(version: Int) {
            callingLogger.i("$tagWithUserId: readyHandler; version=$version")
            onCallingReady()
        }

        override fun onSend(
            context: COpaquePointer?,
            conversationId: String?,
            selfUserId: String?,
            selfClientId: String?,
            targetRecipientsJson: String?,
            clientIdDestination: String?,
            data: ByteArray,
            transient: Boolean,
            myClientsOnly: Boolean
        ): Int {
            callingLogger.i("[OnSendOTR/iOS] -> ConversationId: ${conversationId?.obfuscateId()}")
            if (conversationId == null || selfUserId == null || selfClientId == null) return AvsCallBackError.INVALID_ARGUMENT.value
            return try {
                val messageTarget = if (myClientsOnly) {
                    CallingMessageTarget.Self
                } else {
                    val specificTarget = targetRecipientsJson?.let { recipientsJson ->
                        callMapper.toClientMessageTarget(Json.decodeFromString<CallClientList>(recipientsJson))
                    } ?: MessageTarget.Conversation()
                    CallingMessageTarget.HostConversation(specificTarget)
                }
                enqueueCallingMessage(
                    context = context,
                    callHostConversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationId),
                    messageString = data.decodeToString(),
                    avsSelfUserId = qualifiedIdMapper.fromStringToQualifiedID(selfUserId),
                    avsSelfClientId = ClientId(selfClientId),
                    messageTarget = messageTarget
                )
                AvsCallBackError.NONE.value
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                callingLogger.e("[OnSendOTR/iOS] -> Error Exception: $e")
                AvsCallBackError.COULD_NOT_DECODE_ARGUMENT.value
            }
        }

        override fun onSftRequest(context: COpaquePointer?, url: String?, data: ByteArray): Int {
            if (url == null) return AvsCallBackError.INVALID_ARGUMENT.value
            scope.launch {
                val connected = withTimeoutOrNull(DEFAULT_WAIT_UNTIL_CONNECTED_TIMEOUT) {
                    networkStateObserver.observeNetworkState().firstOrNull { it is NetworkState.ConnectedWithInternet }
                } != null
                if (!connected) {
                    AppleAvsInterop.respondToSft(deferredHandle.await(), AvsSFTError.NO_RESPONSE_DATA.value, byteArrayOf(), context)
                    return@launch
                }
                val dataString = data.decodeToString()
                val responseData = callRepository.connectToSFT(url = url, data = dataString)
                    .nullableFold(
                        { null },
                        { it }
                    ) ?: byteArrayOf()
                val error = if (responseData.isEmpty()) AvsSFTError.NO_RESPONSE_DATA.value else AvsSFTError.NONE.value
                AppleAvsInterop.respondToSft(deferredHandle.await(), error, responseData, context)
            }
            return AvsCallBackError.NONE.value
        }

        override fun onIncomingCall(
            conversationId: String?,
            messageTime: UInt,
            userId: String?,
            clientId: String?,
            video: Boolean,
            shouldRing: Boolean,
            conversationType: Int
        ) {
            if (conversationId == null || userId == null) return
            callingLogger.i(
                "[OnIncomingCall/iOS] -> ConversationId: ${conversationId.obfuscateId()}" +
                        " | UserId: ${userId.obfuscateId()} | shouldRing: $shouldRing | type: $conversationType"
            )
            val mappedConversationType = callMapper.fromIntToConversationType(conversationType)
            val isMuted = setOf(ConversationTypeForCall.Conference, ConversationTypeForCall.ConferenceMls).contains(mappedConversationType)
            val status = if (shouldRing) CallStatus.INCOMING else CallStatus.STILL_ONGOING
            scope.launch {
                callRepository.createCall(
                    conversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationId),
                    status = status,
                    callerId = qualifiedIdMapper.fromStringToQualifiedID(userId),
                    isMuted = isMuted,
                    isCameraOn = video,
                    type = mappedConversationType,
                    isCbrEnabled = kaliumConfigs.forceConstantBitrateCalls
                )
            }
        }

        override fun onMissedCall(conversationId: String?, messageTime: UInt, userId: String?, video: Boolean) {
            callingLogger.i("[OnMissedCall/iOS] - conversationId: ${conversationId?.obfuscateId()} | userId: ${userId?.obfuscateId()}")
        }

        override fun onAnsweredCall(conversationId: String?) {
            if (conversationId == null) return
            scope.launch {
                callRepository.updateCallStatusById(qualifiedIdMapper.fromStringToQualifiedID(conversationId), CallStatus.ANSWERED)
            }
        }

        override fun onEstablishedCall(conversationId: String?, userId: String?, clientId: String?) {
            if (conversationId == null) return
            scope.launch {
                callRepository.updateCallStatusById(qualifiedIdMapper.fromStringToQualifiedID(conversationId), CallStatus.ESTABLISHED)
            }
        }

        override fun onClosedCall(reason: Int, conversationId: String?, messageTime: UInt, userId: String?, clientId: String?) {
            if (conversationId == null) return
            handleClosedCall(reason, conversationId)
        }

        override fun onMetrics(conversationId: String?, metricsJson: String?) {
            callingLogger.i("$tagWithUserId: Calling metrics: $metricsJson")
        }

        override fun onConfigRequest(handle: UInt, context: COpaquePointer?): Int {
            scope.launch {
                callRepository.getCallConfigResponse(limit = null).fold({
                    callingLogger.w("[OnConfigRequest/iOS] - Error: $it")
                    AppleAvsInterop.updateConfig(handle, 1, "")
                }, { config ->
                    AppleAvsInterop.updateConfig(handle, 0, kaliumConfigs.callConfigTransformer?.invoke(config) ?: config)
                })
            }
            return AvsCallBackError.NONE.value
        }

        override fun onAudioCbrChanged(userId: String?, clientId: String?, enabled: Boolean) {
            scope.launch { callRepository.updateIsCbrEnabled(enabled) }
        }

        override fun onVideoStateChanged(conversationId: String?, userId: String?, clientId: String?, state: Int) {
            callingLogger.i(
                "[onVideoReceiveStateChanged/iOS] - conversationId: ${conversationId?.obfuscateId()}" +
                        " | userId: ${userId?.obfuscateId()} clientId: ${clientId?.obfuscateId()} | state: $state"
            )
        }

        override fun onParticipantChanged(conversationId: String?, data: String?) {
            if (conversationId == null || data == null) return
            scope.launch { handleParticipantsChanged(conversationId, data) }
        }

        override fun onNetworkQualityChanged(conversationId: String?, userId: String?, clientId: String?, qualityInfoJson: String?) {
            if (conversationId == null || qualityInfoJson == null) return
            val callQualityData = Json.decodeFromString<com.wire.kalium.logic.data.call.CallQualityData>(qualityInfoJson)
            callRepository.updateCallQualityData(qualifiedIdMapper.fromStringToQualifiedID(conversationId), callQualityData)
        }

        override fun onRequestNewEpoch(handle: UInt, conversationId: String?) {
            if (conversationId == null) return
            scope.launch { epochInfoUpdater(qualifiedIdMapper.fromStringToQualifiedID(conversationId)) }
        }

        override fun onClientsRequest(handle: UInt, conversationId: String?) {
            if (conversationId == null) return
            scope.launch {
                val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
                conversationClientsInCallUpdater(conversationIdWithDomain)
                epochInfoUpdater(conversationIdWithDomain)
            }
        }

        override fun onActiveSpeakersChanged(handle: UInt, conversationId: String?, data: String?) {
            if (conversationId == null || data == null) return
            val callActiveSpeakers = Json.decodeFromString<CallActiveSpeakers>(data)
            val activeSpeakers = callActiveSpeakers.activeSpeakers.filter { activeSpeaker ->
                activeSpeaker.audioLevel > 0 || activeSpeaker.audioLevelNow > 0
            }.groupBy({ qualifiedIdMapper.fromStringToQualifiedID(it.userId) }) { it.clientId }
            callRepository.updateParticipantsActiveSpeaker(qualifiedIdMapper.fromStringToQualifiedID(conversationId), activeSpeakers)
        }

        override fun onMuteStateChanged(isMuted: Boolean) {
            scope.launch {
                callRepository.establishedCallsFlow().first().firstOrNull()?.conversationId?.let {
                    callRepository.updateIsMutedById(it, isMuted)
                }
            }
        }
    }

    private fun enqueueCallingMessage(
        context: COpaquePointer?,
        callHostConversationId: ConversationId,
        messageString: String,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId,
        messageTarget: CallingMessageTarget
    ) {
        scope.launch {
            val transportConversationIds = when (messageTarget) {
                is CallingMessageTarget.Self -> selfConversationIdProvider()
                is CallingMessageTarget.HostConversation -> Either.Right(listOf(callHostConversationId))
            }
            val result = transportConversationIds.flatMap { conversations ->
                conversations.foldToEitherWhileRight(Unit) { transportConversationId, _ ->
                    sendCallingMessage(
                        callHostConversationId = callHostConversationId,
                        userId = avsSelfUserId,
                        clientId = avsSelfClientId,
                        data = messageString,
                        messageTarget = messageTarget.specificTarget,
                        transportConversationId = transportConversationId
                    )
                }
            }
            val (code, message) = when (result) {
                is Either.Right -> AVS_SEND_SUCCESS_STATUS_CODE to ""
                is Either.Left -> AVS_SEND_FAILURE_STATUS_CODE to "Couldn't send Calling Message"
            }
            AppleAvsInterop.respondToSend(deferredHandle.await(), code, message, context)
        }
    }

    private suspend fun sendCallingMessage(
        callHostConversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String,
        messageTarget: MessageTarget,
        transportConversationId: ConversationId
    ) = messageSender.sendMessage(
        Message.Signaling(
            id = Uuid.random().toString(),
            content = MessageContent.Calling(data, callHostConversationId),
            conversationId = transportConversationId,
            date = kotlinx.datetime.Clock.System.now(),
            senderUserId = userId,
            senderClientId = clientId,
            status = Message.Status.Sent,
            isSelfMessage = true,
            expirationData = null
        ),
        messageTarget
    )

    private suspend fun handleParticipantsChanged(conversationId: String, data: String) {
        val participantsChange = Json.decodeFromString<CallParticipants>(data)
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
        val participants = participantsChange.members.map { member ->
            participantMapper.fromCallMemberToParticipantMinimized(member)
        }
        if (callHelper.shouldEndSFTOneOnOneCall(conversationIdWithDomain, participants)) {
            endCall(conversationIdWithDomain)
        }
        callRepository.updateCallParticipants(conversationIdWithDomain, participants)
    }

    private fun handleClosedCall(reason: Int, conversationId: String) {
        val avsReason = CallClosedReason.fromInt(reason)
        val callStatus = getCallStatusFromCloseReason(avsReason)
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        scope.launch {
            val callMetadata = callRepository.getCallMetadata(conversationIdWithDomain)
            val isConnectedToInternet = networkStateObserver.observeNetworkState().value == NetworkState.ConnectedWithInternet
            if (isConnectedToInternet && shouldPersistMissedCall(callMetadata, callStatus)) {
                callRepository.persistMissedCall(conversationIdWithDomain)
            }

            val shouldUpdateCallStatus = if (callMetadata?.conversationType == Conversation.Type.OneOnOne) {
                when (callMetadata.callStatus) {
                    CallStatus.MISSED,
                    CallStatus.REJECTED,
                    CallStatus.CLOSED -> false
                    else -> true
                }
            } else {
                true
            }

            if (shouldUpdateCallStatus) {
                callRepository.updateCallStatusById(conversationIdWithDomain, callStatus)
            }
            if (callMetadata?.protocol is Conversation.ProtocolInfo.MLS) {
                callRepository.leaveMlsConference(conversationIdWithDomain)
            }
        }

        scope.launch { createAndPersistRecentlyEndedCallMetadata(conversationIdWithDomain, reason) }
    }

    private fun shouldPersistMissedCall(callMetadata: CallMetadata?, callStatus: CallStatus): Boolean = when (callStatus) {
        CallStatus.MISSED -> true
        CallStatus.CLOSED -> callMetadata?.let { metadata ->
            metadata.establishedTime.isNullOrEmpty() &&
                    metadata.callStatus != CallStatus.CLOSED_INTERNALLY &&
                    metadata.callStatus != CallStatus.REJECTED &&
                    metadata.callStatus != CallStatus.STARTED
        } ?: false
        else -> false
    }

    private fun getCallStatusFromCloseReason(reason: CallClosedReason): CallStatus = when (reason) {
        CallClosedReason.STILL_ONGOING -> CallStatus.STILL_ONGOING
        CallClosedReason.CANCELLED -> CallStatus.MISSED
        CallClosedReason.REJECTED -> CallStatus.REJECTED
        else -> CallStatus.CLOSED
    }

    private fun onCallingReady() {
        callingLogger.i("$tagWithUserId: iOS calling ready")
    }

    internal suspend fun waitUntilInitialized() {
        deferredHandle.await()
    }

    private sealed interface CallingMessageTarget {
        val specificTarget: MessageTarget

        data object Self : CallingMessageTarget {
            override val specificTarget: MessageTarget = MessageTarget.Conversation()
        }

        data class HostConversation(override val specificTarget: MessageTarget = MessageTarget.Conversation()) : CallingMessageTarget
    }

    internal companion object {
        private const val DEFAULT_REQUEST_VIDEO_STREAMS_MODE = 0
        private const val AVS_SEND_SUCCESS_STATUS_CODE = 200
        private const val AVS_SEND_FAILURE_STATUS_CODE = 400
        private const val TAG = "CallManager"
        private val DEFAULT_WAIT_UNTIL_CONNECTED_TIMEOUT = 15.seconds
    }
}
