package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock

interface ConversationGroupRepository {
    suspend fun requestToJoinMLSGroup(conversation: Conversation): Either<CoreFailure, Unit>
    suspend fun createGroupConversation(
        name: String? = null,
        usersList: List<UserId>,
        options: ConversationOptions = ConversationOptions()
    ): Either<CoreFailure, Conversation>

    suspend fun addMembers(userIdList: List<UserId>, conversationId: ConversationId): Either<CoreFailure, MemberChangeResult>
    suspend fun deleteMember(userId: UserId, conversationId: ConversationId): Either<CoreFailure, MemberChangeResult>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConversationGroupRepositoryImpl(
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val selfUserId: UserId,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
) : ConversationGroupRepository {

    override suspend fun requestToJoinMLSGroup(conversation: Conversation): Either<CoreFailure, Unit> {
        return if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
            mlsConversationRepository.requestToJoinGroup(
                conversation.protocol.groupId,
                conversation.protocol.epoch
            )
        } else {
            Either.Right(Unit)
        }
    }
    override suspend fun createGroupConversation(
        name: String?,
        usersList: List<UserId>,
        options: ConversationOptions
    ): Either<CoreFailure, Conversation> = wrapStorageRequest {
        userRepository.observeSelfUser().first()
    }.flatMap { selfUser ->
        wrapApiRequest {
            conversationApi.createNewConversation(
                conversationMapper.toApiModel(name, usersList, selfUser.teamId?.value, options)
            )
        }
            .flatMap { conversationResponse ->
                val teamId = selfUser.teamId
                val conversationEntity = conversationMapper.fromApiModelToDaoModel(
                    conversationResponse, mlsGroupState = ConversationEntity.GroupState.PENDING_CREATION, teamId
                )
                val protocol = protocolInfoMapper.fromEntity(conversationEntity.protocolInfo)

                wrapStorageRequest {
                    conversationDAO.insertConversation(conversationEntity)
                }.flatMap {
                    when (protocol) {
                        is Conversation.ProtocolInfo.Proteus -> persistMembersFromConversationResponse(conversationResponse)
                        is Conversation.ProtocolInfo.MLS -> persistMembersFromConversationResponseMLS(
                            conversationResponse, usersList
                        )
                    }
                }.flatMap {
                    when (protocol) {
                        is Conversation.ProtocolInfo.Proteus -> Either.Right(Unit)
                        is Conversation.ProtocolInfo.MLS ->
                            mlsConversationRepository
                                .establishMLSGroup(protocol.groupId)
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

    override suspend fun addMembers(
        userIdList: List<UserId>,
        conversationId: ConversationId
    ): Either<CoreFailure, MemberChangeResult> =
        conversationRepository.detailsById(conversationId).flatMap { conversation ->
            when (conversation.protocol) {
                is Conversation.ProtocolInfo.Proteus ->
                    addMembersToCloudAndStorage(userIdList, conversationId)

                is Conversation.ProtocolInfo.MLS -> {
                    mlsConversationRepository.addMemberToMLSGroup(conversation.protocol.groupId, userIdList)
                        .map { MemberChangeResult.Changed(Clock.System.now().toString()) }
                }
            }
        }

    private suspend fun addMembersToCloudAndStorage(userIdList: List<UserId>, conversationId: ConversationId) =
        wrapApiRequest {
            val users = userIdList.map {
                idMapper.toApiModel(it)
            }
            val addParticipantRequest = AddConversationMembersRequest(users, ConversationDataSource.DEFAULT_MEMBER_ROLE)
            conversationApi.addMember(
                addParticipantRequest, idMapper.toApiModel(conversationId)
            )
        }.flatMap { response ->
            val memberList = userIdList.map { userId ->
                // TODO: mapping the user id list to members with a made up role is incorrect and a recipe for disaster
                Conversation.Member(userId, Conversation.Member.Role.Member)
            }

            conversationRepository.persistMembers(memberList, conversationId).map {
                when (response) {
                    is ConversationMemberAddedDTO.Changed -> MemberChangeResult.Changed(response.time)
                    ConversationMemberAddedDTO.Unchanged -> MemberChangeResult.Unchanged
                }
            }
        }

    override suspend fun deleteMember(
        userId: UserId,
        conversationId: ConversationId
    ): Either<CoreFailure, MemberChangeResult> =
        conversationRepository.detailsById(conversationId).flatMap { conversation ->
            when (conversation.protocol) {
                is Conversation.ProtocolInfo.Proteus ->
                    deleteMemberFromCloudAndStorage(userId, conversationId)

                is Conversation.ProtocolInfo.MLS -> {
                    if (userId == selfUserId) {
                        deleteMemberFromCloudAndStorage(userId, conversationId).flatMap { result ->
                            mlsConversationRepository.leaveGroup(conversation.protocol.groupId)
                                .map { result }
                        }
                    } else {
                        // when removing a member from an MLS group, don't need to call the api
                        mlsConversationRepository.removeMembersFromMLSGroup(conversation.protocol.groupId, listOf(userId))
                            .map { MemberChangeResult.Changed(Clock.System.now().toString()) }
                    }
                }
            }
        }

    private suspend fun deleteMemberFromCloudAndStorage(userId: UserId, conversationId: ConversationId) =
        wrapApiRequest {
            conversationApi.removeMember(idMapper.toApiModel(userId), idMapper.toApiModel(conversationId))
        }.fold({
            Either.Left(it)
        }, { response ->
            wrapStorageRequest {
                conversationDAO.deleteMemberByQualifiedID(
                    idMapper.toDaoModel(userId),
                    idMapper.toDaoModel(conversationId)
                )
            }.map {
                when (response) {
                    is ConversationMemberRemovedDTO.Changed -> MemberChangeResult.Changed(response.time)
                    ConversationMemberRemovedDTO.Unchanged -> MemberChangeResult.Unchanged
                }
            }
        })

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

    /**
     * For MLS groups we aren't allowed by the BE provide any initial members when creating
     * the group, so we need to provide initial list of members separately.
     */
    private suspend fun persistMembersFromConversationResponseMLS(
        conversationResponse: ConversationResponse,
        users: List<UserId>
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationId = idMapper.fromApiToDao(conversationResponse.id)
            val selfUserId = selfUserId
            // TODO(IMPORTANT!): having an initial value is not the correct approach, the
            //  only valid source for members role is the backend
            //  ---> at the moment the backend doesn't tell us anything about the member role! till then we are setting them as Member
            val membersWithRole = users.map { userId -> Conversation.Member(userId, Conversation.Member.Role.Member) }
            val selfMember = Conversation.Member(selfUserId, Conversation.Member.Role.Admin)
            conversationDAO.insertMembersWithQualifiedId((membersWithRole + selfMember).map(memberMapper::toDaoModel), conversationId)
        }
    }
}
