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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.sync.receiver.conversation.MemberJoinEventHandler
import com.wire.kalium.logic.sync.receiver.conversation.MemberLeaveEventHandler
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.message.LocalId
import kotlinx.coroutines.flow.Flow

interface ConversationGroupRepository {
    suspend fun createGroupConversation(
        name: String? = null,
        usersList: List<UserId>,
        options: ConversationOptions = ConversationOptions()
    ): Either<CoreFailure, Conversation>

    suspend fun addMembers(userIdList: List<UserId>, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun deleteMember(userId: UserId, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun joinViaInviteCode(code: String, key: String, uri: String?): Either<CoreFailure, ConversationMemberAddedResponse>
    suspend fun fetchLimitedInfoViaInviteCode(code: String, key: String): Either<NetworkFailure, LimitedConversationInfo>
    suspend fun generateGuestRoomLink(conversationId: ConversationId): Either<NetworkFailure, Unit>
    suspend fun revokeGuestRoomLink(conversationId: ConversationId): Either<NetworkFailure, Unit>
    suspend fun observeGuestRoomLink(conversationId: ConversationId): Flow<String?>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConversationGroupRepositoryImpl(
    private val mlsConversationRepository: MLSConversationRepository,
    private val memberJoinEventHandler: MemberJoinEventHandler,
    private val memberLeaveEventHandler: MemberLeaveEventHandler,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val selfUserId: UserId,
    private val teamIdProvider: SelfTeamIdProvider,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val eventMapper: EventMapper = MapperProvider.eventMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
) : ConversationGroupRepository {

    override suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: ConversationOptions
    ): Either<CoreFailure, Conversation> =
        teamIdProvider().flatMap { selfTeamId ->
            wrapApiRequest {
                conversationApi.createNewConversation(
                    conversationMapper.toApiModel(name, usersList, selfTeamId?.value, options)
                )
            }
                .flatMap { conversationResponse ->
                    val conversationEntity = conversationMapper.fromApiModelToDaoModel(
                        conversationResponse, mlsGroupState = ConversationEntity.GroupState.PENDING_CREATION, selfTeamId
                    )
                    val protocol = protocolInfoMapper.fromEntity(conversationEntity.protocolInfo)

                    wrapStorageRequest {
                        conversationDAO.insertConversation(conversationEntity)
                    }.flatMap {
                        when (protocol) {
                            is Conversation.ProtocolInfo.Proteus ->
                                persistMembersFromConversationResponse(conversationResponse)

                            is Conversation.ProtocolInfo.MLS ->
                                persistMembersFromConversationResponse(conversationResponse)
                                    .flatMap { mlsConversationRepository.establishMLSGroup(protocol.groupId, usersList + selfUserId) }
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

    private suspend fun persistMembersFromConversationResponse(
        conversationResponse: ConversationResponse
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationId = idMapper.fromApiToDao(conversationResponse.id)
            conversationDAO.insertMembersWithQualifiedId(
                memberMapper.fromApiModelToDaoModel(conversationResponse.members),
                conversationId
            )
        }
    }

    override suspend fun addMembers(
        userIdList: List<UserId>,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        conversationDAO.getConversationByQualifiedID(conversationId.toDao())?.let { conversationEntity ->
            val conversation = conversationMapper.fromDaoModel(conversationEntity)

            when (conversation.protocol) {
                is Conversation.ProtocolInfo.Proteus ->
                    addMembersToCloudAndStorage(userIdList, conversationId)

                is Conversation.ProtocolInfo.MLS -> {
                    mlsConversationRepository.addMemberToMLSGroup(conversation.protocol.groupId, userIdList)
                }
            }
        } ?: Either.Left(StorageFailure.DataNotFound)

    private suspend fun addMembersToCloudAndStorage(userIdList: List<UserId>, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapApiRequest {
            val users = userIdList.map { it.toApi() }
            val addParticipantRequest = AddConversationMembersRequest(users, ConversationDataSource.DEFAULT_MEMBER_ROLE)
            conversationApi.addMember(
                addParticipantRequest, conversationId.toApi()
            )
        }.onSuccess { response ->
            if (response is ConversationMemberAddedResponse.Changed) {
                memberJoinEventHandler.handle(eventMapper.conversationMemberJoin(LocalId.generate(), response.event, true))
            }
        }.map {
            Either.Right(Unit)
        }

    override suspend fun deleteMember(
        userId: UserId,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        conversationDAO.getConversationByQualifiedID(conversationId.toDao())?.let { conversationEntity ->
            val conversation = conversationMapper.fromDaoModel(conversationEntity)

            when (conversation.protocol) {
                is Conversation.ProtocolInfo.Proteus ->
                    deleteMemberFromCloudAndStorage(userId, conversationId)

                is Conversation.ProtocolInfo.MLS -> {
                    if (userId == selfUserId) {
                        deleteMemberFromCloudAndStorage(userId, conversationId).flatMap {
                            mlsConversationRepository.leaveGroup(conversation.protocol.groupId)
                        }
                    } else {
                        // when removing a member from an MLS group, don't need to call the api
                        mlsConversationRepository.removeMembersFromMLSGroup(conversation.protocol.groupId, listOf(userId))
                    }
                }
            }
        } ?: Either.Left(StorageFailure.DataNotFound)

    override suspend fun joinViaInviteCode(
        code: String,
        key: String,
        uri: String?
    ): Either<CoreFailure, ConversationMemberAddedResponse> = wrapApiRequest {
        conversationApi.joinConversation(code, key, uri)
    }.onSuccess { response ->
        if (response is ConversationMemberAddedResponse.Changed) {
            memberJoinEventHandler.handle(eventMapper.conversationMemberJoin(LocalId.generate(), response.event, true))
        }
    }

    override suspend fun fetchLimitedInfoViaInviteCode(code: String, key: String): Either<NetworkFailure, LimitedConversationInfo> =
        wrapApiRequest { conversationApi.fetchLimitedInformationViaCode(code, key) }

    private suspend fun deleteMemberFromCloudAndStorage(userId: UserId, conversationId: ConversationId) =
        wrapApiRequest {
            conversationApi.removeMember(userId.toApi(), conversationId.toApi())
        }.onSuccess { response ->
            if (response is ConversationMemberRemovedResponse.Changed) {
                memberLeaveEventHandler.handle(eventMapper.conversationMemberLeave(LocalId.generate(), response.event, false))
            }
        }.map { }

    override suspend fun generateGuestRoomLink(conversationId: ConversationId): Either<NetworkFailure, Unit> =
        wrapApiRequest {
            conversationApi.generateGuestRoomLink(conversationId.toApi())
        }.onSuccess {
            it.data?.let { data -> conversationDAO.updateGuestRoomLink(conversationId.toDao(), data.uri) }
            it.uri?.let { link -> conversationDAO.updateGuestRoomLink(conversationId.toDao(), link) }
        }.map { Either.Right(Unit) }

    override suspend fun revokeGuestRoomLink(conversationId: ConversationId): Either<NetworkFailure, Unit> =
        wrapApiRequest {
            conversationApi.revokeGuestRoomLink(conversationId.toApi())
        }.onSuccess {
            conversationDAO.updateGuestRoomLink(conversationId.toDao(), null)
        }.map { }

    override suspend fun observeGuestRoomLink(conversationId: ConversationId): Flow<String?> =
        conversationDAO.observeGuestRoomLinkByConversationId(conversationId.toDao())
}
