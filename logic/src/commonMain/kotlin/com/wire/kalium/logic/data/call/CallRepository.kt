package com.wire.kalium.logic.data.call

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
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
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.call.CallApi
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlin.math.max

@Suppress("TooManyFunctions")
interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun updateCallMetadataProfileFlow(callMetadataProfile: CallMetadataProfile)
    fun getCallMetadataProfile(): CallMetadataProfile
    suspend fun callsFlow(): Flow<List<Call>>
    suspend fun incomingCallsFlow(): Flow<List<Call>>
    suspend fun ongoingCallsFlow(): Flow<List<Call>>
    suspend fun establishedCallsFlow(): Flow<List<Call>>
    suspend fun createCall(
        conversationId: ConversationId,
        status: CallStatus,
        callerId: String,
        isMuted: Boolean,
        isCameraOn: Boolean,
        shouldRing: Boolean
    )

    suspend fun updateCallStatusById(conversationId: String, status: CallStatus)
    fun updateIsMutedById(conversationId: String, isMuted: Boolean)
    fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean)
    fun updateCallParticipants(conversationId: String, participants: List<Participant>)
    fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers)
    suspend fun getLastClosedCallCreatedByConversationId(conversationId: ConversationId): Flow<String?>
    suspend fun getLastCallConversationTypeByConversationId(conversationId: ConversationId): Conversation.Type
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class CallDataSource(
    private val callApi: CallApi,
    private val persistMessage: PersistMessageUseCase,
    private val callDAO: CallDAO,
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val timeParser: TimeParser,
    private val callMapper: CallMapper = MapperProvider.callMapper()
) : CallRepository {

    private val _callMetadataProfile = MutableStateFlow(CallMetadataProfile(data = emptyMap()))

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = wrapApiRequest {
        callApi.getCallConfig(limit = limit)
    }

    override suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray> = wrapApiRequest {
        callApi.connectToSFT(url = url, data = data)
    }

    override fun updateCallMetadataProfileFlow(callMetadataProfile: CallMetadataProfile) {
        _callMetadataProfile.value = callMetadataProfile
    }

    override fun getCallMetadataProfile(): CallMetadataProfile = _callMetadataProfile.value

    override suspend fun callsFlow(): Flow<List<Call>> = callDAO
        .observeCalls()
        .combineWithCallsMetadata()

    override suspend fun incomingCallsFlow(): Flow<List<Call>> = callDAO
        .observeIncomingCalls()
        .combineWithCallsMetadata()

    override suspend fun ongoingCallsFlow(): Flow<List<Call>> = callDAO
        .observeOngoingCalls()
        .combineWithCallsMetadata()

    override suspend fun establishedCallsFlow(): Flow<List<Call>> = callDAO
        .observeEstablishedCalls()
        .combineWithCallsMetadata()

    // This needs to be reworked the logic into the useCases
    @Suppress("LongMethod", "NestedBlockDepth")
    override suspend fun createCall(
        conversationId: ConversationId,
        status: CallStatus,
        callerId: String,
        isMuted: Boolean,
        isCameraOn: Boolean,
        shouldRing: Boolean
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

        val callEntity = callMapper.toCallEntity(
            conversationId = conversationId,
            id = uuid4().toString(),
            status = status,
            conversationType = conversation.conversation.type,
            callerId = callerIdWithDomain
        )

        val metadata = CallMetadata(
            conversationName = conversation.conversation.name,
            conversationType = conversation.conversation.type,
            callerName = caller?.name,
            callerTeamName = team?.name,
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            establishedTime = null
        )

        val isCallInCurrentSession = _callMetadataProfile.value.data.containsKey(conversationId.toString())
        val lastCallStatus = callDAO.getCallStatusByConversationId(conversationId = callEntity.conversationId)

        val isOneOnOneCall = callEntity.conversationType == ConversationEntity.Type.ONE_ON_ONE
        val isGroupCall = callEntity.conversationType == ConversationEntity.Type.GROUP

        val activeCallStatus = listOf(
            CallEntity.Status.ESTABLISHED,
            CallEntity.Status.ANSWERED,
            CallEntity.Status.STILL_ONGOING
        )

        callingLogger.i(
            "[CallRepository][createCall] -> lastCallStatus: [$lastCallStatus] | ConversationId: [$conversationId] " +
                    "| status: [$status] | shouldRing: [$shouldRing]"
        )
        if (status == CallStatus.INCOMING && !isCallInCurrentSession) {
            updateCallMetadata(
                conversationId = conversationId,
                metadata = metadata
            )
            val callNewStatus = if (isGroupCall) CallStatus.STILL_ONGOING else CallStatus.CLOSED
            if (lastCallStatus in activeCallStatus) { // LAST CALL ACTIVE
                callingLogger.i("[CallRepository][createCall] -> Update.1 | callNewStatus: [$callNewStatus]")
                // Update database
                updateCallStatusById(
                    conversationId = conversationId.toString(),
                    status = callNewStatus
                )
            }

            if ((lastCallStatus !in activeCallStatus && isGroupCall) || isOneOnOneCall) {
                callingLogger.i(
                    "[CallRepository][createCall] -> Update.2 | lastCallStatus: [$lastCallStatus] " +
                            "| isGroupCall: [$isGroupCall] | isOneOnOneCall: [$isOneOnOneCall] | shouldRing: [$shouldRing]"
                )

                // Save into database
                wrapStorageRequest {
                    callDAO.insertCall(
                        call = callEntity.copy(
                            status = if (shouldRing) callEntity.status else CallEntity.Status.STILL_ONGOING
                        )
                    )
                }
            }
        } else {
            callingLogger.i("[CallRepository][createCall] -> else | lastCallStatus: [$lastCallStatus] | status: [$status]")
            if (lastCallStatus !in activeCallStatus || (status == CallStatus.STARTED)) {
                callingLogger.i("[CallRepository][createCall] -> Insert Call")
                // Save into database
                wrapStorageRequest {
                    callDAO.insertCall(call = callEntity)
                }

                // Save into metadata
                updateCallMetadata(
                    conversationId = conversationId,
                    metadata = metadata
                )
            }
        }
    }

    private fun updateCallMetadata(
        conversationId: ConversationId,
        metadata: CallMetadata
    ) {
        val callMetadataProfile = _callMetadataProfile.value
        val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
            this[conversationId.toString()] = metadata
        }

        _callMetadataProfile.value = callMetadataProfile.copy(
            data = updatedCallMetadata
        )
    }

    override suspend fun updateCallStatusById(conversationId: String, status: CallStatus) {
        val callMetadataProfile = _callMetadataProfile.value
        val modifiedConversationId = conversationId.toConversationId()

        // Update Call in Database
        wrapStorageRequest {
            callDAO.updateLastCallStatusByConversationId(
                status = callMapper.toCallEntityStatus(callStatus = status),
                conversationId = callMapper.fromConversationIdToQualifiedIDEntity(conversationId = modifiedConversationId)
            )
            callingLogger.i(
                "[CallRepository][UpdateCallStatusById] -> ConversationId: [$conversationId] " +
                        "| status: [$status]"
            )
        }

        callMetadataProfile.data[modifiedConversationId.toString()]?.let { call ->
            val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
                val establishedTime =
                    if (status == CallStatus.ESTABLISHED) timeParser.currentTimeStamp()
                    else call.establishedTime

                // Update Metadata
                this[modifiedConversationId.toString()] = call.copy(establishedTime = establishedTime)

                // Persist Missed Call Message if necessary
                if ((status == CallStatus.CLOSED && establishedTime == null) || status == CallStatus.MISSED) {
                    callingLogger.i("[CallRepository][UpdateCallStatusById] -> Persist Missed Call Message")
                    persistMissedCallMessageIfNeeded(conversationId = modifiedConversationId)
                }
            }

            _callMetadataProfile.value = callMetadataProfile.copy(
                data = updatedCallMetadata
            )
        }
    }

    override fun updateIsMutedById(conversationId: String, isMuted: Boolean) {
        val callMetadataProfile = _callMetadataProfile.value
        callMetadataProfile.data[conversationId]?.let { callMetadata ->
            val updatedCallMetaData = callMetadataProfile.data.toMutableMap().apply {
                this[conversationId] = callMetadata.copy(
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

    override suspend fun getLastClosedCallCreatedByConversationId(conversationId: ConversationId): Flow<String?> =
        callDAO.getLastClosedCallByConversationId(
            conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                conversationId = conversationId
            )
        )

    override suspend fun getLastCallConversationTypeByConversationId(conversationId: ConversationId): Conversation.Type =
        callDAO.getLastCallConversationTypeByConversationId(
            conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                conversationId = conversationId
            )
        )?.let {
            callMapper.toConversationType(conversationType = it)
        } ?: Conversation.Type.ONE_ON_ONE

    private suspend fun persistMissedCallMessageIfNeeded(
        conversationId: ConversationId
    ) {
        val callerId = callDAO.getCallerIdByConversationId(
            conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                conversationId = conversationId
            )
        )

        val message = Message.System(
            uuid4().toString(),
            MessageContent.MissedCall,
            conversationId,
            timeParser.currentTimeStamp(),
            callerId.toUserId(),
            Message.Status.SENT,
            Message.Visibility.VISIBLE
        )
        persistMessage(message)
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
