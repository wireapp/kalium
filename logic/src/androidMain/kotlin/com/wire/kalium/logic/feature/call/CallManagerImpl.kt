package com.wire.kalium.logic.feature.call

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallTypeCalling
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.CallMapper
import com.wire.kalium.logic.data.call.CallProfile
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.call.CallType
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.feature.call.callback.ParticipantChangedHandlerImpl
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.toInt
import com.wire.kalium.logic.util.toTimeInMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

actual class CallManagerImpl(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val callMapper: CallMapper,
    val messageSender: MessageSender
) : CallManager, CallConfigRequestHandler, SFTRequestHandler {

    private val job = SupervisorJob() // TODO clear job method
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val deferredHandle: Deferred<Handle>
    private val _callProfile = MutableStateFlow(CallProfile(calls = emptyMap()))

    private val _calls = MutableStateFlow(listOf<Call>())
    override val allCalls = _calls.asStateFlow()

    private val clientId: Deferred<ClientId> = scope.async(start = CoroutineStart.LAZY) {
        clientRepository.currentClientId().fold({
            TODO("adjust correct variable calling")
        }, {
            callingLogger.d("$TAG - clientId $it")
            it
        })
    }
    private val userId: Deferred<UserId> = scope.async(start = CoroutineStart.LAZY) {
        userRepository.getSelfUser().first().id.also {
            callingLogger.d("$TAG - userId $it")
        }
    }

    init {
        deferredHandle = startHandleAsync()
    }

    private fun updateCallStatusById(conversationId: String, status: CallStatus) {
        _calls.update {
            val calls = mutableListOf<Call>().apply {
                addAll(it)

                val callIndex = it.indexOfFirst { call -> call.conversationId.toString() == conversationId }
                if (callIndex == -1) {
                    add(
                        Call(
                            conversationId = conversationId.toConversationId(),
                            status = status
                        )
                    )
                } else {
                    this[callIndex] = this[callIndex].copy(
                        status = status
                    )
                }
            }

            _callProfile.value = _callProfile.value.copy(
                calls = calls.associateBy { it.conversationId.toString() }
            )

            calls
        }
    }

    private fun startHandleAsync() = scope.async(start = CoroutineStart.LAZY) {
        val selfUserId = userId.await().toString()
        val selfClientId = clientId.await().value
        calling.wcall_create(
            userId = selfUserId,
            clientId = selfClientId,
            readyHandler = { version: Int, arg: Pointer? ->
                callingLogger.i("$TAG -> readyHandler")
                onCallingReady()
            },
            sendHandler = { context, conversationId, avsSelfUserId, avsSelfClientId, _, _, data, _, _, _ ->
                if (selfUserId != avsSelfUserId && selfClientId != avsSelfClientId) {
                    callingLogger.i("$TAG -> sendHandler error")
                    AvsCallBackError.INVALID_ARGUMENT.value
                } else {
                    callingLogger.i("$TAG -> sendHandler success")
                    sendHandlerSuccess(
                        context = context,
                        messageString = data?.getString(0, UTF8_ENCODING),
                        conversationId = conversationId.toConversationId(),
                        avsSelfUserId = avsSelfUserId.toUserId(),
                        avsSelfClientId = ClientId(avsSelfClientId)
                    )
                    AvsCallBackError.NONE.value
                }
            },
            sftRequestHandler = this@CallManagerImpl,
            incomingCallHandler = { conversationId: String, messageTime: Uint32_t, userId: String, clientId: String, isVideoCall: Boolean,
                                    shouldRing: Boolean, conversationType: Int, arg: Pointer? ->
                callingLogger.i("$TAG -> incomingCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.INCOMING
                )
            },
            missedCallHandler = { conversationId: String, messageTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer? ->
                callingLogger.i("$TAG -> missedCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.MISSED
                )
            },
            answeredCallHandler = { conversationId: String, arg: Pointer? ->
                callingLogger.i("$TAG -> answeredCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.ANSWERED
                )
            },
            establishedCallHandler = { conversationId: String, userId: String, clientId: String, arg: Pointer? ->
                callingLogger.i("$TAG -> establishedCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.ESTABLISHED
                )
            },
            closeCallHandler = { reason: Int, conversationId: String, messageTime: Uint32_t, userId: String, clientId: String,
                                 arg: Pointer? ->
                callingLogger.i("$TAG -> closeCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.CLOSED
                )
            },
            metricsHandler = { conversationId: String, metricsJson: String, arg: Pointer? ->
                callingLogger.i("$TAG -> metricsHandler")
            },
            callConfigRequestHandler = this@CallManagerImpl,
            constantBitRateStateChangeHandler = { userId: String, clientId: String, isEnabled: Boolean, arg: Pointer? ->
                callingLogger.i("$TAG -> constantBitRateStateChangeHandler")
            },
            videoReceiveStateHandler = { conversationId: String, userId: String, clientId: String, state: Int, arg: Pointer? ->
                callingLogger.i("$TAG -> videoReceiveStateHandler")
            },
            arg = null
        )
    }

    private suspend fun <T> withCalling(action: suspend Calling.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return calling.action(handle)
    }

    override suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) =
        withCalling {
            val msg = content.value.toByteArray()

            val currTime = System.currentTimeMillis()
            val msgTime = message.date.toTimeInMillis()

            wcall_recv_msg(
                inst = deferredHandle.await(),
                msg = msg,
                len = msg.size,
                curr_time = Uint32_t(value = currTime / 1000),
                msg_time = Uint32_t(value = msgTime / 1000),
                convId = message.conversationId.toString(),
                userId = message.senderUserId.toString(),
                clientId = message.senderClientId.value
            )
            callingLogger.d("$TAG - onCallingMessageReceived")
        }

    private fun sendHandlerSuccess(
        context: Pointer?,
        messageString: String?,
        conversationId: ConversationId,
        avsSelfUserId: UserId,
        avsSelfClientId: ClientId
    ) {
        scope.launch {
            messageString?.let { message ->
                withCalling {
                    when (sendCallingMessage(conversationId, avsSelfUserId, avsSelfClientId, message)) {
                        is Either.Right -> {
                            wcall_resp(
                                inst = deferredHandle.await(),
                                status = 200,
                                reason = "",
                                arg = context
                            )
                        }
                        is Either.Left -> {
                            wcall_resp(
                                inst = deferredHandle.await(),
                                status = 400, // TODO: Handle the errorCode from CoreFailure
                                reason = "Couldn't send Calling Message",
                                arg = context
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationType: ConversationType,
        isAudioCbr: Boolean
    ) {
        callingLogger.d("$TAG -> starting call..")
        updateCallStatusById(
            conversationId = conversationId.toString(),
            status = CallStatus.STARTED
        )
        withCalling {
            val avsCallType = callMapper.toCallTypeCalling(callType)
            val avsConversationType = callMapper.toConversationTypeCalling(conversationType)
            wcall_start(
                deferredHandle.await(),
                conversationId.toString(),
                avsCallType.avsValue,
                avsConversationType.avsValue,
                isAudioCbr.toInt()
            )
        }
    }

    override suspend fun answerCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> answering call..")
        calling.wcall_answer(
            inst = deferredHandle.await(),
            conversationId = conversationId.toString(),
            callType = CallTypeCalling.AUDIO.avsValue,
            cbrEnabled = false
        )
    }

    override suspend fun endCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> ending Call..")
        wcall_end(inst = deferredHandle.await(), conversationId = conversationId.toString())
    }

    override suspend fun rejectCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> rejecting call..")
        wcall_reject(inst = deferredHandle.await(), conversationId = conversationId.toString())
    }

    override suspend fun muteCall(shouldMute: Boolean) = withCalling {
        val logString = if (shouldMute) "muting" else "un-muting"
        callingLogger.d("$TAG -> $logString call..")
        wcall_set_mute(deferredHandle.await(), muted = shouldMute.toInt())
    }

    override fun onConfigRequest(inst: Handle, arg: Pointer?): Int {
        scope.launch {
            val config = callRepository.getCallConfigResponse(limit = null)
                .fold({
                    TODO("")
                }, {
                    it
                })

            withCalling {
                wcall_config_update(
                    inst = inst,
                    error = 0, // TODO: http error from internal json
                    jsonString = config
                )
            }
            callingLogger.i("$TAG - onConfigRequest")
        }

        return AvsCallBackError.NONE.value
    }

    private suspend fun onSFTResponse(data: ByteArray?, context: Pointer?) {
        withCalling {
            val responseData = data ?: byteArrayOf()
            wcall_sft_resp(
                inst = deferredHandle.await(),
                error = data?.let { AvsSFTError.NONE.value } ?: AvsSFTError.NO_RESPONSE_DATA.value,
                data = responseData,
                length = responseData.size,
                ctx = context
            )
            callingLogger.i("SFT Response sent.")
        }
    }

    override fun onSFTRequest(ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer?): Int {
        scope.launch {
            val dataString = data?.getString(0, UTF8_ENCODING)
            dataString?.let {
                val responseData = callRepository.connectToSFT(
                    url = url,
                    data = dataString
                ).fold({
                    callingLogger.i("Could not connect to SFT server.")
                    null
                }, {
                    callingLogger.i("Connected to SFT server.")
                    it
                })

                onSFTResponse(data = responseData, context = ctx)
            }
        }

        callingLogger.i("$TAG -> sftRequestHandler")
        return AvsCallBackError.NONE.value
    }

    /**
     * onCallingReady
     * Will start the handlers for: ParticipantsChanged, NetworkQuality, ClientsRequest and ActiveSpeaker
     */
    private fun onCallingReady() {
        val participantChangedHandler = ParticipantChangedHandlerImpl(
            participantMapper = callMapper.participantMapper,
            onParticipantsChanged = { conversationId, participants, clients ->
                onParticipantsChanged(
                    conversationId = conversationId,
                    participants = participants
                )

                scope.launch {
                    withCalling {
                        wcall_request_video_streams(
                            inst = deferredHandle.await(),
                            convId = conversationId,
                            mode = DEFAULT_REQUEST_VIDEO_STREAMS_MODE,
                            json = clients.toJsonString()
                        )
                    }
                }
            }
        )

        scope.launch {
            withCalling {
                wcall_set_participant_changed_handler(
                    inst = deferredHandle.await(),
                    wcall_participant_changed_h = participantChangedHandler,
                    arg = null
                )
            }
        }

        // TODO: Network Quality handler
        // TODO: Clients Request handler
        // TODO: Active Speakers handler
    }

    private fun onParticipantsChanged(
        conversationId: String,
        participants: List<Participant>
    ) {
        val callProfile = _callProfile.value

        callProfile[conversationId]?.let {
            callingLogger.i("onParticipantsChanged() - conversationId: $conversationId")
            participants.forEachIndexed { index, participant ->
                callingLogger.i("onParticipantsChanged() - Participant[$index/${participants.size}]: ${participant.id}")
            }

            _callProfile.value = callProfile.copy(
                calls = callProfile.calls.apply {
                    this.toMutableMap()[conversationId] = it.copy(
                        participants = participants,
                        maxParticipants = max(it.maxParticipants, participants.size + 1)
                    )
                }
            )
        }
    }

    companion object {
        private const val TAG = "CallManager"
        private const val UTF8_ENCODING = "UTF-8"
        private const val DEFAULT_REQUEST_VIDEO_STREAMS_MODE = 0
    }
}
