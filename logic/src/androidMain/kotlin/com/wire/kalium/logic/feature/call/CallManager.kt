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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

actual class CallManager(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository
): CallConfigRequestHandler {

    private val handle: Handle
    private val job = SupervisorJob() // TODO clear job method
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private val clientId by lazy {
        runBlocking {
            clientRepository.currentClientId().fold({
               TODO("adjust correct variable calling")
            }, {
                it
            })
        }
    }
    private val userId by lazy {
        runBlocking {
            userRepository.getSelfUser().first().id
        }
    }

    init {
         handle = startHandle()
    }

    private fun startHandle() = calling.wcall_create(
        userId = userId.asString(),
        clientId = clientId.value,
        readyHandler = { version: Int, arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> readyHandler")
        },
        sendHandler = { context: Pointer?, conversationId: String, userIdSelf: String, clientIdSelf: String, userIdDestination: String?,
                        clientIdDestination: String?, data: Pointer?, length: Size_t, isTransient: Boolean, arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> sendHandler")
            0
        },
        sftRequestHandler = { ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> sftRequestHandler")
            0
        },
        incomingCallHandler = { conversationId: String,
                                messageTime: Uint32_t,
                                userId: String,
                                clientId: String,
                                isVideoCall: Boolean,
                                shouldRing: Boolean,
                                conversationType: Int,
                                arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> incomingCallHandler")
        },
        missedCallHandler = { conversationId: String,
                              messageTime: Uint32_t,
                              userId: String,
                              isVideoCall: Boolean,
                              arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> missedCallHandler")
        },
        answeredCallHandler = { conversationId: String, arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> answeredCallHandler")
        },
        establishedCallHandler = { conversationId: String, userId: String, clientId: String, arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> establishedCallHandler")
        },
        closeCallHandler = { reason: Int,
                             conversationId: String,
                             messageTime: Uint32_t,
                             userId: String,
                             clientId: String,
                             arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> closeCallHandler")
        },
        metricsHandler = { conversationId: String, metricsJson: String, arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> metricsHandler")
        },
        callConfigRequestHandler = this,
        constantBitRateStateChangeHandler = { userId: String,
                                              clientId: String,
                                              isEnabled: Boolean,
                                              arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> constantBitRateStateChangeHandler")
        },
        videoReceiveStateHandler = { conversationId: String,
                                     userId: String,
                                     clientId: String,
                                     state: Int,
                                     arg: Pointer? ->
            kaliumLogger.d("WCALL_CREATE -> videoReceiveStateHandler")
        },
        arg = null
    )

    actual suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) {
        kaliumLogger.d("Arrived in onCallingMessageReceived()")
        val msg = content.value.toByteArray()
        calling.wcall_recv_msg(
            inst = handle,
            msg = msg,
            len = msg.size,
            curr_time = Uint32_t(value = System.currentTimeMillis() / 1000),
            msg_time = Uint32_t(value = (System.currentTimeMillis() / 1000) + 10), // TODO: add correct variable
            convId = message.conversationId.asString(),
            userId = message.senderUserId.asString(),
            clientId = message.senderClientId.value
        )
        kaliumLogger.d("Passed through onCallingMessageReceived()")
    }

    override fun onConfigRequest(inst: Handle, arg: Pointer?): Int {
        kaliumLogger.d("WCALL_CREATE -> callConfigRequestHandler")
        scope.launch {
            kaliumLogger.d("SCOPE_LAUNCH -> getCallConfigResponse")
            val config = callRepository.getCallConfigResponse(limit = null)
                .fold({
                    TODO("")
                }, {
                    it
                })

            kaliumLogger.d("SCOPE_LAUNCH -> wcall_config_update")
            calling.wcall_config_update(
                inst = inst,
                error = 0, // TODO: http error from internal json
                jsonString = config
            )
        }

        kaliumLogger.d("onConfigRequest -> returning 0")
        return 0
    }
}
