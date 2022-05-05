package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toConversationId
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
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import java.util.UUID

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun getAllCalls(): StateFlow<List<Call>>
    fun getIncomingCalls(): Flow<List<Call>>
    fun getOngoingCall(): Flow<List<Call>>
    fun updateCallStatusById(conversationId: String, status: CallStatus)
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
    private val calls = MutableStateFlow(listOf<Call>())
    private val allCalls = calls.asStateFlow()

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

    override fun getAllCalls(): StateFlow<List<Call>> = allCalls

    override fun getIncomingCalls(): Flow<List<Call>> = allCalls.map {
        it.filter { call ->
            call.status in listOf(
                CallStatus.INCOMING
            )
        }
    }

    override fun getOngoingCall(): Flow<List<Call>> = allCalls.map {
        it.filter { call -> call.status == CallStatus.ESTABLISHED }
    }

    override suspend fun sendCallingMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String
    ): Either<CoreFailure, Unit> {
        val messageContent = MessageContent.Calling(data)
        val date = Clock.System.now().toString()
        val message = Message(UUID.randomUUID().toString(), messageContent, conversationId, date, userId, clientId, Message.Status.SENT)
        return messageSender.trySendingOutgoingMessage(conversationId, message)
    }

    override fun updateCallStatusById(conversationId: String, status: CallStatus) {
        calls.update {
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
}
