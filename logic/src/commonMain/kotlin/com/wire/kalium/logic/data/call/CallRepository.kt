package com.wire.kalium.logic.data.call

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.call.CallApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.math.max

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun getAllCalls(): StateFlow<List<Call>>
    fun getIncomingCalls(): Flow<List<Call>>
    fun getOngoingCall(): Flow<List<Call>>
    fun createCall(call: Call)
    fun updateCallStatusById(conversationId: String, status: CallStatus)
    fun updateCallParticipants(conversationId: String, participants: List<Participant>)
    suspend fun sendCallingMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String
    ): Either<CoreFailure, Unit>
}

internal class CallDataSource(
    private val callApi: CallApi,
    private val messageSender: MessageSender
) : CallRepository {

    //TODO to be saved somewhere ?
    private val _callProfile = MutableStateFlow(CallProfile(calls = emptyMap()))
    private val allCalls = _callProfile.asStateFlow()

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = suspending {
        wrapApiRequest {
            callApi.getCallConfig(limit = limit)
        }
    }

    override suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray> = suspending {
        wrapApiRequest {
            callApi.connectToSFT(url = url, data = data)
        }
    }

    override fun getAllCalls(): StateFlow<List<Call>> = MutableStateFlow(allCalls.value.calls.values.toList())

    override fun getIncomingCalls(): Flow<List<Call>> = allCalls.map {
        it.calls.values.filter { call ->
            call.status in listOf(
                CallStatus.INCOMING
            )
        }
    }

    override fun getOngoingCall(): Flow<List<Call>> = allCalls.map {
        it.calls.values.filter { call -> call.status == CallStatus.ESTABLISHED }
    }

    override suspend fun sendCallingMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String
    ): Either<CoreFailure, Unit> {
        val messageContent = MessageContent.Calling(data)
        val date = Clock.System.now().toString()
        val message = Message(uuid4().toString(), messageContent, conversationId, date, userId, clientId, Message.Status.SENT)
        return messageSender.sendMessage(message)
    }

    override fun createCall(call: Call) {
        val callProfile = _callProfile.value
        val updatedCalls = callProfile.calls.toMutableMap().apply {
            this[call.conversationId.toString()] = call
        }

        _callProfile.value = callProfile.copy(
            calls = updatedCalls
        )
    }

    override fun updateCallStatusById(conversationId: String, status: CallStatus) {
        val callProfile = _callProfile.value
        callProfile.calls[conversationId]?.let { call ->
            val updatedCalls = callProfile.calls.toMutableMap().apply {
                this[conversationId] = call.copy(
                    status = status
                )
            }

            _callProfile.value = callProfile.copy(
                calls = updatedCalls
            )
        }
    }

    override fun updateCallParticipants(conversationId: String, participants: List<Participant>) {
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
}
