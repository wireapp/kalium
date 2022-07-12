package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.conversation.AddParticipantRequest
import com.wire.kalium.network.api.conversation.AddParticipantResponse
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    suspend fun getSelfConversationId(): ConversationId
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<ConversationDetails>
    suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>>
    suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo>
    suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Member>>

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>>
    suspend fun persistMembers(members: List<Member>, conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun addMembers(userIdList: List<UserId>, conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun deleteMember(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun deleteMembers(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun getOneToOneConversationDetailsByUserId(otherUserId: UserId): Either<CoreFailure, ConversationDetails.OneOne>
    suspend fun createGroupConversation(
        name: String? = null,
        usersList: List<UserId>,
        options: ConversationOptions = ConversationOptions()
    ): Either<CoreFailure, Conversation>

    suspend fun updateMutedStatus(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<CoreFailure, Unit>

    suspend fun getConversationsForNotifications(): Flow<List<Conversation>>
    suspend fun updateConversationNotificationDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit>
    suspend fun updateAllConversationsNotificationDate(date: String): Either<StorageFailure, Unit>
    suspend fun updateConversationModifiedDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit>
}

@Suppress("LongParameterList", "TooManyFunctions")
class ConversationDataSource(
    private val userRepository: UserRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val clientApi: ClientApi,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val conversationStatusMapper: ConversationStatusMapper = MapperProvider.conversationStatusMapper()
) : ConversationRepository {

    // TODO:I would suggest preparing another suspend func getSelfUser to get nullable self user,
    // this will help avoid some functions getting stuck when observeSelfUser will filter nullable values
    override suspend fun fetchConversations(): Either<CoreFailure, Unit> {
        kaliumLogger.d("Fetching conversations")
        return fetchAllConversationsFromAPI()
    }

    // TODO: Vitor: he UseCase could observeSelfUser and update the flow.
    // But the Repository is too smart, does it by itself, and doesn't let the UseCase handle this.
    override suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> {
        val selfUserTeamId = userRepository.observeSelfUser().first().teamId
        return persistConversations(listOf(event.conversation), selfUserTeamId?.value)
    }

    private suspend fun fetchAllConversationsFromAPI(): Either<NetworkFailure, Unit> {
        val selfUserTeamId = userRepository.observeSelfUser().first().teamId
        var hasMore = true
        var lastPagingState: String? = null
        var latestResult: Either<NetworkFailure, Unit> = Either.Right(Unit)

        while (hasMore && latestResult.isRight()) {
            latestResult = wrapApiRequest {
                kaliumLogger.v("Fetching conversation page starting with pagingState $lastPagingState")
                conversationApi.fetchConversationsIds(pagingState = lastPagingState)
            }.onSuccess { pagingResponse ->
                wrapApiRequest {
                    conversationApi.fetchConversationsListDetails(pagingResponse.conversationsIds.toList())
                }.onSuccess { conversations ->
                    if (conversations.conversationsFailed.isNotEmpty()) {
                        kaliumLogger.d("Skipping ${conversations.conversationsFailed.size} conversations failed")
                    }
                    if (conversations.conversationsNotFound.isNotEmpty()) {
                        kaliumLogger.d("Skipping ${conversations.conversationsNotFound.size} conversations not found")
                    }
                    persistConversations(conversations.conversationsFound, selfUserTeamId?.value)
                }.onFailure {
                    kaliumLogger.e("Error fetching conversation details $it")
                }

                lastPagingState = pagingResponse.pagingState
                hasMore = pagingResponse.hasMore
            }.onFailure {
                kaliumLogger.e("Error fetching conversation ids $it")
                Either.Left(it)
            }.map { }
        }

        return latestResult
    }

    private suspend fun persistConversations(conversations: List<ConversationResponse>, selfUserTeamId: String?) = wrapStorageRequest {
        val conversationEntities = conversations.map { conversationResponse ->
            conversationMapper.fromApiModelToDaoModel(conversationResponse,
                mlsGroupState = conversationResponse.groupId?.let { mlsGroupState(it) },
                selfUserTeamId?.let { TeamId(it) })
        }
        conversationDAO.insertConversations(conversationEntities)
        conversations.forEach { conversationsResponse ->
            conversationDAO.insertMembers(
                memberMapper.fromApiModelToDaoModel(conversationsResponse.members), idMapper.fromApiToDao(conversationsResponse.id)
            )
        }
    }

    private suspend fun mlsGroupState(groupId: String): ConversationEntity.GroupState =
        mlsConversationRepository.hasEstablishedMLSGroup(groupId).fold({
            throw IllegalStateException(it.toString()) // TODO find a more fitting exception?
        }, { exists ->
            if (exists) ConversationEntity.GroupState.ESTABLISHED else ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
        })

    override suspend fun getSelfConversationId(): ConversationId = idMapper.fromDaoModel(conversationDAO.getSelfConversationId())

    override suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>> = wrapStorageRequest {
        observeConversationList()
    }

    override suspend fun observeConversationList(): Flow<List<Conversation>> {
        return conversationDAO.getAllConversations().map { it.map(conversationMapper::fromDaoModel) }
    }

    /**
     * Gets a flow that allows observing of
     */
    override suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<ConversationDetails> =
        conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationID)).wrapStorageRequest().onlyRight()
            .map(conversationMapper::fromDaoModel).flatMapLatest(::getConversationDetailsFlow)

    override suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            conversationApi.fetchConversationDetails(idMapper.toApiModel(conversationID))
        }.flatMap {
            val selfUserTeamId = userRepository.getSelfUser()?.teamId
            persistConversations(listOf(it), selfUserTeamId?.value)
        }
    }

    override suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.getConversationByQualifiedID(QualifiedIDEntity(conversationID.value, conversationID.domain))
    }.run {
        if (isLeft()) {
            fetchConversation(conversationID)
        } else {
            Either.Right(Unit)
        }
    }

    private suspend fun getConversationDetailsFlow(conversation: Conversation): Flow<ConversationDetails> = when (conversation.type) {
        Conversation.Type.SELF -> flowOf(ConversationDetails.Self(conversation))
        // TODO(user-metadata): get actual legal hold status
        Conversation.Type.GROUP -> flowOf(ConversationDetails.Group(conversation, LegalHoldStatus.DISABLED))
        Conversation.Type.CONNECTION_PENDING, Conversation.Type.ONE_ON_ONE -> getOneToOneConversationDetailsFlow(conversation)
    }

    private suspend fun getOneToOneConversationDetailsFlow(conversation: Conversation): Flow<ConversationDetails> {
        val selfUser = userRepository.observeSelfUser().first()
        return getConversationMembers(conversation.id).map { members ->
            members.firstOrNull { itemId -> itemId != selfUser.id }
        }.fold(
            { storageFailure -> logMemberDetailsError(conversation, storageFailure) },
            { otherUserId -> otherUserId?.let { userRepository.getKnownUser(it) } ?: emptyFlow() }
        ).filterNotNull().map { otherUser -> conversationMapper.toConversationDetailsOneToOne(conversation, otherUser, selfUser) }
    }

    private fun logMemberDetailsError(conversation: Conversation, error: StorageFailure): Flow<OtherUser> {
        when (error) {
            is StorageFailure.DataNotFound -> kaliumLogger.e("DataNotFound when fetching conversation members: $error")
            is StorageFailure.Generic -> kaliumLogger.e("Failure getting other 1:1 user for $conversation", error.rootCause)
        }
        return emptyFlow()
    }

    // Deprecated notice, so we can use newer versions of Kalium on Reloaded without breaking things.
    @Deprecated("This doesn't return conversation details", ReplaceWith("detailsById"))
    override suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>> =
        wrapStorageRequest {
            conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId)).filterNotNull()
                .map(conversationMapper::fromDaoModel)
        }

    override suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation> = wrapStorageRequest {
        conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationId))?.let {
            conversationMapper.fromDaoModel(it)
        }
    }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId)).first()?.protocolInfo
        }

    override suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Member>> =
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationID)).map { members ->
            members.map(memberMapper::fromDaoModel)
        }

    override suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

    override suspend fun persistMembers(members: List<Member>, conversationID: ConversationId): Either<CoreFailure, Unit> =
        userRepository.fetchUsersIfUnknownByIds(members.map { it.id }.toSet()).flatMap {
            wrapStorageRequest {
                conversationDAO.insertMembers(
                    members.map(memberMapper::toDaoModel), idMapper.toDaoModel(conversationID)
                )
            }
        }

    override suspend fun addMembers(userIdList: List<UserId>, conversationID: ConversationId): Either<CoreFailure, Unit> = wrapApiRequest {
        val users = userIdList.map {
            idMapper.toApiModel(it)
        }
        val addParticipantRequest = AddParticipantRequest(users, DEFAULT_MEMBER_ROLE)
        conversationApi.addParticipant(
            addParticipantRequest, idMapper.toApiModel(conversationID)
        )
    }.flatMap {
        when (it) {
            is AddParticipantResponse.ConversationUnchanged -> Either.Right(Unit)
            // TODO: the server response with an event can we use event processor to handle it
            is AddParticipantResponse.UserAdded -> userIdList.map { userId ->
                // TODO: mapping the user id list to members with a made up role is incorrect and a recipe for disaster
                Member(userId, Member.Role.Member)
            }.let { membersList ->
                persistMembers(membersList, conversationID)
            }
        }
    }

    override suspend fun deleteMember(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.deleteMemberByQualifiedID(userID, conversationID) }

    override suspend fun deleteMembers(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.deleteMembersByQualifiedID(userIDList, conversationID) }

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
        }.flatMap { conversationResponse ->
            val teamId = selfUser.teamId
            val conversationEntity = conversationMapper.fromApiModelToDaoModel(
                conversationResponse, mlsGroupState = ConversationEntity.GroupState.PENDING, teamId
            )
            val conversation = conversationMapper.fromDaoModel(conversationEntity)

            wrapStorageRequest {
                conversationDAO.insertConversation(conversationEntity)
            }.flatMap {
                when (conversationEntity.protocolInfo) {
                    is ProtocolInfo.Proteus -> persistMembersFromConversationResponse(conversationResponse)
                    is ProtocolInfo.MLS -> persistMembersFromConversationResponseMLS(
                        conversationResponse, usersList
                    )
                }
            }.flatMap {
                when (conversationEntity.protocolInfo) {
                    is ProtocolInfo.Proteus -> Either.Right(conversation)
                    is ProtocolInfo.MLS -> mlsConversationRepository
                        .establishMLSGroup((conversationEntity.protocolInfo as ProtocolInfo.MLS).groupId)
                        .flatMap { Either.Right(conversation) }
                }
            }
        }
    }

    override suspend fun getConversationsForNotifications(): Flow<List<Conversation>> =
        conversationDAO.getConversationsForNotifications().filterNotNull().map { it.map(conversationMapper::fromDaoModel) }

    override suspend fun updateConversationNotificationDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationNotificationDate(idMapper.toDaoModel(qualifiedID), date) }

    override suspend fun updateAllConversationsNotificationDate(date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateAllConversationsNotificationDate(date) }

    override suspend fun updateConversationModifiedDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationModifiedDate(idMapper.toDaoModel(qualifiedID), date) }

    private suspend fun persistMembersFromConversationResponse(conversationResponse: ConversationResponse): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationId = idMapper.fromApiToDao(conversationResponse.id)
            conversationDAO.insertMembers(memberMapper.fromApiModelToDaoModel(conversationResponse.members), conversationId)
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
            val selfUserId = userRepository.getSelfUserId()
            // TODO(IMPORTANT!): having an initial value is not the correct approach, the
            //  only valid source for members role is the backend
            //  ---> at the moment the backend doesn't tell us anything about the member role! till then we are setting them as Member
            val membersWithRole = users.map { userId -> Member(userId, Member.Role.Member) }
            val selfMember = Member(selfUserId, Member.Role.Admin)
            conversationDAO.insertMembers((membersWithRole + selfMember).map(memberMapper::toDaoModel), conversationId)
        }
    }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        getConversationMembers(conversationId).map { it.map(idMapper::toApiModel) }.flatMap {
            wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
        }

    override suspend fun getOneToOneConversationDetailsByUserId(otherUserId: UserId): Either<StorageFailure, ConversationDetails.OneOne> {
        return wrapStorageRequest {
            conversationDAO.getAllConversationWithOtherUser(idMapper.toDaoModel(otherUserId))
                .firstOrNull { it.type == ConversationEntity.Type.ONE_ON_ONE }?.let { conversationEntity ->
                    conversationMapper.fromDaoModel(conversationEntity)
                }?.let { conversation ->
                    userRepository.getKnownUser(otherUserId).first()?.let { otherUser ->
                        val selfUser = userRepository.observeSelfUser().first()

                        conversationMapper.toConversationDetailsOneToOne(conversation, otherUser, selfUser)
                    }
                }
        }
    }

    /**
     * Updates the conversation muting options status and the timestamp of the applied change, both remotely and local
     */
    override suspend fun updateMutedStatus(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<CoreFailure, Unit> = wrapApiRequest {
        conversationApi.updateConversationMemberState(
            memberUpdateRequest = conversationStatusMapper.toApiModel(mutedStatus, mutedStatusTimestamp),
            conversationId = idMapper.toApiModel(conversationId)
        )
    }.flatMap {
        wrapStorageRequest {
            conversationDAO.updateConversationMutedStatus(
                conversationId = idMapper.toDaoModel(conversationId),
                mutedStatus = conversationStatusMapper.toDaoModel(mutedStatus),
                mutedStatusTimestamp = mutedStatusTimestamp
            )
        }
    }

    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
    }
}
