/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import co.touchlab.stately.collections.ConcurrentMutableMap
import kotlin.uuid.Uuid
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.onlyRight
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.callingLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.obfuscateDomain
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.call.mapper.CallMapper
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.EpochChangesObserver
import com.wire.kalium.logic.data.conversation.JoinSubconversationUseCase
import com.wire.kalium.logic.data.conversation.LeaveSubconversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.FederatedIdMapper
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.QualifiedIdMapper
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.network.api.base.authenticated.CallApi
import com.wire.kalium.network.api.base.authenticated.ServerTimeApi
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.call.CallDAO
import com.wire.kalium.persistence.dao.call.CallEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.max
import kotlin.time.toDuration

internal val CALL_SUBCONVERSATION_ID = SubconversationId("conference")

@Suppress("TooManyFunctions")
@Mockable
interface CallRepository {
    suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String>
    suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray>
    fun getCallMetadata(conversationId: ConversationId): CallMetadata?
    suspend fun callsFlow(): Flow<List<Call>>
    suspend fun incomingCallsFlow(): Flow<List<Call>>
    suspend fun outgoingCallsFlow(): Flow<List<Call>>
    suspend fun ongoingCallsFlow(): Flow<List<Call>>
    suspend fun establishedCallsFlow(): Flow<List<Call>>
    suspend fun establishedCallConversationId(): ConversationId?
    fun observeLastActiveCallByConversationId(conversationId: ConversationId): Flow<Call?>

    @Suppress("LongParameterList")
    suspend fun createCall(
        conversationId: ConversationId,
        type: ConversationTypeForCall,
        status: CallStatus,
        callerId: UserId,
        isMuted: Boolean,
        isCameraOn: Boolean,
        isCbrEnabled: Boolean
    )

    suspend fun updateCallStatusById(conversationId: ConversationId, status: CallStatus)
    fun updateIsMutedById(conversationId: ConversationId, isMuted: Boolean)
    fun updateIsCbrEnabled(isCbrEnabled: Boolean)
    fun updateIsCameraOnById(conversationId: ConversationId, isCameraOn: Boolean)
    suspend fun updateCallParticipants(conversationId: ConversationId, participants: List<ParticipantMinimized>)
    fun updateParticipantsActiveSpeaker(conversationId: ConversationId, activeSpeakers: Map<UserId, List<String>>)
    suspend fun getLastClosedCallCreatedByConversationId(conversationId: ConversationId): Flow<String?>
    suspend fun updateOpenCallsToClosedStatus()
    suspend fun leavePreviouslyJoinedMlsConferences()
    suspend fun persistMissedCall(conversationId: ConversationId)
    suspend fun joinMlsConference(
        conversationId: ConversationId,
        onJoined: suspend () -> Unit,
        onEpochChange: suspend (ConversationId, EpochInfo) -> Unit
    ): Either<CoreFailure, Unit>

    suspend fun leaveMlsConference(conversationId: ConversationId)
    suspend fun observeEpochInfo(conversationId: ConversationId): Either<CoreFailure, Flow<EpochInfo>>
    suspend fun advanceEpoch(conversationId: ConversationId)
    fun currentCallProtocol(conversationId: ConversationId): Conversation.ProtocolInfo?

    suspend fun updateRecentlyEndedCallMetadata(recentlyEndedCallMetadata: RecentlyEndedCallMetadata)
    suspend fun observeRecentlyEndedCallMetadata(): Flow<RecentlyEndedCallMetadata>
    suspend fun fetchServerTime(): String?
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class CallDataSource(
    private val callApi: CallApi,
    private val serverTimeApi: ServerTimeApi,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val persistMessage: PersistMessageUseCase,
    private val callDAO: CallDAO,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val epochChangesObserver: EpochChangesObserver,
    private val subconversationRepository: SubconversationRepository,
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val joinSubconversation: JoinSubconversationUseCase,
    private val leaveSubconversation: LeaveSubconversationUseCase,
    private val transactionProvider: CryptoTransactionProvider,
    private val callMapper: CallMapper,
    private val federatedIdMapper: FederatedIdMapper,
    kaliumDispatchers: KaliumDispatcher = KaliumDispatcherImpl,
    initialCallMetadataProfile: CallMetadataProfile = CallMetadataProfile(), // For testing purposes
) : CallRepository {

    private var _callMetadataProfile: MutableStateFlow<CallMetadataProfile> = MutableStateFlow(initialCallMetadataProfile)

    private val job = SupervisorJob() // TODO(calling): clear job method
    private val scope = CoroutineScope(job + kaliumDispatchers.io)
    private val callJobs = ConcurrentMutableMap<ConversationId, Job>()
    private val staleParticipantJobs = ConcurrentMutableMap<QualifiedClientID, Job>()
    private val _recentlyEndedCallFlow = MutableSharedFlow<RecentlyEndedCallMetadata>(
        extraBufferCapacity = 1
    )

    override suspend fun updateRecentlyEndedCallMetadata(recentlyEndedCallMetadata: RecentlyEndedCallMetadata) {
        _recentlyEndedCallFlow.emit(recentlyEndedCallMetadata)
    }

    override suspend fun observeRecentlyEndedCallMetadata(): Flow<RecentlyEndedCallMetadata> {
        return _recentlyEndedCallFlow
    }

    override suspend fun getCallConfigResponse(limit: Int?): Either<CoreFailure, String> = wrapApiRequest {
        callApi.getCallConfig(limit = limit)
    }

    override suspend fun connectToSFT(url: String, data: String): Either<CoreFailure, ByteArray> = wrapApiRequest {
        callApi.connectToSFT(url = url, data = data)
    }

    override fun getCallMetadata(conversationId: ConversationId): CallMetadata? = _callMetadataProfile[conversationId]

    override suspend fun callsFlow(): Flow<List<Call>> = callDAO.observeCalls().combineWithCallsMetadata()

    override suspend fun incomingCallsFlow(): Flow<List<Call>> = callDAO.observeIncomingCalls().combineWithCallsMetadata()
    override suspend fun outgoingCallsFlow(): Flow<List<Call>> = callDAO.observeOutgoingCalls().combineWithCallsMetadata()

    override suspend fun ongoingCallsFlow(): Flow<List<Call>> = callDAO.observeOngoingCalls().combineWithCallsMetadata()

    override suspend fun establishedCallsFlow(): Flow<List<Call>> = callDAO.observeEstablishedCalls().combineWithCallsMetadata()

    // TODO This method needs to be simplified and optimized
    @Suppress("LongMethod", "NestedBlockDepth")
    override suspend fun createCall(
        conversationId: ConversationId,
        type: ConversationTypeForCall,
        status: CallStatus,
        callerId: UserId,
        isMuted: Boolean,
        isCameraOn: Boolean,
        isCbrEnabled: Boolean
    ) {
        val conversation: ConversationDetails =
            conversationRepository.observeConversationDetailsById(conversationId).onlyRight().first()

        val caller = userRepository.getKnownUser(callerId).first()
        val team = caller?.teamId?.let { teamId -> teamRepository.getTeam(teamId).first() }

        val callEntity = callMapper.toCallEntity(
            conversationId = conversationId,
            id = Uuid.random().toString(),
            type = type,
            status = status,
            conversationType = conversation.conversation.type,
            callerId = callerId
        )

        val metadata = CallMetadata(
            callerId = callerId,
            conversationName = conversation.conversation.name,
            conversationType = conversation.conversation.type,
            callerName = caller?.name,
            callerTeamName = team?.name,
            isMuted = isMuted,
            isCameraOn = isCameraOn,
            isCbrEnabled = isCbrEnabled,
            establishedTime = null,
            callStatus = status,
            protocol = conversation.conversation.protocol,
            activeSpeakers = mapOf()
        )

        val isCallInCurrentSession = _callMetadataProfile.value.containsKey(conversationId)
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
                    " ConversationId: [${conversationId.toLogString()}] " +
                    "| status: [$status]"
        )
        if (status == CallStatus.INCOMING && !isCallInCurrentSession) {
            _callMetadataProfile.update { callMetadataProfile ->
                callMetadataProfile.plus(conversationId = conversationId, metadata = metadata)
            }
            val callNewStatus = if (isGroupCall) CallStatus.STILL_ONGOING else CallStatus.CLOSED
            if (lastCallStatus in activeCallStatus) { // LAST CALL ACTIVE
                callingLogger.i("[CallRepository][createCall] -> Update.1 | callNewStatus: [$callNewStatus]")
                // Update database
                updateCallStatusById(
                    conversationId = conversationId,
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
                _callMetadataProfile.update { callMetadataProfile ->
                    callMetadataProfile.plus(conversationId = conversationId, metadata = metadata)
                }
            }
        }
    }

    override suspend fun updateCallStatusById(conversationId: ConversationId, status: CallStatus) {
            // Update Call in Database
            wrapStorageRequest {
                callDAO.updateLastCallStatusByConversationId(
                    status = callMapper.toCallEntityStatus(callStatus = status),
                    conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                        conversationId = conversationId
                    )
                )
                callingLogger.i(
                    "[CallRepository][UpdateCallStatusById] ->" +
                            " ConversationId: [${conversationId.value.obfuscateId()}" +
                            "@${conversationId.domain.obfuscateDomain()}]" +
                            " " + "| status: [$status]"
                )
            }

        _callMetadataProfile.update(conversationId) { callMetadata ->
            callMetadata.copy(
                callStatus = status,
                establishedTime = when (status) {
                    CallStatus.ESTABLISHED -> DateTimeUtil.currentIsoDateTimeString()
                    else -> callMetadata.establishedTime
                },
            )
        }
    }

    override suspend fun persistMissedCall(conversationId: ConversationId) {
        callingLogger.i(
            "[CallRepository] -> Persisting Missed Call for conversation : conversationId: " +
                    conversationId.toLogString()
        )
        val qualifiedIDEntity = callMapper.fromConversationIdToQualifiedIDEntity(conversationId = conversationId)
        callDAO.getCallerIdByConversationId(conversationId = qualifiedIDEntity)?.let { callerId ->
            val qualifiedUserId = qualifiedIdMapper.fromStringToQualifiedID(callerId)

            val message = Message.System(
                Uuid.random().toString(),
                MessageContent.MissedCall,
                conversationId,
                Clock.System.now(),
                qualifiedUserId,
                Message.Status.Sent,
                Message.Visibility.VISIBLE,
                expirationData = null,
            )
            persistMessage(message)
        } ?: callingLogger.i("[CallRepository] -> Unable to persist Missed Call due to missing Caller ID")
    }

    override fun updateIsMutedById(conversationId: ConversationId, isMuted: Boolean) {
        _callMetadataProfile.update(conversationId) { callMetadata ->
            callMetadata.copy(isMuted = isMuted)
        }
    }

    override fun updateIsCbrEnabled(isCbrEnabled: Boolean) {
        val conversationId = callDAO.getEstablishedCall().conversationId.toModel()
        _callMetadataProfile.update(conversationId) { callMetadata ->
            callMetadata.copy(isCbrEnabled = isCbrEnabled)
        }
    }

    override fun updateIsCameraOnById(conversationId: ConversationId, isCameraOn: Boolean) {
        _callMetadataProfile.update(conversationId) { callMetadata ->
            callMetadata.copy(isCameraOn = isCameraOn)
        }
    }

    @Suppress("NestedBlockDepth")
    override suspend fun updateCallParticipants(conversationId: ConversationId, participants: List<ParticipantMinimized>) {
        _callMetadataProfile.update(conversationId) { callMetadata ->
            if (callMetadata.participants != participants) {
                callingLogger.i(
                    "updateCallParticipants() -" +
                            " conversationId: ${conversationId.toLogString()}" +
                            " with size of: ${participants.size}"
                )

                val currentParticipantIds = callMetadata.participants.map { it.userId }.toSet()
                val newParticipantIds = participants.map { it.userId }.toSet()
                val sharingScreenParticipantIds = participants.filter { it.isSharingScreen }
                    .map { participant -> participant.id }

                val updatedUsers = callMetadata.users.toMutableList()

                newParticipantIds.minus(currentParticipantIds).let { missedUserIds ->
                    if (missedUserIds.isNotEmpty())
                        updatedUsers.addAll(
                            userRepository.getUsersMinimizedByQualifiedIDs(missedUserIds.toList()).getOrElse { listOf() }
                        )
                }

                callMetadata.copy(
                    participants = participants,
                    maxParticipants = max(callMetadata.maxParticipants, participants.size),
                    users = updatedUsers,
                    screenShareMetadata = updateScreenSharingMetadata(
                        metadata = callMetadata.screenShareMetadata,
                        usersCurrentlySharingScreen = sharingScreenParticipantIds
                    )
                )
            } else {
                callMetadata
            }
        }?.let { callMetadata ->
            if (callMetadata.protocol is Conversation.ProtocolInfo.MLS && callMetadata.conversationType is Conversation.Type.Group) {
                transactionProvider.transaction("removeStaleParticipantAfterTimeout") { transactionContext ->
                    participants.forEach { participant ->
                        if (participant.hasEstablishedAudio) {
                            clearStaleParticipantTimeout(participant)
                        } else {
                            removeStaleParticipantAfterTimeout(transactionContext, participant, conversationId)
                        }
                    }
                    Unit.right()
                }
            }
        }
    }

    /**
     * Manages call sharing metadata for analytical purposes by tracking the following:
     * - **Active Screen Shares**: Maintains a record of currently active screen shares with their start times (local to the device).
     * - **Completed Screen Share Duration**: Accumulates the total duration of screen shares that have already ended.
     * - **Unique Sharing Users**: Keeps a unique list of all users who have shared their screen during the call.
     *
     * To update the metadata, the following steps are performed:
     * 1. **Calculate Ended Screen Share Time**: Determine the total time for users who stopped sharing since the last update.
     * 2. **Update Active Shares**: Filter out inactive shares and add any new ones, associating them with the current start time.
     * 3. **Track Unique Users**: Append ids to current set in order to keep track of unique users.
     */
    private fun updateScreenSharingMetadata(
        metadata: CallScreenSharingMetadata,
        usersCurrentlySharingScreen: List<QualifiedID>
    ): CallScreenSharingMetadata {
        val now = DateTimeUtil.currentInstant()

        val alreadyEndedScreenSharesTimeInMillis = metadata.activeScreenShares
            .filterKeys { id -> id !in usersCurrentlySharingScreen }
            .values
            .sumOf { startTime -> DateTimeUtil.calculateMillisDifference(startTime, now) }

        val updatedShares = metadata.activeScreenShares
            .filterKeys { id -> id in usersCurrentlySharingScreen }
            .plus(
                usersCurrentlySharingScreen
                    .filterNot { id -> metadata.activeScreenShares.containsKey(id) }
                    .associateWith { now }
            )

        return metadata.copy(
            activeScreenShares = updatedShares,
            completedScreenShareDurationInMillis = metadata.completedScreenShareDurationInMillis + alreadyEndedScreenSharesTimeInMillis,
            uniqueSharingUsers = metadata.uniqueSharingUsers.plus(usersCurrentlySharingScreen.map { id -> id.toString() })
        )
    }

    private fun clearStaleParticipantTimeout(participant: ParticipantMinimized) {
        callingLogger.i("Clear stale participant timer")
        val qualifiedClient = QualifiedClientID(ClientId(participant.clientId), participant.id)
        staleParticipantJobs.remove(qualifiedClient)?.cancel()
    }

    private fun removeStaleParticipantAfterTimeout(
        transactionContext: CryptoTransactionContext,
        participant: ParticipantMinimized,
        conversationId: ConversationId
    ) {
        val qualifiedClient = QualifiedClientID(ClientId(participant.clientId), participant.id)
        if (staleParticipantJobs.containsKey(qualifiedClient)) {
            return
        }

        staleParticipantJobs[qualifiedClient] = scope.launch {
            callingLogger.i("Start stale participant timer")
            delay(STALE_PARTICIPANT_TIMEOUT)
            callingLogger.i("Removing stale participant")
            subconversationRepository.getSubconversationInfo(conversationId, CALL_SUBCONVERSATION_ID)?.let { groupId ->
                transactionContext.wrapInMLSContext { mlsContext ->
                    mlsConversationRepository.removeClientsFromMLSGroup(
                        mlsContext,
                        groupId,
                        listOf(qualifiedClient)
                    )
                }
            }
        }
    }

    override fun updateParticipantsActiveSpeaker(conversationId: ConversationId, activeSpeakers: Map<UserId, List<String>>) {
        _callMetadataProfile.update(conversationId) { callMetadata ->
            callingLogger.i(
                "updateActiveSpeakers() -" +
                        " conversationId: ${conversationId.value.obfuscateId()}" +
                        "@${conversationId.domain.obfuscateDomain()}" +
                        "with size of: ${activeSpeakers.size}"
            )

            callMetadata.copy(activeSpeakers = activeSpeakers)
        }
    }

    override suspend fun getLastClosedCallCreatedByConversationId(conversationId: ConversationId): Flow<String?> =
        callDAO.getLastClosedCallByConversationId(
            conversationId = callMapper.fromConversationIdToQualifiedIDEntity(
                conversationId = conversationId
            )
        )

    override suspend fun updateOpenCallsToClosedStatus() {
        leavePreviouslyJoinedMlsConferences()
        callDAO.updateOpenCallsToClosedStatus()
    }

    override suspend fun establishedCallConversationId(): ConversationId? =
        callDAO
            .observeEstablishedCalls()
            .combineWithCallsMetadata()
            .first()
            .firstOrNull()
            ?.conversationId

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<List<CallEntity>>.combineWithCallsMetadata(): Flow<List<Call>> =
        this.combine(_callMetadataProfile) { calls, callMetadataProfile ->
            calls.map { callEntity ->
                callMetadataProfile[callEntity.conversationId.toModel()] // get flow of metadata for each call
                    ?.map { callMetadata -> callEntity to callMetadata } // associate each CallEntity with CallMetadata
                    ?: flowOf(callEntity to null)
            }
        }.flatMapLatest { listOfCallDataFlows ->
            if (listOfCallDataFlows.isEmpty()) { // If there are no calls, return an empty flow as combine does not work with empty lists
                flowOf(emptyList())
            } else {
                // Combine all flows for each call with metadata into a single flow with list of calls with metadata
                combine(listOfCallDataFlows) { flowOfCallDataArray: Array<Pair<CallEntity, CallMetadata?>> ->
                    flowOfCallDataArray.toList()
                        .map { (callEntity, callMetadata) ->
                            callMapper.toCall(callEntity = callEntity, metadata = callMetadata)
                        }
                }
            }
        }

    private fun Flow<CallEntity?>.combineWithCallMetadata(): Flow<Call?> =
        this.map { listOfNotNull(it) } // create a list to re-use combineWithCallsMetadata
            .combineWithCallsMetadata()
            .map { it.firstOrNull() } // get the call from the list or null if there is no call

    override suspend fun leavePreviouslyJoinedMlsConferences() {
        callingLogger.i("Leaving previously joined MLS conferences")

        callDAO.observeEstablishedCalls()
            .first()
            .filter { it.type == CallEntity.Type.MLS_CONFERENCE }
            .forEach {
                leaveMlsConference(it.conversationId.toModel())
            }
    }

    override suspend fun joinMlsConference(
        conversationId: ConversationId,
        onJoined: suspend () -> Unit,
        onEpochChange: suspend (ConversationId, EpochInfo) -> Unit
    ): Either<CoreFailure, Unit> {
        callingLogger.i(
            "Joining MLS conference for conversation = " +
                    conversationId.toLogString()
        )

        return joinSubconversation(conversationId, CALL_SUBCONVERSATION_ID).onSuccess {
            onJoined()
            callJobs[conversationId] = scope.launch {
                observeEpochInfo(conversationId).onSuccess {
                    it.collectLatest { epochInfo ->
                        callingLogger.logStructuredJson(
                            level = KaliumLogLevel.DEBUG,
                            leadingMessage = "[CallRepository] Received epoch change",
                            jsonStringKeyValues = mapOf(
                                "conversationId" to conversationId.toLogString(),
                                "epoch" to epochInfo.epoch.toString()
                            )
                        )
                        onEpochChange(conversationId, epochInfo)
                    }
                }
            }
        }
    }

    override suspend fun leaveMlsConference(conversationId: ConversationId) {
        callingLogger.i(
            "Leaving MLS conference for conversation = " +
                    conversationId.toLogString()
        )

        // Cancels flow observing epoch changes
        callJobs.remove(conversationId)?.cancel()

        // Cancel all jobs for removing stale participants
        staleParticipantJobs.block { map ->
            val jobsSnapshot = map.values.toList()
            jobsSnapshot.forEach { it.cancel() }
            map.clear()
        }

        transactionProvider.mlsTransaction("leaveSubconversation") { mlsContext ->
            leaveSubconversation(mlsContext, conversationId, CALL_SUBCONVERSATION_ID)
        }
            .onSuccess {
                callingLogger.i("Successfully left MLS conference")
            }
            .onFailure {
                callingLogger.e("Failed to leave MLS conference: $it")
            }
    }

    private suspend fun createEpochInfo(
        mlsContext: MlsCoreCryptoContext,
        parentGroupID: GroupID,
        subconversationGroupID: GroupID
    ): Either<CoreFailure, EpochInfo> =
        wrapMLSRequest {
            val epoch = mlsContext.conversationEpoch(subconversationGroupID.toCrypto())
            val secret = mlsContext.deriveSecret(subconversationGroupID.toCrypto(), 32u)
            val conversationMembers = mlsContext.members(parentGroupID.toCrypto())
            val subconversationMembers = mlsContext.members(subconversationGroupID.toCrypto())
            val callClients = conversationMembers.map {
                CallClient(
                    federatedIdMapper.parseToFederatedId(UserId(it.userId.value, it.userId.domain)),
                    it.value,
                    subconversationMembers.contains(it)
                )
            }

            callingLogger.logStructuredJson(
                level = KaliumLogLevel.DEBUG,
                leadingMessage = "[CallRepository] Created epoch info",
                jsonStringKeyValues = mapOf(
                    "groupId" to parentGroupID.toLogString(),
                    "subConversationGroupID" to subconversationGroupID.toLogString(),
                    "epoch" to epoch
                )
            )

            val epochInfo = EpochInfo(
                epoch,
                CallClientList(callClients),
                secret
            )
            epochInfo
        }

    override suspend fun observeEpochInfo(conversationId: ConversationId): Either<CoreFailure, Flow<EpochInfo>> =
        conversationRepository.getConversationProtocolInfo(conversationId).flatMap { protocolInfo ->
            when (protocolInfo) {
                is Conversation.ProtocolInfo.MLS -> subconversationRepository.getSubconversationInfo(
                    conversationId,
                    CALL_SUBCONVERSATION_ID
                )?.let { subconversationGroupId ->
                    transactionProvider.mlsTransaction("createEpochInfo") { mlsContext ->
                        createEpochInfo(mlsContext, protocolInfo.groupId, subconversationGroupId)
                    }.map { initialEpochInfo ->
                        flowOf(
                            flowOf(initialEpochInfo),
                            epochChangesObserver.observe()
                                .filter { it.groupId == protocolInfo.groupId || it.groupId == subconversationGroupId }
                                .mapNotNull {
                                    transactionProvider.mlsTransaction { transactionContext ->
                                        createEpochInfo(transactionContext, protocolInfo.groupId, subconversationGroupId)
                                    }.getOrNull()
                                }
                        ).flattenConcat()
                    }
                } ?: Either.Left(CoreFailure.NotSupportedByProteus)

                is Conversation.ProtocolInfo.Proteus,
                is Conversation.ProtocolInfo.Mixed -> Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }

    override suspend fun advanceEpoch(conversationId: ConversationId) {
        subconversationRepository.getSubconversationInfo(conversationId, CALL_SUBCONVERSATION_ID)?.let { groupId ->
            // Advance the epoch in the subconversation by updating the key material
            transactionProvider.mlsTransaction("advanceEpoch") {
                mlsConversationRepository.updateKeyingMaterial(it, groupId)
            }
                .onSuccess { callingLogger.e("[CallRepository] -> Generated new epoch") }
                .onFailure { callingLogger.e("[CallRepository] -> Failure generating new epoch: $it") }
        } ?: callingLogger.w("[CallRepository] -> Requested new epoch but there's no conference subconversation")
    }

    override fun currentCallProtocol(conversationId: ConversationId): Conversation.ProtocolInfo? =
        _callMetadataProfile[conversationId]?.protocol

    override suspend fun fetchServerTime(): String? {
        val result = serverTimeApi.getServerTime()
        return if (result.isSuccessful()) {
            result.value.time
        } else {
            null
        }
    }

    override fun observeLastActiveCallByConversationId(conversationId: ConversationId): Flow<Call?> =
        callDAO.observeLastActiveCallByConversationId(callMapper.fromConversationIdToQualifiedIDEntity(conversationId))
            .combineWithCallMetadata()

    companion object {
        val STALE_PARTICIPANT_TIMEOUT = 190.toDuration(kotlin.time.DurationUnit.SECONDS)
    }
}

private inline operator fun MutableStateFlow<CallMetadataProfile>.get(conversationId: ConversationId) = value[conversationId]?.value

private inline fun MutableStateFlow<CallMetadataProfile>.update(conversationId: ConversationId, function: (CallMetadata) -> CallMetadata) =
    value[conversationId]?.updateAndGet(function)
