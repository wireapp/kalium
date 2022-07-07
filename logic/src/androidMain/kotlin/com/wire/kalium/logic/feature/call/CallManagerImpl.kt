package com.wire.kalium.logic.feature.call

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.call.VideoState
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.scenario.OnActiveSpeakers
import com.wire.kalium.logic.feature.call.scenario.OnAnsweredCall
import com.wire.kalium.logic.feature.call.scenario.OnClientsRequest
import com.wire.kalium.logic.feature.call.scenario.OnCloseCall
import com.wire.kalium.logic.feature.call.scenario.OnConfigRequest
import com.wire.kalium.logic.feature.call.scenario.OnEstablishedCall
import com.wire.kalium.logic.feature.call.scenario.OnIncomingCall
import com.wire.kalium.logic.feature.call.scenario.OnMissedCall
import com.wire.kalium.logic.feature.call.scenario.OnNetworkQualityChanged
import com.wire.kalium.logic.feature.call.scenario.OnParticipantListChanged
import com.wire.kalium.logic.feature.call.scenario.OnSFTRequest
import com.wire.kalium.logic.feature.call.scenario.OnSendOTR
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.util.toInt
import com.wire.kalium.logic.util.toTimeInMillis
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Suppress("LongParameterList", "TooManyFunctions")
actual class CallManagerImpl(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    kaliumDispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    private val callMapper: CallMapper = MapperProvider.callMapper(),
    private val federatedIdMapper: FederatedIdMapper
) : CallManager {

    private val job = SupervisorJob() // TODO(calling): clear job method
    private val scope = CoroutineScope(job + kaliumDispatchers.io)
    private val deferredHandle: Deferred<Handle> = startHandleAsync()

    private val strongReferences = mutableListOf<Any>()
    private fun <T : Any> T.keepingStrongReference(): T {
        strongReferences.add(this)
        return this
    }

    private val clientId: Deferred<ClientId> = scope.async(start = CoroutineStart.LAZY) {
        clientRepository.currentClientId().fold({
            TODO("adjust correct variable calling")
        }, {
            callingLogger.d("$TAG - clientId $it")
            it
        })
    }
    private val userId: Deferred<UserId> = scope.async(start = CoroutineStart.LAZY) {
        userRepository.observeSelfUser().first().id.also {
            callingLogger.d("$TAG - userId $it")
        }
    }

    private fun startHandleAsync(): Deferred<Handle> = scope.async(start = CoroutineStart.LAZY) {
        val selfUserId = federatedIdMapper.parseToFederatedId(userId.await())
        val selfClientId = clientId.await().value

        val waitInitializationJob = Job()

        val handle = calling.wcall_create(
            userId = selfUserId,
            clientId = selfClientId,
            readyHandler = { version: Int, arg: Pointer? ->
                callingLogger.i("$TAG -> readyHandler")
                onCallingReady()
                waitInitializationJob.complete()
                Unit
            }.keepingStrongReference(),
            // TODO(refactor): inject all of these CallbackHandlers in class constructor
            sendHandler = OnSendOTR(
                deferredHandle,
                calling,
                selfUserId,
                selfClientId,
                messageSender,
                scope
            ).keepingStrongReference(),
            sftRequestHandler = OnSFTRequest(deferredHandle, calling, callRepository, scope).keepingStrongReference(),
            incomingCallHandler = OnIncomingCall(callRepository, callMapper, federatedIdMapper, scope).keepingStrongReference(),
            missedCallHandler = OnMissedCall(callRepository, scope).keepingStrongReference(),
            answeredCallHandler = OnAnsweredCall(callRepository, scope).keepingStrongReference(),
            establishedCallHandler = OnEstablishedCall(callRepository, scope).keepingStrongReference(),
            closeCallHandler = OnCloseCall(callRepository, scope).keepingStrongReference(),
            metricsHandler = { conversationId: String, metricsJson: String, arg: Pointer? ->
                callingLogger.i("$TAG -> metricsHandler")
            }.keepingStrongReference(),
            callConfigRequestHandler = OnConfigRequest(calling, callRepository, scope).keepingStrongReference(),
            constantBitRateStateChangeHandler = { userId: String, clientId: String, isEnabled: Boolean, arg: Pointer? ->
                callingLogger.i("$TAG -> constantBitRateStateChangeHandler")
            }.keepingStrongReference(),
            videoReceiveStateHandler = { conversationId: String, userId: String, clientId: String, state: Int, arg: Pointer? ->
                callingLogger.i("$TAG -> videoReceiveStateHandler")
            }.keepingStrongReference(),
            arg = null
        )
        callingLogger.d("$TAG - wcall_create() called")
        // TODO(edge-case): Add a timeout. Perhaps make some functions (like onCallingMessageReceived) return Eithers.
        waitInitializationJob.join()
        handle
    }

    private suspend fun <T> withCalling(action: suspend Calling.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return calling.action(handle)
    }

    override suspend fun onCallingMessageReceived(message: Message.Regular, content: MessageContent.Calling) =
        withCalling {
            callingLogger.i("$TAG - onCallingMessageReceived called")
            val msg = content.value.toByteArray()

            val currTime = System.currentTimeMillis()
            val msgTime = message.date.toTimeInMillis()

            wcall_recv_msg(
                inst = deferredHandle.await(),
                msg = msg,
                len = msg.size,
                curr_time = Uint32_t(value = currTime / 1000),
                msg_time = Uint32_t(value = msgTime / 1000),
                convId = federatedIdMapper.parseToFederatedId(message.conversationId),
                userId = federatedIdMapper.parseToFederatedId(message.senderUserId),
                clientId = message.senderClientId.value
            )
            callingLogger.i("$TAG - wcall_recv_msg() called")
        }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationType: ConversationType,
        isAudioCbr: Boolean
    ) {
        callingLogger.d("$TAG -> starting call for conversation = $conversationId..")

        val isCameraOn = callType == CallType.VIDEO
        callRepository.createCall(
            conversationId = conversationId,
            status = CallStatus.STARTED,
            isMuted = false,
            isCameraOn = isCameraOn,
            callerId = federatedIdMapper.parseToFederatedId(userId.await())
        )

        withCalling {
            val avsCallType = callMapper.toCallTypeCalling(callType)
            val avsConversationType = callMapper.toConversationTypeCalling(conversationType)
            wcall_start(
                deferredHandle.await(),
                federatedIdMapper.parseToFederatedId(conversationId),
                avsCallType.avsValue,
                avsConversationType.avsValue,
                isAudioCbr.toInt()
            )

            callingLogger.d("$TAG - wcall_start() called -> Call for conversation = $conversationId started")
        }
    }

    override suspend fun answerCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> answering call for conversation = $conversationId..")
        wcall_answer(
            inst = deferredHandle.await(),
            conversationId = federatedIdMapper.parseToFederatedId(conversationId),
            callType = CallTypeCalling.AUDIO.avsValue,
            cbrEnabled = false
        )
        callingLogger.d("$TAG - wcall_answer() called -> Incoming call for conversation = $conversationId answered")
    }

    override suspend fun endCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> ending Call for conversation = $conversationId..")
        wcall_end(inst = deferredHandle.await(), conversationId = federatedIdMapper.parseToFederatedId(conversationId))
        callingLogger.d("$TAG - wcall_end() called -> call for conversation = $conversationId ended")
    }

    override suspend fun rejectCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> rejecting call for conversation = $conversationId..")
        wcall_reject(inst = deferredHandle.await(), conversationId = federatedIdMapper.parseToFederatedId(conversationId))
        callingLogger.d("$TAG - wcall_reject() called -> call for conversation = $conversationId rejected")
    }

    override suspend fun muteCall(shouldMute: Boolean) = withCalling {
        val logString = if (shouldMute) "muting" else "un-muting"
        callingLogger.d("$TAG -> $logString call..")
        wcall_set_mute(deferredHandle.await(), muted = shouldMute.toInt())
        callingLogger.d("$TAG - wcall_set_mute() called")
    }

    /**
     * This method should NOT be called while the call is still incoming or outgoing and not established yet.
     */
    override suspend fun updateVideoState(conversationId: ConversationId, videoState: VideoState) {
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

    /**
     * onCallingReady
     * Will start the handlers for: ParticipantsChanged, NetworkQuality, ClientsRequest and ActiveSpeaker
     */
    private fun onCallingReady() {
        initParticipantsHandler()
        initNetworkHandler()
        initClientsHandler()
        initActiveSpeakersHandler()
    }

    private fun initParticipantsHandler() {
        scope.launch {
            withCalling {
                val onParticipantListChanged = OnParticipantListChanged(
                    handle = deferredHandle.await(),
                    calling = calling,
                    callRepository = callRepository,
                    participantMapper = callMapper.participantMapper,
                    userRepository = userRepository,
                    conversationRepository = conversationRepository,
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
                val selfUserId = federatedIdMapper.parseToFederatedId(userId.await())

                val onClientsRequest = OnClientsRequest(
                    calling = calling,
                    selfUserId = selfUserId,
                    conversationRepository = conversationRepository,
                    federatedIdMapper = federatedIdMapper,
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
                    callRepository = callRepository
                ).keepingStrongReference()

                wcall_set_active_speaker_handler(
                    inst = deferredHandle.await(),
                    activeSpeakersHandler = activeSpeakersHandler
                )

                callingLogger.d("$TAG - wcall_set_req_clients_handler() called")
            }
        }
    }

    companion object {
        const val TAG = "CallManager"
        const val NETWORK_QUALITY_INTERVAL_SECONDS = 5
        const val UTF8_ENCODING = "UTF-8"
    }
}
