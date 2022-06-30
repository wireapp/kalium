package com.wire.kalium.logic.data.call

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.toUserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.math.max

interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun updateCallMetadataProfileFlow(callMetadataProfile: CallMetaDataProfile)
    suspend fun callsFlow(): Flow<List<Call>>
    suspend fun incomingCallsFlow(): Flow<List<Call>>
    suspend fun ongoingCallsFlow(): Flow<List<Call>>
    suspend fun establishedCallsFlow(): Flow<List<Call>>
    suspend fun createCall(conversationId: ConversationId, status: CallStatus, callerId: String, isMuted: Boolean, isCameraOn: Boolean)
    suspend fun updateCallStatusById(conversationId: String, status: CallStatus)
    suspend fun isNotOngoingCall(conversationId: String): Boolean
    fun updateIsMutedById(conversationId: String, isMuted: Boolean)
    fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean)
    fun updateCallParticipants(conversationId: String, participants: List<Participant>)
    fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers)
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class CallDataSource(
    private val callApi: CallApi,
    private val callDAO: CallDAO,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val timeParser: TimeParser,
    private val messageRepository: MessageRepository,
    private val callMapper: CallMapper = MapperProvider.callMapper()
) : CallRepository {

    private val _callMetadataProfile = MutableStateFlow(CallMetaDataProfile(data = emptyMap()))

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = wrapApiRequest {
        callApi.getCallConfig(limit = limit)
    }

    override suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray> = wrapApiRequest {
        callApi.connectToSFT(url = url, data = data)
    }

    // TODO: THIS WILL BREAK TESTS
    override fun updateCallMetadataProfileFlow(callMetadataProfile: CallMetaDataProfile) {
        _callMetadataProfile.value = callMetadataProfile
    }

    override suspend fun callsFlow(): Flow<List<Call>> = callDAO
        .getCalls()
        .combineWithCallsMetadata()

    override suspend fun incomingCallsFlow(): Flow<List<Call>> = callDAO
        .getIncomingCalls()
        .combineWithCallsMetadata()

    override suspend fun ongoingCallsFlow(): Flow<List<Call>> = callDAO
        .getOngoingCalls()
        .combineWithCallsMetadata()

    override suspend fun establishedCallsFlow(): Flow<List<Call>> = callDAO
        .getEstablishedCalls()
        .combineWithCallsMetadata()

    override suspend fun isNotOngoingCall(conversationId: String): Boolean {
        val modifiedConversationId = callMapper.fromConversationIdToQualifiedIDEntity(
            conversationId = conversationId.toConversationId()
        )

        return callDAO
            .isOngoingCall(
                conversationId = modifiedConversationId
            )
            .not()
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
        //TODO fix this callerId in OnIncomingCall once we support federation
        val myId = userRepository.getSelfUserId()
        val callerIdWithDomain = UserId(callerId.toUserId().value, myId.domain)
        val caller = userRepository.getKnownUser(callerIdWithDomain).first()

        val team = caller?.team
            ?.let { teamId -> teamRepository.getTeam(teamId).first() }

        val callEntity = callMapper.toCallEntity(
            conversationId = conversationId,
            id = uuid4().toString(),
            status = status,
            conversationType = conversation.conversation.type,
            callerId = callerIdWithDomain
        )

        val metadata = CallMetaData(
            conversationName = conversation.conversation.name,
            conversationType = conversation.conversation.type,
            callerName = caller?.name,
            callerTeamName = team?.name,
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            establishedTime = null
        )

        // Save into database
        callDAO.insertCall(call = callEntity)

        // Save into metadata
        val callMetadataProfile = _callMetadataProfile.value
        val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
            this[conversationId.toString()] = metadata
        }

        _callMetadataProfile.value = callMetadataProfile.copy(
            data = updatedCallMetadata
        )
    }

    override suspend fun updateCallStatusById(conversationId: String, status: CallStatus) {
        val callMetaDataProfile = _callMetadataProfile.value
        val modifiedConversationId = conversationId.toConversationId()
        callMetaDataProfile.data[modifiedConversationId.toString()]?.let { call ->
            val updatedCallMetadata = callMetaDataProfile.data.toMutableMap().apply {
                val establishedTime =
                    if (status == CallStatus.ESTABLISHED) timeParser.currentTimeStamp()
                    else call.establishedTime

                // Update Metadata
                this[modifiedConversationId.toString()] = call.copy(establishedTime = establishedTime)

                // Update Call in Database
                callDAO.updateLastCallStatusByConversationId(
                    status = callMapper.toCallEntityStatus(callStatus = status),
                    conversationId = callMapper.fromConversationIdToQualifiedIDEntity(conversationId = modifiedConversationId)
                )

                // Persist Missed Call Message if necessary
                if ((status == CallStatus.CLOSED && establishedTime == null) || status == CallStatus.MISSED) {
                    persistMissedCallMessage(conversationId = modifiedConversationId)
                }
            }

            _callMetadataProfile.value = callMetaDataProfile.copy(
                data = updatedCallMetadata
            )
        }
    }

    override fun updateIsMutedById(conversationId: String, isMuted: Boolean) {
        val callMetadataProfile = _callMetadataProfile.value
        callMetadataProfile.data[conversationId]?.let { callMetaData ->
            val updatedCallMetaData = callMetadataProfile.data.toMutableMap().apply {
                this[conversationId] = callMetaData.copy(
                    isMuted = isMuted
                )
            }

            _callMetadataProfile.value = callMetadataProfile.copy(
                data = updatedCallMetaData
            )
        }
    }

    override fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean) {
        val callMetadataProfile = _callMetadataProfile.value
        callMetadataProfile.data[conversationId]?.let { call ->
            val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
                this[conversationId] = call.copy(
                    isCameraOn = isCameraOn
                )
            }

            _callMetadataProfile.value = callMetadataProfile.copy(
                data = updatedCallMetadata
            )
        }
    }

    override fun updateCallParticipants(conversationId: String, participants: List<Participant>) {
        val callMetadataProfile = _callMetadataProfile.value

        callMetadataProfile.data[conversationId]?.let { call ->
            callingLogger.i("updateCallParticipants() - conversationId: $conversationId with size of: ${participants.size}")

            val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
                this[conversationId] = call.copy(
                    participants = participants,
                    maxParticipants = max(call.maxParticipants, participants.size + 1)
                )
            }

            _callMetadataProfile.value = callMetadataProfile.copy(
                data = updatedCallMetadata
            )
        }
    }

    override fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers) {
        val callMetadataProfile = _callMetadataProfile.value

        callMetadataProfile.data[conversationId]?.let { call ->
            callingLogger.i("updateActiveSpeakers() - conversationId: $conversationId with size of: ${activeSpeakers.activeSpeakers.size}")

            val updatedParticipants = callMapper.activeSpeakerMapper.mapParticipantsActiveSpeaker(
                participants = call.participants,
                activeSpeakers = activeSpeakers
            )

            val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
                this[conversationId] = call.copy(
                    participants = updatedParticipants,
                    maxParticipants = max(call.maxParticipants, updatedParticipants.size + 1)
                )
            }

            _callMetadataProfile.value = callMetadataProfile.copy(
                data = updatedCallMetadata
            )
        }
    }

    private suspend fun persistMissedCallMessage(
        conversationId: ConversationId
    ) {
        val callerId = callDAO.getCallerIdByConversationId(
            conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                conversationId = conversationId
            )
        )

        messageRepository.persistMessage(
            Message.System(
                uuid4().toString(),
                MessageContent.MissedCall,
                conversationId,
                timeParser.currentTimeStamp(),
                callerId.toUserId(),
                Message.Status.SENT,
                Message.Visibility.VISIBLE
            )
        )
    }

    private fun Flow<List<CallEntity>>.combineWithCallsMetadata(): Flow<List<Call>> =
        this.combine(_callMetadataProfile) { calls, metadata ->
            calls.map { call ->
                val conversationId = ConversationId(
                    value = call.conversationId.value,
                    domain = call.conversationId.domain
                )

                callMapper.toCall(
                    callEntity = call,
                    metadata = metadata.data[conversationId.toString()]
                )
            }
        }
}
