package com.wire.kalium.logic.feature.call

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.asString
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

actual class CallManager(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository
) : CallConfigRequestHandler {

    private val job = SupervisorJob() // TODO clear job method
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val deferredHandle: Deferred<Handle>

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

    private fun startHandleAsync() = scope.async(start = CoroutineStart.LAZY) {
        calling.wcall_create(
            userId = userId.await().asString(),
            clientId = clientId.await().value,
            readyHandler = { version: Int, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> readyHandler")
            },
            sendHandler = { context: Pointer?, conversationId: String, userIdSelf: String, clientIdSelf: String, userIdDestination: String?,
                            clientIdDestination: String?, data: Pointer?, length: Size_t, isTransient: Boolean, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> sendHandler")
                0
            },
            sftRequestHandler = { ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> sftRequestHandler")
                0
            },
            incomingCallHandler = { conversationId: String, messageTime: Uint32_t, userId: String, clientId: String, isVideoCall: Boolean,
                                    shouldRing: Boolean, conversationType: Int, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> incomingCallHandler")
            },
            missedCallHandler = { conversationId: String, messageTime: Uint32_t, userId: String, isVideoCall: Boolean, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> missedCallHandler")
            },
            answeredCallHandler = { conversationId: String, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> answeredCallHandler")
            },
            establishedCallHandler = { conversationId: String, userId: String, clientId: String, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> establishedCallHandler")
            },
            closeCallHandler = { reason: Int, conversationId: String, messageTime: Uint32_t, userId: String, clientId: String,
                                 arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> closeCallHandler")
            },
            metricsHandler = { conversationId: String, metricsJson: String, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> metricsHandler")
            },
            callConfigRequestHandler = this@CallManager,
            constantBitRateStateChangeHandler = { userId: String, clientId: String, isEnabled: Boolean, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> constantBitRateStateChangeHandler")
            },
            videoReceiveStateHandler = { conversationId: String, userId: String, clientId: String, state: Int, arg: Pointer? ->
                kaliumLogger.i("startHandleAsync -> videoReceiveStateHandler")
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

    actual suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) =
        withCalling {
            val msg = content.value.toByteArray()
            wcall_recv_msg(
                inst = deferredHandle.await(),
                msg = msg,
                len = msg.size,
                curr_time = Uint32_t(value = System.currentTimeMillis() / 1000),
                msg_time = Uint32_t(value = (System.currentTimeMillis() / 1000) + 10), // TODO: add correct variable
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
}
