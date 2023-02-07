/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.call

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.call.mapper.ActiveSpeakerMapper
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.call.Call
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapCryptoRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.math.max

internal val CALL_SUBCONVERSATION_ID = SubconversationId("conference")

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
    suspend fun establishedCallConversationId(): ConversationId?
    suspend fun createCall(
        conversationId: ConversationId,
        status: CallStatus,
        callerId: String,
        isMuted: Boolean,
        isCameraOn: Boolean
    )
    suspend fun updateCallStatusById(conversationIdString: String, status: CallStatus)
    fun updateIsMutedById(conversationId: String, isMuted: Boolean)
    fun updateIsCameraOnById(conversationId: String, isCameraOn: Boolean)
    fun updateCallParticipants(conversationId: String, participants: List<Participant>)
    fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers)
    suspend fun getLastClosedCallCreatedByConversationId(conversationId: ConversationId): Flow<String?>
    suspend fun updateOpenCallsToClosedStatus()
    suspend fun persistMissedCall(conversationId: ConversationId)
    suspend fun joinMlsConference(
        conversationId: ConversationId,
        scope: CoroutineScope,
        onEpochChange: suspend (ConversationId, EpochInfo) -> Unit
    ): Either<CoreFailure, Unit>
    suspend fun leaveMlsConference(conversationId: ConversationId)
    suspend fun observeEpochInfo(conversationId: ConversationId): Either<CoreFailure, Flow<EpochInfo>>
    suspend fun advanceEpoch(conversationId: ConversationId)
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class CallDataSource(
    private val callApi: CallApi,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val persistMessage: PersistMessageUseCase,
    private val callDAO: CallDAO,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val joinSubconversationUseCase: JoinSubconversationUseCase,
    private val callMapper: CallMapper,
    private val federatedIdMapper: FederatedIdMapper,
    private val activeSpeakerMapper: ActiveSpeakerMapper = MapperProvider.activeSpeakerMapper()
) : CallRepository {

    private val _callMetadataProfile = MutableStateFlow(CallMetadataProfile(data = emptyMap()))

    private val callJobs = mutableMapOf<ConversationId, Job>()

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

    override suspend fun callsFlow(): Flow<List<Call>> = callDAO.observeCalls().combineWithCallsMetadata()

    override suspend fun incomingCallsFlow(): Flow<List<Call>> = callDAO.observeIncomingCalls().combineWithCallsMetadata()

    override suspend fun ongoingCallsFlow(): Flow<List<Call>> = callDAO.observeOngoingCalls().combineWithCallsMetadata()

    override suspend fun establishedCallsFlow(): Flow<List<Call>> = callDAO.observeEstablishedCalls().combineWithCallsMetadata()

    // TODO This method needs to be simplified and optimized
    @Suppress("LongMethod", "NestedBlockDepth")
    override suspend fun createCall(
        conversationId: ConversationId,
        status: CallStatus,
        callerId: String,
        isMuted: Boolean,
        isCameraOn: Boolean
    ) {
        val conversation: ConversationDetails =
            conversationRepository.observeConversationDetailsById(conversationId).onlyRight().first()

        // in OnIncomingCall we get callerId without a domain,
        // to cover that case and have a valid UserId we have that workaround
        val callerIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(callerId)
        val caller = userRepository.getKnownUser(callerIdWithDomain).first()

        val team = caller?.teamId?.let { teamId -> teamRepository.getTeam(teamId).first() }

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
            establishedTime = null,
            protocol = conversation.conversation.protocol
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
            "[CallRepository][createCall] -> lastCallStatus: [$lastCallStatus] |" +
                    " ConversationId: [${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}] " +
                    "| status: [$status]"
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
                    conversationIdString = conversationId.toString(),
                    status = callNewStatus
                )
            }

            if ((lastCallStatus !in activeCallStatus && isGroupCall) || isOneOnOneCall) {
                callingLogger.i(
                    "[CallRepository][createCall] -> Update.2 | lastCallStatus: [$lastCallStatus] " +
                            "| isGroupCall: [$isGroupCall] | isOneOnOneCall: [$isOneOnOneCall]"
                )

                // Save into database
                wrapStorageRequest {
                    callDAO.insertCall(call = callEntity)
                }
            }
        } else {
            callingLogger.i(
                "[CallRepository][createCall] -> else | lastCallStatus: [$lastCallStatus] | status: [$status]"
            )
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

    private fun updateCallMetadata(conversationId: ConversationId, metadata: CallMetadata) {
        val callMetadataProfile = _callMetadataProfile.value
        val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
            this[conversationId.toString()] = metadata
        }

        _callMetadataProfile.value = callMetadataProfile.copy(
            data = updatedCallMetadata
        )
    }

    override suspend fun updateCallStatusById(conversationIdString: String, status: CallStatus) {
        val callMetadataProfile = _callMetadataProfile.value
        val modifiedConversationId = qualifiedIdMapper.fromStringToQualifiedID(conversationIdString)

        // Update Call in Database
        wrapStorageRequest {
            callDAO.updateLastCallStatusByConversationId(
                status = callMapper.toCallEntityStatus(callStatus = status),
                conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                    conversationId = modifiedConversationId
                )
            )
            callingLogger.i(
                "[CallRepository][UpdateCallStatusById] ->" +
                        " ConversationId: [${modifiedConversationId.value.obfuscateId()}" +
                        "@${modifiedConversationId.domain.obfuscateDomain()}]" +
                        " " + "| status: [$status]"
            )
        }

        callMetadataProfile.data[modifiedConversationId.toString()]?.let { call ->
            val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
                val establishedTime = if (status == CallStatus.ESTABLISHED) DateTimeUtil.currentIsoDateTimeString()
                else call.establishedTime

                // Update Metadata
                this[modifiedConversationId.toString()] = call.copy(establishedTime = establishedTime)
            }

            _callMetadataProfile.value = callMetadataProfile.copy(
                data = updatedCallMetadata
            )
        }
    }

    override suspend fun persistMissedCall(conversationId: ConversationId) {
        callingLogger.i(
            "[CallRepository] -> Persisting Missed Call for conversation : conversationId: " +
                    "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}"
        )
        val qualifiedIDEntity = callMapper.fromConversationIdToQualifiedIDEntity(conversationId = conversationId)
        callDAO.getCallerIdByConversationId(conversationId = qualifiedIDEntity)?.let { callerId ->
            val qualifiedUserId = qualifiedIdMapper.fromStringToQualifiedID(callerId)

            val message = Message.System(
                uuid4().toString(),
                MessageContent.MissedCall,
                conversationId,
                DateTimeUtil.currentIsoDateTimeString(),
                qualifiedUserId,
                Message.Status.SENT,
                Message.Visibility.VISIBLE
            )
            persistMessage(message)
        } ?: callingLogger.i("[CallRepository] -> Unable to persist Missed Call due to missing Caller ID")
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
        val conversationIdToLog = qualifiedIdMapper.fromStringToQualifiedID(conversationId)
        callMetadataProfile.data[conversationId]?.let { call ->
            if (call.participants != participants) {
                callingLogger.i(
                    "updateCallParticipants() -" +
                            " conversationId: ${conversationIdToLog.value.obfuscateId()}@${conversationIdToLog.domain.obfuscateDomain()}" +
                            " with size of: ${participants.size}"
                )

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
    }

    override fun updateParticipantsActiveSpeaker(conversationId: String, activeSpeakers: CallActiveSpeakers) {
        val callMetadataProfile = _callMetadataProfile.value
        val conversationIdWithDomain = qualifiedIdMapper.fromStringToQualifiedID(conversationId)

        callMetadataProfile.data[conversationIdWithDomain.toString()]?.let { call ->
            callingLogger.i(
                "updateActiveSpeakers() -" +
                        " conversationId: ${conversationIdWithDomain.value.obfuscateId()}" +
                        "@${conversationIdWithDomain.domain.obfuscateDomain()}" +
                        "with size of: ${activeSpeakers.activeSpeakers.size}"
            )

            val updatedParticipants = activeSpeakerMapper.mapParticipantsActiveSpeaker(
                participants = call.participants,
                activeSpeakers = activeSpeakers
            )

            val updatedCallMetadata = callMetadataProfile.data.toMutableMap().apply {
                this[conversationIdWithDomain.toString()] = call.copy(
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

    override suspend fun updateOpenCallsToClosedStatus() {
        callDAO.updateOpenCallsToClosedStatus()
    }

    override suspend fun establishedCallConversationId(): ConversationId? =
        callDAO
            .observeEstablishedCalls()
            .combineWithCallsMetadata()
            .first()
            .firstOrNull()
            ?.conversationId

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

    override suspend fun joinMlsConference(
        conversationId: ConversationId,
        scope: CoroutineScope,
        onEpochChange: suspend (ConversationId, EpochInfo) -> Unit
    ) = joinSubconversationUseCase(conversationId, CALL_SUBCONVERSATION_ID).onSuccess {
        callJobs[conversationId] = scope.launch {
            observeEpochInfo(conversationId).onSuccess {
                it.collectLatest { epochInfo ->
                    onEpochChange(conversationId, epochInfo)
                }
            }
        }
    }

    override suspend fun leaveMlsConference(conversationId: ConversationId) {
        callingLogger.i(
            "Leaving MLS conference for conversation = " +
                    "${conversationId.value.obfuscateId()}@${conversationId.domain.obfuscateDomain()}"
        )

        // Cancels flow observing epoch changes
        callJobs.remove(conversationId)?.cancel()

        // TODO leaveSubconversationUseCase(conversationId, CALL_SUBCONVERSATION_ID)
    }

    private suspend fun createEpochInfo(parentGroupID: GroupID, subconversationGroupID: GroupID): Either<CoreFailure, EpochInfo> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapCryptoRequest {
                val epoch = mlsClient.conversationEpoch(subconversationGroupID.toCrypto())
                val secret = mlsClient.deriveSecret(subconversationGroupID.toCrypto(), 32u)
                val conversationMembers = mlsClient.members(parentGroupID.toCrypto())
                val subconversationMembers = mlsClient.members(subconversationGroupID.toCrypto())
                val callClients = conversationMembers.map {
                    CallClient(
                        federatedIdMapper.parseToFederatedId(UserId(it.userId.value, it.userId.domain)),
                        it.value,
                        subconversationMembers.contains(it)
                    )
                }

                val epochInfo = EpochInfo(
                    epoch,
                    CallClientList(callClients),
                    secret
                )
                epochInfo
            }
        }

    @OptIn(FlowPreview::class)
    override suspend fun observeEpochInfo(conversationId: ConversationId): Either<CoreFailure, Flow<EpochInfo>> =
        conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
            when (protocolInfo) {
                is Conversation.ProtocolInfo.MLS -> subconversationRepository.getSubconversationInfo(
                    conversationId,
                    CALL_SUBCONVERSATION_ID
                )?.let { subconversationGroupId ->
                    createEpochInfo(protocolInfo.groupId, subconversationGroupId).map { initialEpochInfo ->
                        flowOf(
                            flowOf(initialEpochInfo),
                            mlsConversationRepository.observeEpochChanges()
                                .filter { it == protocolInfo.groupId || it == subconversationGroupId }
                                .mapNotNull { createEpochInfo(protocolInfo.groupId, subconversationGroupId).getOrNull() }
                        ).flattenConcat()
                    }
                } ?: Either.Left(CoreFailure.NotSupportedByProteus)
                is Conversation.ProtocolInfo.Proteus -> Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }

    override suspend fun advanceEpoch(conversationId: ConversationId) {
        subconversationRepository.getSubconversationInfo(conversationId, CALL_SUBCONVERSATION_ID)?.let { groupId ->
            // Advance the epoch in the subconversation by updating the key material
            mlsConversationRepository.updateKeyingMaterial(groupId)
                .onSuccess { callingLogger.e("[CallRepository] -> Generated new epoch") }
                .onFailure { callingLogger.e("[CallRepository] -> Failure generating new epoch: $it") }
        } ?: callingLogger.w("[CallRepository] -> Requested new epoch but there's no conference subconversation")
    }
}
