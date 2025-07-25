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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapNullableFlowStorageRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessageContent.MemberChange.FailedToAdd
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.local.LocalEventRepository
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ServiceAddedResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isConversationHasNoCode
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.LocalId
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Mockable
interface ConversationGroupRepository {
    suspend fun createGroupConversation(
        name: String? = null,
        usersList: List<UserId>,
        options: CreateConversationParam = CreateConversationParam(),
    ): Either<CoreFailure, Conversation>

    suspend fun addMembers(userIdList: List<UserId>, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun addService(serviceId: ServiceId, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun deleteMember(userId: UserId, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun joinViaInviteCode(
        code: String,
        key: String,
        uri: String?,
        password: String?
    ): Either<NetworkFailure, ConversationMemberAddedResponse>

    suspend fun fetchLimitedInfoViaInviteCode(code: String, key: String): Either<NetworkFailure, ConversationCodeInfo>
    suspend fun generateGuestRoomLink(
        conversationId: ConversationId,
        password: String?
    ): Either<NetworkFailure, EventContentDTO.Conversation.CodeUpdated>

    suspend fun revokeGuestRoomLink(conversationId: ConversationId): Either<NetworkFailure, Unit>
    suspend fun observeGuestRoomLink(conversationId: ConversationId): Flow<Either<CoreFailure, ConversationGuestLink?>>
    suspend fun updateMessageTimer(conversationId: ConversationId, messageTimer: Long?): Either<CoreFailure, Unit>
    suspend fun updateGuestRoomLink(conversationId: ConversationId, accountUrl: String): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConversationGroupRepositoryImpl(
    private val mlsConversationRepository: MLSConversationRepository,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    private val localEventRepository: LocalEventRepository,
    private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val newConversationMembersRepository: NewConversationMembersRepository,
    private val userRepository: UserRepository,
    private val newGroupConversationSystemMessagesCreator: Lazy<NewGroupConversationSystemMessagesCreator>,
    private val selfUserId: UserId,
    private val teamIdProvider: SelfTeamIdProvider,
    private val legalHoldHandler: LegalHoldHandler,
    private val transactionProvider: CryptoTransactionProvider,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
) : ConversationGroupRepository {

    override suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: CreateConversationParam,
    ): Either<CoreFailure, Conversation> = createGroupConversation(name, usersList, options, LastUsersAttempt.None)

    private suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: CreateConversationParam,
        lastUsersAttempt: LastUsersAttempt,
    ): Either<CoreFailure, Conversation> =
        teamIdProvider().flatMap { selfTeamId ->
            val apiResult = wrapApiRequest {
                conversationApi.createNewConversation(
                    conversationMapper.toApiModel(name, usersList, selfTeamId?.value, options)
                )
            }

            when (apiResult) {
                is Either.Left -> handleCreateConversationFailure(
                    apiResult = apiResult,
                    usersList = usersList,
                    name = name,
                    options = options,
                    lastUsersAttempt = lastUsersAttempt
                )

                is Either.Right -> handleGroupConversationCreated(
                    conversationResponse = apiResult.value,
                    selfTeamId = selfTeamId,
                    usersList = usersList,
                    lastUsersAttempt = lastUsersAttempt
                )
            }
        }

    @Suppress("LongMethod")
    private suspend fun handleGroupConversationCreated(
        conversationResponse: ConversationResponse,
        selfTeamId: TeamId?,
        usersList: List<UserId>,
        lastUsersAttempt: LastUsersAttempt,
    ): Either<CoreFailure, Conversation> {
        val conversationEntity = conversationMapper.fromApiModelToDaoModel(
            apiModel = conversationResponse,
            mlsGroupState = ConversationEntity.GroupState.PENDING_CREATION,
            selfUserTeamId = selfTeamId,
        )
        val mlsPublicKeys = conversationMapper.fromApiModel(conversationResponse.publicKeys)
        val protocol = protocolInfoMapper.fromEntity(conversationEntity.protocolInfo)

        return wrapStorageRequest {
            conversationDAO.insertConversation(conversationEntity)
        }.flatMap {
            newGroupConversationSystemMessagesCreator.value.conversationStartedUnverifiedWarning(conversationEntity.id.toModel())
        }.flatMap {
            newGroupConversationSystemMessagesCreator.value.conversationStarted(conversationEntity)
        }.flatMap {
            when (protocol) {
                is Conversation.ProtocolInfo.Proteus -> Either.Right(setOf())
                is Conversation.ProtocolInfo.MLSCapable ->
                    transactionProvider.mlsTransaction("handleGroupConversationCreated") { mlsContext ->
                        mlsConversationRepository.establishMLSGroup(
                            mlsContext = mlsContext,
                            groupID = protocol.groupId,
                            members = usersList + selfUserId,
                            publicKeys = mlsPublicKeys,
                            allowSkippingUsersWithoutKeyPackages = true,
                        ).map { it.notAddedUsers }
                    }
            }
        }.flatMap { protocolSpecificAdditionFailures ->
            newConversationMembersRepository.persistMembersAdditionToTheConversation(
                conversationEntity.id, conversationResponse
            ).flatMap {
                if (protocolSpecificAdditionFailures.isEmpty()) {
                    Either.Right(Unit)
                } else {
                    newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                        conversationId = conversationEntity.id.toModel(),
                        userIdList = protocolSpecificAdditionFailures.toList(),
                        type = FailedToAdd.Type.Federation
                    )
                }
            }.flatMap {
                when (lastUsersAttempt) {
                    is LastUsersAttempt.None -> Either.Right(Unit)
                    is LastUsersAttempt.Failed ->
                        newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                            conversationEntity.id.toModel(), lastUsersAttempt.failedUsers, lastUsersAttempt.failType
                        )
                }
            }
        }.onSuccess {
            legalHoldHandler.handleConversationMembersChanged(conversationEntity.id.toModel())
        }.flatMap {
            wrapStorageRequest {
                conversationDAO.getConversationById(conversationEntity.id)?.let {
                    conversationMapper.fromDaoModel(it)
                }
            }
        }
    }

    private suspend fun handleCreateConversationFailure(
        apiResult: Either.Left<NetworkFailure>,
        usersList: List<UserId>,
        name: String?,
        options: CreateConversationParam,
        lastUsersAttempt: LastUsersAttempt
    ): Either<CoreFailure, Conversation> {
        val canRetryOnce = apiResult.value.isRetryable
                && lastUsersAttempt is LastUsersAttempt.None
                && apiResult.value !is NetworkFailure.FederatedBackendFailure.ConflictingBackends
        // For conflicting backends the app needs to show the info to the user right away so that he/she can react and adjust selection,
        // so for this particular federation failure type it shouldn't attempt to retry automatically with extracting only valid users.

        return if (canRetryOnce) {
            extractValidUsersForRetryableError(apiResult.value, usersList)
                .flatMap { (validUsers, failedUsers, failType) ->
                    // edge case, in case backend goes 🍌 and returns non-matching domains
                    if (failedUsers.isEmpty()) Either.Left(apiResult.value)

                    createGroupConversation(name, validUsers, options, LastUsersAttempt.Failed(failedUsers, failType))
                }
        } else {
            Either.Left(apiResult.value)
        }
    }

    override suspend fun addMembers(
        userIdList: List<UserId>,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
            .flatMap { protocol ->
                when (protocol) {
                    is ConversationEntity.ProtocolInfo.Proteus ->
                        tryAddMembersToCloudAndStorage(userIdList, conversationId, LastUsersAttempt.None)

                    is ConversationEntity.ProtocolInfo.Mixed ->
                        tryAddMembersToCloudAndStorage(userIdList, conversationId, LastUsersAttempt.None)
                            .flatMap {
                                // best effort approach for migrated conversations, no retries
                                transactionProvider.mlsTransaction("addMembers") { mlsContext ->
                                    mlsConversationRepository.addMemberToMLSGroup(
                                        mlsContext = mlsContext,
                                        GroupID(protocol.groupId),
                                        userIdList,
                                        CipherSuite.fromTag(protocol.cipherSuite.cipherSuiteTag)
                                    )
                                }
                            }

                    is ConversationEntity.ProtocolInfo.MLS -> {
                        tryAddMembersToMLSGroup(
                            conversationId,
                            protocol.groupId,
                            userIdList,
                            LastUsersAttempt.None,
                            cipherSuite = CipherSuite.fromTag(protocol.cipherSuite.cipherSuiteTag)
                        )
                    }
                }
            }

    /**
     * Handle the error cases and retry for claimPackages offline and out of packages.
     * Handle error case and retry for sendingCommit unreachable or missing legal hold consent.
     */
    @Suppress("LongMethod")
    private suspend fun tryAddMembersToMLSGroup(
        conversationId: ConversationId,
        groupId: String,
        userIdList: List<UserId>,
        lastUsersAttempt: LastUsersAttempt,
        cipherSuite: CipherSuite,
        remainingAttempts: Int = 2
    ): Either<CoreFailure, Unit> {

        val addingMemberResult = transactionProvider.mlsTransaction("tryAddMembersToMLSGroup") { mlsContext ->
            mlsConversationRepository.addMemberToMLSGroup(
                mlsContext = mlsContext,
                GroupID(groupId),
                userIdList,
                cipherSuite
            )
        }
        return when (addingMemberResult) {
            is Either.Right -> handleMLSMembersNotAdded(conversationId, lastUsersAttempt)
            is Either.Left -> {
                addingMemberResult.value.handleMLSMembersFailed(
                    conversationId = conversationId,
                    groupId = groupId,
                    userIdList = userIdList,
                    lastUsersAttempt = lastUsersAttempt,
                    remainingAttempts = remainingAttempts,
                    cipherSuite = cipherSuite
                )
            }
        }
    }

    @Suppress("LongMethod")
    private suspend fun CoreFailure.handleMLSMembersFailed(
        conversationId: ConversationId,
        groupId: String,
        userIdList: List<UserId>,
        lastUsersAttempt: LastUsersAttempt,
        remainingAttempts: Int,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, Unit> {
        return when {
            // claiming key packages offline or out of packages
            this is CoreFailure.MissingKeyPackages && remainingAttempts > 0 -> {
                val (validUsers, failedUsers) = userIdList.partition { !this.failedUserIds.contains(it) }
                tryAddMembersToMLSGroup(
                    conversationId = conversationId,
                    groupId = groupId,
                    userIdList = validUsers,
                    lastUsersAttempt = LastUsersAttempt.Failed(
                        failedUsers = lastUsersAttempt.failedUsers + failedUsers,
                        failType = FailedToAdd.Type.Federation,
                    ),
                    remainingAttempts = remainingAttempts - 1,
                    cipherSuite = cipherSuite
                )
            }

            // sending commit unreachable
            this is NetworkFailure.FederatedBackendFailure.RetryableFailure && remainingAttempts > 0 -> {
                val (validUsers, failedUsers) = extractValidUsersForRetryableFederationError(userIdList, this)
                tryAddMembersToMLSGroup(
                    conversationId = conversationId,
                    groupId = groupId,
                    userIdList = validUsers,
                    lastUsersAttempt = LastUsersAttempt.Failed(
                        failedUsers = lastUsersAttempt.failedUsers + failedUsers,
                        failType = FailedToAdd.Type.Federation,
                    ),
                    remainingAttempts = remainingAttempts - 1,
                    cipherSuite = cipherSuite
                )
            }

            // missing legal hold consent
            this.isMissingLegalHoldConsentError && remainingAttempts > 0 -> {
                fetchAndExtractValidUsersForRetryableLegalHoldError(userIdList)
                    .flatMap { (validUsers, failedUsers) ->
                        tryAddMembersToMLSGroup(
                            conversationId = conversationId,
                            groupId = groupId,
                            userIdList = validUsers,
                            lastUsersAttempt = LastUsersAttempt.Failed(
                                failedUsers = lastUsersAttempt.failedUsers + failedUsers,
                                failType = FailedToAdd.Type.LegalHold,
                            ),
                            remainingAttempts = remainingAttempts - 1,
                            cipherSuite = cipherSuite
                        )
                    }
            }

            else -> {
                newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                    conversationId = conversationId,
                    userIdList = (lastUsersAttempt.failedUsers + userIdList),
                    type = when {
                        this.isMissingLegalHoldConsentError -> FailedToAdd.Type.LegalHold
                        else -> FailedToAdd.Type.Federation
                    }
                ).flatMap {
                    Either.Left(this)
                }
            }
        }
    }

    private suspend fun handleMLSMembersNotAdded(
        conversationId: ConversationId,
        lastUsersAttempt: LastUsersAttempt,
    ): Either<CoreFailure, Unit> =
        when (lastUsersAttempt) {
            is LastUsersAttempt.Failed -> newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                conversationId, lastUsersAttempt.failedUsers, lastUsersAttempt.failType
            )

            is LastUsersAttempt.None -> Either.Right(Unit)
        }

    override suspend fun addService(serviceId: ServiceId, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
            .flatMap { protocol ->
                when (protocol) {
                    is ConversationEntity.ProtocolInfo.Proteus, is ConversationEntity.ProtocolInfo.Mixed -> {
                        wrapApiRequest {
                            conversationApi.addService(
                                AddServiceRequest(id = serviceId.id, provider = serviceId.provider),
                                conversationId.toApi()
                            )
                        }.onSuccess { response ->
                            if (response is ServiceAddedResponse.Changed) {
                                val event = eventMapper.fromEventContentDTO(
                                    LocalId.generate(),
                                    response.event
                                )
                                localEventRepository.emitLocalEvent(event)
                            }
                        }.map { Unit }
                    }

                    is ConversationEntity.ProtocolInfo.MLS -> {
                        val failure = MLSFailure.Generic(
                            UnsupportedOperationException("Adding service to MLS conversation is not supported")
                        )
                        Either.Left(failure)
                    }
                }
            }

    private suspend fun tryAddMembersToCloudAndStorage(
        userIdList: List<UserId>,
        conversationId: ConversationId,
        lastUsersAttempt: LastUsersAttempt,
    ): Either<CoreFailure, Unit> {
        val apiResult = wrapApiRequest {
            val users = userIdList.map { it.toApi() }
            val addParticipantRequest = AddConversationMembersRequest(users, ConversationDataSource.DEFAULT_MEMBER_ROLE)
            conversationApi.addMember(addParticipantRequest, conversationId.toApi())
        }

        return when (apiResult) {
            is Either.Left -> handleAddingMembersFailure(apiResult, lastUsersAttempt, userIdList, conversationId)
            is Either.Right -> handleAddingMembersSuccess(apiResult, lastUsersAttempt, conversationId)
        }
    }

    private suspend fun handleAddingMembersSuccess(
        apiResult: Either.Right<ConversationMemberAddedResponse>,
        lastUsersAttempt: LastUsersAttempt,
        conversationId: ConversationId
    ) = if (apiResult.value is ConversationMemberAddedResponse.Changed) {
        val event = eventMapper.fromEventContentDTO(
            LocalId.generate(),
            (apiResult.value as ConversationMemberAddedResponse.Changed).event
        )
        localEventRepository.emitLocalEvent(event)

        if (lastUsersAttempt is LastUsersAttempt.Failed && lastUsersAttempt.failedUsers.isNotEmpty()) {
            newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                conversationId, lastUsersAttempt.failedUsers, lastUsersAttempt.failType
            )
        }
        Either.Right(Unit)
    } else {
        Either.Right(Unit)
    }

    private suspend fun handleAddingMembersFailure(
        apiResult: Either.Left<NetworkFailure>,
        lastUsersAttempt: LastUsersAttempt,
        userIdList: List<UserId>,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> {
        val canRetryOnce = apiResult.value.isRetryable && lastUsersAttempt is LastUsersAttempt.None
        return if (canRetryOnce) {
            extractValidUsersForRetryableError(apiResult.value, userIdList)
                .flatMap { (validUsers, failedUsers, failType) ->
                    when (failedUsers.isNotEmpty() && validUsers.isNotEmpty()) {
                        true -> tryAddMembersToCloudAndStorage(validUsers, conversationId, LastUsersAttempt.Failed(failedUsers, failType))
                        false -> {
                            newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                                conversationId, (validUsers + failedUsers), failType
                            ).flatMap {
                                Either.Left(apiResult.value)
                            }
                        }
                    }
                }
        } else {
            val failType = apiResult.value.toFailedToAddType()
            newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                conversationId, userIdList + lastUsersAttempt.failedUsers, failType
            ).flatMap {
                Either.Left(apiResult.value)
            }
        }
    }

    override suspend fun deleteMember(
        userId: UserId,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
            .flatMap { protocol ->
                when (protocol) {
                    is ConversationEntity.ProtocolInfo.Proteus ->
                        deleteMemberFromCloudAndStorage(userId, conversationId)

                    is ConversationEntity.ProtocolInfo.MLSCapable ->
                        transactionProvider.transaction("deleteMember") { transactionContext ->
                            deleteMemberFromMlsGroup(transactionContext, userId, conversationId, protocol)
                        }
                }
            }

    override suspend fun joinViaInviteCode(
        code: String,
        key: String,
        uri: String?,
        password: String?
    ): Either<NetworkFailure, ConversationMemberAddedResponse> = wrapApiRequest {
        conversationApi.joinConversation(code, key, uri, password)
    }.onSuccess { response ->
        if (response is ConversationMemberAddedResponse.Changed) {
            val conversationId = response.event.qualifiedConversation.toModel()
            val event = eventMapper.fromEventContentDTO(
                LocalId.generate(),
                response.event
            )
            localEventRepository.emitLocalEvent(event)

            wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
                .flatMap { protocol ->
                    when (protocol) {
                        is ConversationEntity.ProtocolInfo.Proteus ->
                            Either.Right(Unit)

                        is ConversationEntity.ProtocolInfo.MLSCapable -> {
                            transactionProvider.transaction("joinViaInviteCode") { transactionContext ->
                                joinExistingMLSConversation(transactionContext, conversationId).flatMap {
                                    transactionContext.wrapInMLSContext { mlsContext ->
                                        mlsConversationRepository.addMemberToMLSGroup(
                                            mlsContext,
                                            GroupID(protocol.groupId),
                                            listOf(selfUserId),
                                            CipherSuite.fromTag(protocol.cipherSuite.cipherSuiteTag)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }
    }

    override suspend fun fetchLimitedInfoViaInviteCode(
        code: String,
        key: String
    ): Either<NetworkFailure, ConversationCodeInfo> =
        wrapApiRequest { conversationApi.fetchLimitedInformationViaCode(code, key) }

    private suspend fun deleteMemberFromMlsGroup(
        transactionContext: CryptoTransactionContext,
        userId: UserId,
        conversationId: ConversationId,
        protocol: ConversationEntity.ProtocolInfo.MLSCapable
    ) = when (protocol) {
        is ConversationEntity.ProtocolInfo.MLS -> {
            transactionContext.wrapInMLSContext { mlsContext ->
                if (userId == selfUserId) {
                    deleteMemberFromCloudAndStorage(userId, conversationId).flatMap {
                        mlsConversationRepository.leaveGroup(mlsContext, GroupID(protocol.groupId))

                    }
                } else {
                    // when removing a member from an MLS group, don't need to call the api
                    mlsConversationRepository.removeMembersFromMLSGroup(mlsContext, GroupID(protocol.groupId), listOf(userId))
                }
            }
        }

        is ConversationEntity.ProtocolInfo.Mixed -> {
            deleteMemberFromCloudAndStorage(userId, conversationId).flatMap {
                transactionContext.wrapInMLSContext { mlsContext ->
                    mlsConversationRepository.removeMembersFromMLSGroup(mlsContext, GroupID(protocol.groupId), listOf(userId))
                }
            }
        }
    }

    private suspend fun deleteMemberFromCloudAndStorage(userId: UserId, conversationId: ConversationId) =
        wrapApiRequest {
            conversationApi.removeMember(userId.toApi(), conversationId.toApi())
        }.onSuccess { response ->
            if (response is ConversationMemberRemovedResponse.Changed) {
                val event = eventMapper.fromEventContentDTO(
                    LocalId.generate(),
                    response.event
                )
                localEventRepository.emitLocalEvent(event)
            }
        }.map { }

    override suspend fun generateGuestRoomLink(
        conversationId: ConversationId,
        password: String?
    ): Either<NetworkFailure, EventContentDTO.Conversation.CodeUpdated> =
        wrapApiRequest {
            conversationApi.generateGuestRoomLink(conversationId.toApi(), password)
        }

    override suspend fun revokeGuestRoomLink(conversationId: ConversationId): Either<NetworkFailure, Unit> =
        wrapApiRequest {
            conversationApi.revokeGuestRoomLink(conversationId.toApi())
        }.onSuccess {
            wrapStorageRequest {
                conversationDAO.deleteGuestRoomLink(conversationId.toDao())
            }
        }

    override suspend fun observeGuestRoomLink(conversationId: ConversationId): Flow<Either<CoreFailure, ConversationGuestLink?>> =
        wrapNullableFlowStorageRequest {
            conversationDAO.observeGuestRoomLinkByConversationId(conversationId.toDao())
                .map { it?.let { ConversationGuestLink(it.link, it.isPasswordProtected) } }
        }

    override suspend fun updateMessageTimer(
        conversationId: ConversationId,
        messageTimer: Long?
    ): Either<CoreFailure, Unit> =
        wrapApiRequest { conversationApi.updateMessageTimer(conversationId.toApi(), messageTimer) }
            .onSuccess {
                conversationMessageTimerEventHandler.handle(
                    eventMapper.conversationMessageTimerUpdate(
                        LocalId.generate(),
                        it,
                    )
                )
            }
            .map { }

    override suspend fun updateGuestRoomLink(conversationId: ConversationId, accountUrl: String): Either<CoreFailure, Unit> =
        wrapApiRequest {
            conversationApi.guestLinkInfo(conversationId.toApi())
        }.fold({
            if (it is NetworkFailure.ServerMiscommunication &&
                it.kaliumException is KaliumException.InvalidRequestError &&
                (it.kaliumException as KaliumException.InvalidRequestError).isConversationHasNoCode()
            ) {
                wrapStorageRequest {
                    conversationDAO.deleteGuestRoomLink(conversationId.toDao())
                }
            } else {
                Either.Left(it)
            }
        }, {
            wrapStorageRequest {
                conversationDAO.updateGuestRoomLink(conversationId.toDao(), it.link(accountUrl), it.hasPassword)
            }
        })

    /**
     * Extract valid and invalid users lists from the given userIdList and a [FailedToAdd.Type] depending on a given [CoreFailure].
     * If the given [CoreFailure] is not retryable, the original userIdList is returned as valid users, invalid users list is empty
     * and the type is [FailedToAdd.Type.Unknown].
     */
    private suspend fun extractValidUsersForRetryableError(
        failure: CoreFailure,
        userIdList: List<UserId>,
    ): Either<CoreFailure, ValidToInvalidUsers> = when {
        failure is NetworkFailure.FederatedBackendFailure.RetryableFailure ->
            Either.Right(extractValidUsersForRetryableFederationError(userIdList, failure))

        failure.isMissingLegalHoldConsentError ->
            fetchAndExtractValidUsersForRetryableLegalHoldError(userIdList)

        else ->
            Either.Right(ValidToInvalidUsers(userIdList, emptyList(), FailedToAdd.Type.Unknown))
    }

    private fun CoreFailure.toFailedToAddType() = when {
        this is NetworkFailure.FederatedBackendFailure -> FailedToAdd.Type.Federation
        this.isMissingLegalHoldConsentError -> FailedToAdd.Type.LegalHold
        else -> FailedToAdd.Type.Unknown
    }

    /**
     * Filter the initial [userIdList] into valid and invalid users where valid users are only team members and people with consent.
     */
    private suspend fun fetchAndExtractValidUsersForRetryableLegalHoldError(
        userIdList: List<UserId>
    ): Either<CoreFailure, ValidToInvalidUsers> =
        teamIdProvider().flatMap { selfTeamId ->
            userRepository.fetchUsersLegalHoldConsent(userIdList.toSet()).map {
                it.usersWithConsent
                    .partition { (_, teamId) -> teamId == selfTeamId }
                    .let { (validUsers, membersFromOtherTeam) ->
                        ValidToInvalidUsers(
                            validUsers = validUsers.map { (userId, _) -> userId },
                            failedUsers = membersFromOtherTeam.map { (userId, _) -> userId } + it.usersWithoutConsent + it.usersFailed,
                            failType = FailedToAdd.Type.LegalHold
                        )
                    }
            }
        }

    /**
     * Extract from a [NetworkFailure.FederatedBackendFailure.RetryableFailure] the domains
     * and filter the initial [userIdList] into valid and invalid users.
     */
    private fun extractValidUsersForRetryableFederationError(
        userIdList: List<UserId>,
        federatedDomainFailure: NetworkFailure.FederatedBackendFailure.RetryableFailure
    ): ValidToInvalidUsers {
        val (validUsers, failedUsers) = userIdList.partition { !federatedDomainFailure.domains.contains(it.domain) }
        return ValidToInvalidUsers(validUsers, failedUsers, FailedToAdd.Type.Federation)
    }

    private data class ValidToInvalidUsers(val validUsers: List<UserId>, val failedUsers: List<UserId>, val failType: FailedToAdd.Type)

    private sealed class LastUsersAttempt {
        open val failedUsers: List<UserId> = emptyList()

        data object None : LastUsersAttempt()
        data class Failed(override val failedUsers: List<UserId>, val failType: FailedToAdd.Type) : LastUsersAttempt()
    }
}
