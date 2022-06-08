package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.call.CallApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlin.math.max

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun updateCallProfileFlow(callProfile: CallProfile)
    fun callsFlow(): Flow<List<Call>>
    fun incomingCallsFlow(): Flow<List<Call>>
    fun ongoingCallsFlow(): Flow<List<Call>>
    fun createCall(call: Call)
    fun removeCallById(conversationId: String)
    fun updateCallStatusById(conversationId: String, status: CallStatus)
    fun updateIsMutedById(conversationId: String, isMuted: Boolean)
    fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean)
    fun updateCallParticipants(conversationId: String, participants: List<Participant>)
    fun updateActiveSpeakers(conversationId: String, activeSpeakers: List<ActiveSpeaker>)
}

internal class CallDataSource(
    private val callApi: CallApi
) : CallRepository {

    //TODO(question): to be saved somewhere ?
    private val _callProfile = MutableStateFlow(CallProfile(calls = emptyMap()))
    private val allCalls = _callProfile.asStateFlow()

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = wrapApiRequest {
        callApi.getCallConfig(limit = limit)
    }

    override suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray> = wrapApiRequest {
        callApi.connectToSFT(url = url, data = data)
    }

    override fun updateCallProfileFlow(callProfile: CallProfile) {
        _callProfile.value = callProfile
    }

    override fun callsFlow(): Flow<List<Call>> = allCalls.map {
        it.calls.values.toList()
    }

    override fun incomingCallsFlow(): Flow<List<Call>> = allCalls.map {
        it.calls.values.filter { call ->
            call.status == CallStatus.INCOMING
        }
    }

        override fun ongoingCallsFlow(): Flow<List<Call>> = allCalls.map {
        it.calls.values.filter { call ->
            call.status in listOf(
                CallStatus.ESTABLISHED,
                CallStatus.ANSWERED
            )
        }
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

    override fun updateIsMutedById(conversationId: String, isMuted: Boolean) {
        val callProfile = _callProfile.value
        callProfile.calls[conversationId]?.let { call ->
            val updatedCalls = callProfile.calls.toMutableMap().apply {
                this[conversationId] = call.copy(
                    isMuted = isMuted
                )
            }

            _callProfile.value = callProfile.copy(
                calls = updatedCalls
            )
        }
    }

    override fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean) {
        val callProfile = _callProfile.value
        callProfile.calls[conversationId]?.let { call ->
            val updatedCalls = callProfile.calls.toMutableMap().apply {
                this[conversationId] = call.copy(
                    isCameraOn = isCameraOn
                )
            }

            _callProfile.value = callProfile.copy(
                calls = updatedCalls
            )
        }
    }

    override fun removeCallById(conversationId: String) {
        val callProfile = _callProfile.value
        val oldValues = callProfile.calls.filterKeys { it != conversationId }
        _callProfile.value = CallProfile(oldValues)
    }

    override fun updateCallParticipants(conversationId: String, participants: List<Participant>) {
        val callProfile = _callProfile.value

        callProfile[conversationId]?.let {
            callingLogger.i("updateCallParticipants() - conversationId: $conversationId with size of: ${participants.size}")

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

    override fun updateActiveSpeakers(conversationId: String, activeSpeakers: List<ActiveSpeaker>) {
        val callProfile = _callProfile.value

        callProfile[conversationId]?.let {
            callingLogger.i("updateActiveSpeakers() - conversationId: $conversationId with size of: ${activeSpeakers.size}")

            _callProfile.value = callProfile.copy(
                calls = callProfile.calls.apply {
                    this.toMutableMap()[conversationId] = it.copy(
                        activeSpeakers = activeSpeakers
                    )
                }
            )
        }
    }
}
