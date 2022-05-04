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
import com.wire.kalium.logic.data.call.SendCallingMessage
import com.wire.kalium.logic.data.call.UpdateCallStatusById
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.asString
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.call.scenario.OnAnsweredCall
import com.wire.kalium.logic.feature.call.scenario.OnCloseCall
import com.wire.kalium.logic.feature.call.scenario.OnConfigRequest
import com.wire.kalium.logic.feature.call.scenario.OnEstablishedCall
import com.wire.kalium.logic.feature.call.scenario.OnIncomingCall
import com.wire.kalium.logic.feature.call.scenario.OnMissedCall
import com.wire.kalium.logic.feature.call.scenario.OnSFTRequest
import com.wire.kalium.logic.feature.call.scenario.OnSendOTR
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.util.toInt
import com.wire.kalium.logic.util.toTimeInMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

actual class CallManagerImpl(
    private val calling: Calling,
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val clientRepository: ClientRepository,
    private val callMapper: CallMapper,
    val updateCallStatusById: UpdateCallStatusById,
    val messageSender: MessageSender,
) : CallManager {

    private val job = SupervisorJob() // TODO clear job method
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val deferredHandle: Deferred<Handle>

    override val allCalls = calls.asStateFlow()

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

    private fun startHandleAsync() = scope.async(start = CoroutineStart.LAZY) {
        val selfUserId = userId.await().asString()
        val selfClientId = clientId.await().value
        calling.wcall_create(
            userId = selfUserId,
            clientId = selfClientId,
            readyHandler = { version: Int, arg: Pointer? ->
                callingLogger.i("$TAG -> readyHandler")
            },
            //TODO inject all of these CallbackHandlers in class constructor
            sendHandler = OnSendOTR(deferredHandle, calling, selfUserId, selfClientId, SendCallingMessage(messageSender)),
            sftRequestHandler = OnSFTRequest(deferredHandle, calling, callRepository),
            incomingCallHandler = OnIncomingCall(updateCallStatusById),
            missedCallHandler = OnMissedCall(updateCallStatusById),
            answeredCallHandler = OnAnsweredCall(updateCallStatusById),
            establishedCallHandler = OnEstablishedCall(updateCallStatusById),
            closeCallHandler = OnCloseCall(updateCallStatusById),
            metricsHandler = { conversationId: String, metricsJson: String, arg: Pointer? ->
                callingLogger.i("$TAG -> metricsHandler")
            },
            callConfigRequestHandler = OnConfigRequest(calling, callRepository),
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
                convId = message.conversationId.asString(),
                userId = message.senderUserId.asString(),
                clientId = message.senderClientId.value
            )
            callingLogger.d("$TAG - onCallingMessageReceived")
        }

    override suspend fun startCall(
        conversationId: ConversationId,
        callType: CallType,
        conversationType: ConversationType,
        isAudioCbr: Boolean
    ) {
        callingLogger.d("$TAG -> starting call..")
        updateCallStatusById.updateCallStatus(
            conversationId = conversationId.asString(),
            status = CallStatus.STARTED
        )
        withCalling {
            val avsCallType = callMapper.toCallTypeCalling(callType)
            val avsConversationType = callMapper.toConversationTypeCalling(conversationType)
            wcall_start(
                deferredHandle.await(),
                conversationId.asString(),
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
            conversationId = conversationId.asString(),
            callType = CallTypeCalling.AUDIO.avsValue,
            cbrEnabled = false
        )
    }

    override suspend fun endCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> ending Call..")
        wcall_end(inst = deferredHandle.await(), conversationId = conversationId.asString())
    }

    override suspend fun rejectCall(conversationId: ConversationId) = withCalling {
        callingLogger.d("$TAG -> rejecting call..")
        wcall_reject(inst = deferredHandle.await(), conversationId = conversationId.asString())
    }

    override suspend fun muteCall(shouldMute: Boolean) = withCalling {
        val logString = if (shouldMute) "muting" else "un-muting"
        callingLogger.d("$TAG -> $logString call..")
        wcall_set_mute(deferredHandle.await(), muted = shouldMute.toInt())
    }

    companion object {
        const val TAG = "CallManager"
        const val UTF8_ENCODING = "UTF-8"
    }
}
