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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.service.ServiceId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
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
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ServiceAddedResponse
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
        failedUsersList: List<UserId> = emptyList()
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
    private val newGroupConversationSystemMessagesCreator: Lazy<NewGroupConversationSystemMessagesCreator>,
    private val selfUserId: UserId,
    private val teamIdProvider: SelfTeamIdProvider,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val eventMapper: EventMapper = MapperProvider.eventMapper(),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
) : ConversationGroupRepository {

    override suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: ConversationOptions,
        failedUsersList: List<UserId>
    ): Either<CoreFailure, Conversation> =
        teamIdProvider().flatMap { selfTeamId ->
            val apiResult = wrapApiRequest {
                conversationApi.createNewConversation(
                    conversationMapper.toApiModel(name, usersList, selfTeamId?.value, options)
                )
            }

            when (apiResult) {
                is Either.Left -> {
                    val canRetryOnce = apiResult.value.hasUnreachableDomainsError && failedUsersList.isEmpty()
                    if (canRetryOnce) {
                        val (validUsers, failedUsers) = extractValidUsersForRetryableFederationError(
                            usersList,
                            apiResult.value as NetworkFailure.FederatedBackendFailure.FailedDomains
                        )
                        // edge case, in case backend goes 🍌 and returns non-matching domains
                        if (failedUsers.isEmpty()) Either.Left(apiResult.value)

                        createGroupConversation(name, validUsers, options, failedUsers)
                    } else {
                        Either.Left(apiResult.value)
                    }
                }

                is Either.Right -> {
                    val conversationResponse = apiResult.value
                    val conversationEntity = conversationMapper.fromApiModelToDaoModel(
                        conversationResponse, mlsGroupState = ConversationEntity.GroupState.PENDING_CREATION, selfTeamId
                    )
                    val protocol = protocolInfoMapper.fromEntity(conversationEntity.protocolInfo)

                    wrapStorageRequest {
                        conversationDAO.insertConversation(conversationEntity)
                    }.flatMap {
                        newGroupConversationSystemMessagesCreator.value.conversationStarted(conversationEntity)
                    }.flatMap {
                        newConversationMembersRepository.persistMembersAdditionToTheConversation(
                            conversationEntity.id, conversationResponse, failedUsersList
                        ).flatMap {
                            when (protocol) {
                                is Conversation.ProtocolInfo.Proteus -> Either.Right(Unit)
                                is Conversation.ProtocolInfo.MLS -> mlsConversationRepository.establishMLSGroup(
                                    groupID = protocol.groupId,
                                    members = usersList + selfUserId
                                )
                            }
                        }
                    }.flatMap {
                        wrapStorageRequest {
                            conversationDAO.getConversationByQualifiedID(conversationEntity.id)?.let {
                                conversationMapper.fromDaoModel(it)
                            }
                        }
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
                        tryAddMembersToCloudAndStorage(userIdList, conversationId)

                    is ConversationEntity.ProtocolInfo.MLS -> {
                        mlsConversationRepository.addMemberToMLSGroup(GroupID(protocol.groupId), userIdList)
                    }
                }
            }

    override suspend fun addService(serviceId: ServiceId, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
            .flatMap { protocol ->
                when (protocol) {
                    is ConversationEntity.ProtocolInfo.Proteus -> {
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
                                        true
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
        failedUsersList: Set<UserId> = emptySet(),
    ): Either<CoreFailure, Unit> {
        val apiResult = wrapApiRequest {
            val users = userIdList.map { it.toApi() }
            val addParticipantRequest = AddConversationMembersRequest(users, ConversationDataSource.DEFAULT_MEMBER_ROLE)
            conversationApi.addMember(addParticipantRequest, conversationId.toApi())
        }

        return when (apiResult) {
            is Either.Left -> handleAddingMembersFailure(apiResult, failedUsersList, userIdList, conversationId)
            is Either.Right -> handleAddingMembersSuccess(apiResult, failedUsersList, conversationId)
        }
    }

    private suspend fun handleAddingMembersSuccess(
        apiResult: Either.Right<ConversationMemberAddedResponse>,
        failedUsersList: Set<UserId>,
        conversationId: ConversationId
    ) = if (apiResult.value is ConversationMemberAddedResponse.Changed) {
        memberJoinEventHandler.handle(
            eventMapper.conversationMemberJoin(LocalId.generate(), apiResult.value.event, true)
        ).flatMap {
            if (failedUsersList.isNotEmpty()) {
                newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(conversationId, failedUsersList)
            }
            Either.Right(Unit)
        }
    } else {
        Either.Right(Unit)
    }

    private suspend fun handleAddingMembersFailure(
        apiResult: Either.Left<NetworkFailure>,
        failedUsersList: Set<UserId>,
        userIdList: List<UserId>,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> {
        val canRetryOnce = apiResult.value.isRetryable && failedUsersList.isEmpty()
        return if (canRetryOnce) {
            val (validUsers, failedUsers) = extractValidUsersForRetryableFederationError(
                userIdList,
                apiResult.value as NetworkFailure.FederatedBackendFailure.RetryableFailure
            )
            when (failedUsers.isNotEmpty()) {
                true -> tryAddMembersToCloudAndStorage(validUsers, conversationId, failedUsers.toSet())
                false -> {
                    newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                        conversationId,
                        (validUsers + failedUsers).toSet()
                    ).flatMap {
                        Either.Left(apiResult.value)
                    }
                }
            }
        } else {
            newGroupConversationSystemMessagesCreator.value.conversationFailedToAddMembers(
                conversationId,
                failedUsersList + userIdList
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

                    is ConversationEntity.ProtocolInfo.MLS -> {
                        if (userId == selfUserId) {
                            deleteMemberFromCloudAndStorage(userId, conversationId).flatMap {
                                mlsConversationRepository.leaveGroup(GroupID(protocol.groupId))
                            }
                        } else {
                            // when removing a member from an MLS group, don't need to call the api
                            mlsConversationRepository.removeMembersFromMLSGroup(
                                GroupID(protocol.groupId),
                                listOf(userId)
                            )
                        }
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

            memberJoinEventHandler.handle(eventMapper.conversationMemberJoin(LocalId.generate(), response.event, true))
                .flatMap {
                    wrapStorageRequest { conversationDAO.getConversationProtocolInfo(conversationId.toDao()) }
                        .flatMap {
                            when (it) {
                                is ConversationEntity.ProtocolInfo.Proteus ->
                                    Either.Right(Unit)

                                is ConversationEntity.ProtocolInfo.MLS -> {
                                    joinExistingMLSConversation(conversationId).flatMap {
                                        addMembers(listOf(selfUserId), conversationId)
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

    private suspend fun deleteMemberFromCloudAndStorage(userId: UserId, conversationId: ConversationId) =
        wrapApiRequest {
            conversationApi.removeMember(userId.toApi(), conversationId.toApi())
        }.onSuccess { response ->
            if (response is ConversationMemberRemovedResponse.Changed) {
                memberLeaveEventHandler.handle(
                    eventMapper.conversationMemberLeave(
                        LocalId.generate(),
                        response.event,
                        false
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
                conversationDAO.updateGuestRoomLink(conversationId.toDao(), null, false)
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
                        true
                    )
                )
            }
            .map { }

    /**
     * Extract from a [NetworkFailure.FederatedBackendFailure.RetryableFailure] the domains
     * and filter the initial [userIdList] into valid and invalid users.
     */
    private fun extractValidUsersForRetryableFederationError(
        userIdList: List<UserId>,
        federatedDomainFailure: NetworkFailure.FederatedBackendFailure.RetryableFailure
    ): ValidToInvalidUsers {
        val (validUsers, failedUsers) = userIdList.partition { !federatedDomainFailure.domains.contains(it.domain) }
        return ValidToInvalidUsers(validUsers, failedUsers)
    }

    private data class ValidToInvalidUsers(val validUsers: List<UserId>, val failedUsers: List<UserId>)
}
