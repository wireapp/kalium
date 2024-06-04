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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.MessageContent.MemberChange.FailedToAdd
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.sync.receiver.conversation.ConversationMessageTimerEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapNullableFlowStorageRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ServiceAddedResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isConversationHasNoCode
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.message.LocalId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConversationGroupRepository {
    suspend fun createGroupConversation(
        name: String? = null,
        usersList: List<UserId>,
        options: ConversationOptions = ConversationOptions(),
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
    private val memberJoinEventHandler: MemberJoinEventHandler,
    private val memberLeaveEventHandler: MemberLeaveEventHandler,
    private val conversationMessageTimerEventHandler: ConversationMessageTimerEventHandler,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val newConversationMembersRepository: NewConversationMembersRepository,
    private val userRepository: UserRepository,
    private val newGroupConversationSystemMessagesCreator: Lazy<NewGroupConversationSystemMessagesCreator>,
    private val selfUserId: UserId,
    private val teamIdProvider: SelfTeamIdProvider,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
) : ConversationGroupRepository {

    override suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: ConversationOptions,
    ): Either<CoreFailure, Conversation> = createGroupConversation(name, usersList, options, LastUsersAttempt.None)

    private suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: ConversationOptions,
        lastUsersAttempt: LastUsersAttempt,
    ): Either<CoreFailure, Conversation> =
        teamIdProvider().flatMap { selfTeamId ->
            val apiResult = wrapApiRequest {
                conversationApi.createNewConversation(
                    conversationMapper.toApiModel(name, usersList, selfTeamId?.value, options)
                )
            }

            when (apiResult) {
                is Either.Left -> {
                    val canRetryOnce = apiResult.value.isRetryable && lastUsersAttempt is LastUsersAttempt.None
                    if (canRetryOnce) {
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

                is Either.Right -> handleGroupConversationCreated(apiResult.value, selfTeamId, usersList, lastUsersAttempt)
            }
        }

    private suspend fun handleGroupConversationCreated(
        conversationResponse: ConversationResponse,
        selfTeamId: TeamId?,
        usersList: List<UserId>,
        lastUsersAttempt: LastUsersAttempt,
    ): Either<CoreFailure, Conversation> {
        val conversationEntity = conversationMapper.fromApiModelToDaoModel(
            conversationResponse, mlsGroupState = ConversationEntity.GroupState.PENDING_CREATION, selfTeamId
        )
        val protocol = protocolInfoMapper.fromEntity(conversationEntity.protocolInfo)

        return wrapStorageRequest {
            conversationDAO.insertConversation(conversationEntity)
        }.flatMap {
            newGroupConversationSystemMessagesCreator.value.conversationStarted(conversationEntity)
        }.flatMap {
            when (protocol) {
                is Conversation.ProtocolInfo.Proteus -> Either.Right(setOf())
                is Conversation.ProtocolInfo.MLSCapable -> mlsConversationRepository.establishMLSGroup(
                    groupID = protocol.groupId,
                    members = usersList + selfUserId,
                    allowSkippingUsersWithoutKeyPackages = true
                ).map { it.notAddedUsers }
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
        }.flatMap {
            wrapStorageRequest {
                newGroupConversationSystemMessagesCreator.value.conversationStartedUnverifiedWarning(
                    conversationEntity.id.toModel()
                )
            }
        }.flatMap {
            wrapStorageRequest {
                conversationDAO.getConversationByQualifiedID(conversationEntity.id)?.let {
                    conversationMapper.fromDaoModel(it)
                }
            }
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
                                mlsConversationRepository.addMemberToMLSGroup(GroupID(protocol.groupId), userIdList)
                            }

                    is ConversationEntity.ProtocolInfo.MLS -> {
                        tryAddMembersToMLSGroup(conversationId, protocol.groupId, userIdList, LastUsersAttempt.None)
                    }
                }
            }

    /**
     * Handle the error cases and retry for claimPackages offline and out of packages.
     * Handle error case and retry for sendingCommit unreachable or missing legal hold consent.
     */
    private suspend fun tryAddMembersToMLSGroup(
        conversationId: ConversationId,
        groupId: String,
        userIdList: List<UserId>,
        lastUsersAttempt: LastUsersAttempt,
        remainingAttempts: Int = 2
    ): Either<CoreFailure, Unit> {
        return when (val addingMemberResult = mlsConversationRepository.addMemberToMLSGroup(GroupID(groupId), userIdList)) {
            is Either.Right -> handleMLSMembersNotAdded(conversationId, lastUsersAttempt)
            is Either.Left -> {
                addingMemberResult.value.handleMLSMembersFailed(
                    conversationId = conversationId,
                    groupId = groupId,
                    userIdList = userIdList,
                    lastUsersAttempt = lastUsersAttempt,
                    remainingAttempts = remainingAttempts,
                )
            }
        }
    }

    private suspend fun CoreFailure.handleMLSMembersFailed(
        conversationId: ConversationId,
        groupId: String,
        userIdList: List<UserId>,
        lastUsersAttempt: LastUsersAttempt,
        remainingAttempts: Int,
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
                    remainingAttempts = remainingAttempts - 1
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
                    remainingAttempts = remainingAttempts - 1
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
                            remainingAttempts = remainingAttempts - 1
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
                                memberJoinEventHandler.handle(
                                    eventMapper.conversationMemberJoin(
                                        LocalId.generate(),
                                        response.event,
                                    )
                                )
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
        memberJoinEventHandler.handle(
            eventMapper.conversationMemberJoin(LocalId.generate(), apiResult.value.event)
        ).flatMap {
            if (lastUsersAttempt is LastUsersAttempt.Failed && lastUsersAttempt.failedUsers.isNotEmpty()) {
                newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                    conversationId, lastUsersAttempt.failedUsers, lastUsersAttempt.failType
                )
            }
            Either.Right(Unit)
        }
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
                    when (failedUsers.isNotEmpty()) {
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

                    is ConversationEntity.ProtocolInfo.Mixed ->
                        deleteMemberFromCloudAndStorage(userId, conversationId)
                            .flatMap { deleteMemberFromMlsGroup(userId, conversationId, protocol) }

                    is ConversationEntity.ProtocolInfo.MLS -> {
                        deleteMemberFromMlsGroup(userId, conversationId, protocol)
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

            memberJoinEventHandler.handle(eventMapper.conversationMemberJoin(LocalId.generate(), response.event))
                .flatMap {
                    wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
                        .flatMap { protocol ->
                            when (protocol) {
                                is ConversationEntity.ProtocolInfo.Proteus ->
                                    Either.Right(Unit)

                                is ConversationEntity.ProtocolInfo.MLSCapable -> {
                                    joinExistingMLSConversation(conversationId).flatMap {
                                        mlsConversationRepository.addMemberToMLSGroup(GroupID(protocol.groupId), listOf(selfUserId))
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
        userId: UserId,
        conversationId: ConversationId,
        protocol: ConversationEntity.ProtocolInfo.MLSCapable
    ) =
        if (userId == selfUserId) {
            deleteMemberFromCloudAndStorage(userId, conversationId).flatMap {
                mlsConversationRepository.leaveGroup(GroupID(protocol.groupId))
            }
        } else {
            // when removing a member from an MLS group, don't need to call the api
            mlsConversationRepository.removeMembersFromMLSGroup(GroupID(protocol.groupId), listOf(userId))
        }

    private suspend fun deleteMemberFromCloudAndStorage(userId: UserId, conversationId: ConversationId) =
        wrapApiRequest {
            conversationApi.removeMember(userId.toApi(), conversationId.toApi())
        }.onSuccess { response ->
            if (response is ConversationMemberRemovedResponse.Changed) {
                memberLeaveEventHandler.handle(
                    eventMapper.conversationMemberLeave(
                        LocalId.generate(),
                        response.event,
                    )
                )
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
                it.kaliumException.isConversationHasNoCode()
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
     * Filter the initial [userIdList] into valid and invalid users where valid users are only team members.
     */
    private suspend fun fetchAndExtractValidUsersForRetryableLegalHoldError(
        userIdList: List<UserId>
    ): Either<CoreFailure, ValidToInvalidUsers> =
        userRepository.fetchUsersLegalHoldConsent(userIdList.toSet()).map {
            ValidToInvalidUsers(it.usersWithConsent, it.usersWithoutConsent + it.usersFailed, FailedToAdd.Type.LegalHold)
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
