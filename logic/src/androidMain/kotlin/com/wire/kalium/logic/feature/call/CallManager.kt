package com.wire.kalium.logic.feature.call

import com.sun.jna.Pointer
import com.wire.kalium.calling.Calling
import com.wire.kalium.calling.ENVIRONMENT_DEFAULT
import com.wire.kalium.calling.callbacks.LogHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import com.wire.kalium.calling.types.Uint32_t
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.asString
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

actual class CallManager actual constructor(
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val callRepository: CallRepository
) {

    private lateinit var calling: Calling
    private lateinit var logHandler: LogHandler

    companion object {
        private var wCall: Handle? = null
    }

    actual suspend fun start() {
        if (wCall != null) return
        val userId = userRepository.getSelfUser().first().id
        val clientId = clientRepository.currentClientId().fold({
            TODO("")
        }, {
            it
        })

        configureInstances()

        calling.wcall_init(env = ENVIRONMENT_DEFAULT)
        calling.wcall_set_log_handler(
            logHandler = logHandler,
            arg = null
        )

        // start listening events(calls)

        wCall = calling.wcall_create(
            userId = userId.asString(),
            clientId = clientId.value,
            readyHandler = { version: Int, arg: Pointer? ->
                kaliumLogger.d("WCALL_CREATE -> readyHandler")
            },
            sendHandler = { context: Pointer?,
                            conversationId: String,
                            userIdSelf: String,
                            clientIdSelf: String,
                            userIdDestination: String?,
                            clientIdDestination: String?,
                            data: Pointer?,
                            length: Size_t,
                            isTransient: Boolean,
                            arg: Pointer? ->
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
            callConfigRequestHandler = { inst: Handle, arg: Pointer? ->
                kaliumLogger.d("WCALL_CREATE -> callConfigRequestHandler")
                val config = runBlocking {
                    callRepository.getCallConfigResponse(limit = null)
                        .fold({
                            TODO("")
                        }, {
                            it
                        })
                }
                calling.wcall_config_update(
                    inst = wCall!!,
                    error = 0, // TODO: http error from internal json
                    jsonString = config
                )
                0
            },
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


        //
    }

    actual suspend fun onCallingMessageReceived(message: Message, content: MessageContent.Calling) {
        kaliumLogger.d("Arrived in onCallingMessageReceived()")
        val msg = content.value.toByteArray()
        calling.wcall_recv_msg(
            inst = wCall!!,
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

    actual suspend fun activeCallsIDs(): Flow<List<String>> {
//        Callin
//        AVSystem.load()
        // todo: get avs
        return flowOf(listOf(""))
    }

    private fun configureInstances() {
        logHandler = LogHandlerImpl()
        calling = Calling.INSTANCE
    }

    class LogHandlerImpl : LogHandler {
        override fun onLog(level: Int, message: String, arg: Pointer?) {
            when (level) {
                0 -> kaliumLogger.d(message)
                1 -> kaliumLogger.i(message)
                2 -> kaliumLogger.w(message)
                3 -> kaliumLogger.e(message)
            }
        }
    }
}
