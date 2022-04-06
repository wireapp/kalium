package com.wire.kalium.logic.feature.call

import com.sun.jna.Pointer
import com.wire.kalium.calling.CallType
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.CallingConversationType
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.asString
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.kaliumLogger
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

actual class CallManagerImpl(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    val messageSender: MessageSender
) : CallManager, CallConfigRequestHandler {

    private val job = SupervisorJob() // TODO clear job method
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val deferredHandle: Deferred<Handle>

    private val _calls = MutableStateFlow(listOf<Call>())
    actual override val allCalls = _calls.asStateFlow()

    private val clientId: Deferred<ClientId> = scope.async(start = CoroutineStart.LAZY) {
        clientRepository.currentClientId().fold({
            TODO("adjust correct variable calling")
        }, {
            kaliumLogger.d("CallManager - clientId $it")
            it
        })
    }
    private val userId: Deferred<UserId> = scope.async(start = CoroutineStart.LAZY) {
        userRepository.getSelfUser().first().id.also {
            kaliumLogger.d("CallManager - userId $it")
        }
    }

    init {
        deferredHandle = startHandleAsync()
    }

    private fun updateCallStatusById(conversationId: String, status: CallStatus) {
        _calls.update {
            mutableListOf<Call>().apply {
                addAll(it)

                val callIndex = it.indexOfFirst { call -> call.conversationId.asString() == conversationId }
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
        }
    }

    actual override suspend fun startCall(conversationId: ConversationId, callType: CallType, conversationType: CallingConversationType, isAudioCbr: Boolean) {
        withCalling {
            wcall_start(deferredHandle.await(), conversationId.asString(), callType.avsValue, conversationType.avsValue, isAudioCbr.toInt())
        }
    }

    private fun startHandleAsync() = scope.async(start = CoroutineStart.LAZY) {
        val selfUserId = userId.await().asString()
        val selfClientId = clientId.await().value
        calling.wcall_create(
            userId = selfUserId,
            clientId = selfClientId,
            readyHandler = { version: Int, arg: Pointer? ->
                kaliumLogger.i("$TAG -> readyHandler")
            },
            sendHandler = { _, conversationId, avsSelfUserId, avsSelfClientId, _, _, data, _, _, _ ->
                if(selfUserId == avsSelfUserId && selfClientId == avsSelfClientId) AvsCallBackError.INVALID_ARGUMENT.value
                else {
                    scope.launch {
                        val messageString = data?.getString(0, UTF8_ENCODING)
                        messageString?.let { sendCallingMessage(conversationId.toConversationId(), avsSelfUserId.toUserId(), ClientId(avsSelfClientId), it) }
                    }
                    AvsCallBackError.None.value
                }
            },
            sftRequestHandler = { ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer? ->
                kaliumLogger.i("$TAG -> sftRequestHandler")
                0
            },
            incomingCallHandler = { conversationId: String, messageTime: Uint32_t, userId: String, clientId: String, isVideoCall: Boolean,
                                    shouldRing: Boolean, conversationType: Int, arg: Pointer? ->
                kaliumLogger.i("$TAG -> incomingCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.INCOMING
                )
            },
            missedCallHandler = { conversationId: String, messageTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer? ->
                kaliumLogger.i("$TAG -> missedCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.MISSED
                )
            },
            answeredCallHandler = { conversationId: String, arg: Pointer? ->
                kaliumLogger.i("$TAG -> answeredCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.ANSWERED
                )
            },
            establishedCallHandler = { conversationId: String, userId: String, clientId: String, arg: Pointer? ->
                kaliumLogger.i("$TAG -> establishedCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.ESTABLISHED
                )
            },
            closeCallHandler = { reason: Int, conversationId: String, messageTime: Uint32_t, userId: String, clientId: String,
                                 arg: Pointer? ->
                kaliumLogger.i("$TAG -> closeCallHandler")
                updateCallStatusById(
                    conversationId = conversationId,
                    status = CallStatus.CLOSED
                )
            },
            metricsHandler = { conversationId: String, metricsJson: String, arg: Pointer? ->
                kaliumLogger.i("$TAG -> metricsHandler")
            },
            callConfigRequestHandler = this@CallManagerImpl,
            constantBitRateStateChangeHandler = { userId: String, clientId: String, isEnabled: Boolean, arg: Pointer? ->
                kaliumLogger.i("$TAG -> constantBitRateStateChangeHandler")
            },
            videoReceiveStateHandler = { conversationId: String, userId: String, clientId: String, state: Int, arg: Pointer? ->
                kaliumLogger.i("$TAG -> videoReceiveStateHandler")
            },
            arg = null
        ).also {
            kaliumLogger.d("CallManager - initialized with $it")
        }
    }

    private suspend fun <T> withCalling(action: suspend Calling.(handle: Handle) -> T): T {
        val handle = deferredHandle.await()
        return calling.action(handle)
    }

    actual override suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) =
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
                convId = message.conversationId.asString(),
                userId = message.senderUserId.asString(),
                clientId = message.senderClientId.value
            )
            kaliumLogger.d("onCallingMessageReceived -> Passed through")
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
                kaliumLogger.i("onConfigRequest -> wcall_config_update")
            }
        }

        return 0
    }

    companion object {
        private const val TAG = "startHandleAsync"
        private const val UTF8_ENCODING = "UTF-8"
    }
}
