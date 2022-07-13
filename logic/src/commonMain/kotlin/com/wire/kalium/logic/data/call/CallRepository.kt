package com.wire.kalium.logic.data.call

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.call.CallApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.math.max

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun updateCallProfileFlow(callProfile: CallProfile)
    fun callsFlow(): Flow<List<Call>>
    fun incomingCallsFlow(): Flow<List<Call>>
    fun ongoingCallsFlow(): Flow<List<Call>>
    fun establishedCallsFlow(): Flow<List<Call>>
    suspend fun createCall(
        conversationId: ConversationId,
        status: CallStatus,
        callerId: String,
        isMuted: Boolean,
        isCameraOn: Boolean
    )

    suspend fun updateCallStatusById(conversationId: String, status: CallStatus)
    fun updateIsMutedById(conversationId: String, isMuted: Boolean)
    fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean)
    fun updateCallParticipants(conversationId: String, participants: List<Participant>)
    fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers)
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class CallDataSource(
    private val callApi: CallApi,
    private val persistMessage: PersistMessageUseCase,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val timeParser: TimeParser,
    private val callMapper: CallMapper = MapperProvider.callMapper()
) : CallRepository {

    // TODO(question): to be saved somewhere?
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
                CallStatus.STILL_ONGOING
            )
        }
    }

    override fun establishedCallsFlow(): Flow<List<Call>> = allCalls.map {
        it.calls.values.filter { call ->
            call.status in listOf(
                CallStatus.ESTABLISHED,
                CallStatus.ANSWERED
            )
        }
    }

    override suspend fun createCall(
        conversationId: ConversationId,
        status: CallStatus,
        callerId: String,
        isMuted: Boolean,
        isCameraOn: Boolean
    ) {
        val conversation: ConversationDetails = conversationRepository
            .observeConversationDetailsById(conversationId)
            .first()

        // in OnIncomingCall we get callerId without a domain,
        // to cover that case and have a valid UserId we have that workaround
        // TODO fix this callerId in OnIncomingCall once we support federation
        val myId = userRepository.getSelfUserId()
        val callerIdWithDomain = UserId(callerId.toUserId().value, myId.domain)
        val caller = userRepository.getKnownUser(callerIdWithDomain).first()

        val team = caller?.teamId
            ?.let { teamId -> teamRepository.getTeam(teamId).first() }

        val call = Call(
            conversationId = conversationId,
            status = status,
            callerId = callerIdWithDomain.toString(),
            conversationName = conversation.conversation.name,
            conversationType = conversation.conversation.type,
            callerName = caller?.name,
            callerTeamName = team?.name,
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            establishedTime = null
        )

        val callProfile = _callProfile.value
        val updatedCalls = callProfile.calls.toMutableMap().apply {
            this[call.conversationId.toString()] = call
        }

        _callProfile.value = callProfile.copy(
            calls = updatedCalls
        )
    }

    override suspend fun updateCallStatusById(conversationId: String, status: CallStatus) {
        val callProfile = _callProfile.value
        val modifiedConversationId = conversationId.toConversationId().toString()
        callProfile.calls[modifiedConversationId]?.let { call ->
            val updatedCalls = callProfile.calls.toMutableMap().apply {
                val establishedTime =
                    if (status == CallStatus.ESTABLISHED) timeParser.currentTimeStamp()
                    else call.establishedTime

                this[modifiedConversationId] = call.copy(
                    status = status,
                    establishedTime = establishedTime
                )
                persistMissedCallMessageIfNeeded(this[modifiedConversationId]!!)
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

    override fun updateCallParticipants(conversationId: String, participants: List<Participant>) {
        val callProfile = _callProfile.value

        callProfile[conversationId]?.let { call ->
            callingLogger.i("updateCallParticipants() - conversationId: $conversationId with size of: ${participants.size}")

            val updatedCalls = callProfile.calls.toMutableMap().apply {
                this[conversationId] = call.copy(
                    participants = participants,
                    maxParticipants = max(call.maxParticipants, participants.size + 1)
                )
            }

            _callProfile.value = callProfile.copy(
                calls = updatedCalls
            )
        }
    }

    override fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers) {
        val callProfile = _callProfile.value

        callProfile.calls[conversationId]?.let { call ->
            callingLogger.i("updateActiveSpeakers() - conversationId: $conversationId with size of: ${activeSpeakers.activeSpeakers.size}")

            val updatedParticipants = callMapper.activeSpeakerMapper.mapParticipantsActiveSpeaker(
                participants = call.participants,
                activeSpeakers = activeSpeakers
            )

            val updatedCalls = callProfile.calls.toMutableMap().apply {
                this[conversationId] = call.copy(
                    participants = updatedParticipants,
                    maxParticipants = max(call.maxParticipants, updatedParticipants.size + 1)
                )
            }

            _callProfile.value = callProfile.copy(
                calls = updatedCalls
            )
        }
    }

    private suspend fun persistMissedCallMessageIfNeeded(call: Call) {
        if ((call.status == CallStatus.CLOSED && call.establishedTime == null) || call.status == CallStatus.MISSED) {
            val message = Message.System(
                uuid4().toString(),
                MessageContent.MissedCall,
                call.conversationId,
                timeParser.currentTimeStamp(),
                call.callerId.toUserId(),
                Message.Status.SENT,
                Message.Visibility.VISIBLE
            )
            persistMessage(message)
        }
    }
}
