package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
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
import com.wire.kalium.network.api.ConversationId as RemoteConversationId
import com.wire.kalium.persistence.dao.Member as MemberEntity

interface ConversationRepository {
    suspend fun getSelfConversationId(): ConversationId
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<ConversationDetails>
    suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo>
    suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Member>>
    suspend fun persistMember(member: MemberEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun persistMembers(members: List<MemberEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun deleteMember(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun deleteMembers(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun getOneToOneConversationDetailsByUserId(otherUserId: UserId): Either<CoreFailure, ConversationDetails.OneOne>
    suspend fun createGroupConversation(
        name: String? = null,
        members: List<Member>,
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

    //TODO:I would suggest preparing another suspend func getSelfUser to get nullable self user,
    // this will help avoid some functions getting stuck when observeSelfUser will filter nullable values
    override suspend fun fetchConversations(): Either<CoreFailure, Unit> {
        kaliumLogger.d("Fetching conversations")
        val selfUserTeamId = userRepository.observeSelfUser().first().teamId

        return fetchAllConversationsFromAPI().onFailure { networkFailure ->
            val throwable = (networkFailure as? NetworkFailure.ServerMiscommunication)?.rootCause
            kaliumLogger.e("Failed to fetch all conversations due to network error", throwable)
        }.flatMap { conversations ->
            kaliumLogger.d("Persisting fetched conversations into storage")
            persistConversations(conversations, selfUserTeamId)
        }
    }

    override suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> {
        val selfUserTeamId = userRepository.observeSelfUser().first().teamId
        return persistConversations(listOf(event.conversation), selfUserTeamId)
    }

    private suspend fun fetchAllConversationsFromAPI(): Either<NetworkFailure, List<ConversationResponse>> {
        var hasMore = true
        var lastPagingState: String? = null
        var latestResult: Either<NetworkFailure, Unit> = Either.Right(Unit)
        val allConversationsIds = mutableSetOf<RemoteConversationId>()

        while (hasMore && latestResult.isRight()) {
            latestResult = wrapApiRequest {
                kaliumLogger.v("Fetching conversation page starting with pagingState $lastPagingState")
                conversationApi.fetchConversationsIds(pagingState = lastPagingState)
            }.onSuccess {
                allConversationsIds += it.conversationsIds
                lastPagingState = it.pagingState
                hasMore = it.hasMore
            }.onFailure {
                Either.Left(it)
            }.map {

            }
        }

        return wrapApiRequest {
            conversationApi.fetchConversationsListDetails(allConversationsIds.toList())
        }.map {
            it.conversationsFound
        }
    }

    private suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: String?
    ) = wrapStorageRequest {
        val conversationEntities = conversations.map { conversationResponse ->
            conversationMapper.fromApiModelToDaoModel(
                conversationResponse,
                mlsGroupState = conversationResponse.groupId?.let { mlsGroupState(it) },
                selfUserTeamId?.let { TeamId(it) })
        }
        conversationDAO.insertConversations(conversationEntities)
        conversations.forEach { conversationsResponse ->
            conversationDAO.insertMembers(
                memberMapper.fromApiModelToDaoModel(conversationsResponse.members),
                idMapper.fromApiToDao(conversationsResponse.id)
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
        conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationID))
            .wrapStorageRequest()
            .onlyRight()
            .map(conversationMapper::fromDaoModel)
            .flatMapLatest(::getConversationDetailsFlow)

    override suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            conversationApi.fetchConversationDetails(idMapper.toApiModel(conversationID))
        }.flatMap {
            val selfUserTeamId = userRepository.getSelfUser()?.teamId
            persistConversations(listOf(it), selfUserTeamId)
        }
    }

    private suspend fun getConversationDetailsFlow(conversation: Conversation): Flow<ConversationDetails> =
        when (conversation.type) {
            Conversation.Type.SELF -> flowOf(ConversationDetails.Self(conversation))
            Conversation.Type.GROUP ->
                flowOf(
                    ConversationDetails.Group(
                        conversation,
                        LegalHoldStatus.DISABLED //TODO(user-metadata): get actual legal hold status
                    )
                )
            // TODO(connection-requests): Handle requests instead of filtering them out
            Conversation.Type.CONNECTION_PENDING,
            Conversation.Type.ONE_ON_ONE -> {
                val selfUser = userRepository.observeSelfUser().first()

                getConversationMembers(conversation.id)
                    .map { members ->
                        members.firstOrNull { itemId -> itemId != selfUser.id }
                    }
                    .fold({
                        when (it) {
                            StorageFailure.DataNotFound -> {
                                kaliumLogger.e("DataNotFound when fetching conversation members: $it")
                            }
                            is StorageFailure.Generic -> {
                                kaliumLogger.e("Failure getting other 1:1 user for $conversation", it.rootCause)
                            }
                        }
                        emptyFlow()
                    }, { otherUserIdOrNull ->
                        otherUserIdOrNull?.let {
                            userRepository.getKnownUser(it)
                        } ?: run {
                            emptyFlow()
                        }
                    }).filterNotNull().map { otherUser ->
                        conversationMapper.toConversationDetailsOneToOne(conversation, otherUser, selfUser)
                    }
            }
        }

    //Deprecated notice, so we can use newer versions of Kalium on Reloaded without breaking things.
    @Deprecated("This doesn't return conversation details", ReplaceWith("getConversationDetailsById"))
    override suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>> =
        wrapStorageRequest {
            conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId))
                .filterNotNull()
                .map(conversationMapper::fromDaoModel)
        }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId)).first()?.protocolInfo
        }

    override suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Member>> =
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationID)).map { members ->
            members.map(memberMapper::fromDaoModel)
        }

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    private suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

    override suspend fun persistMember(member: MemberEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.insertMember(member, conversationID) }

    override suspend fun persistMembers(members: List<MemberEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.insertMembers(members, conversationID) }

    override suspend fun deleteMember(userID: QualifiedIDEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.deleteMemberByQualifiedID(userID, conversationID) }

    override suspend fun deleteMembers(userIDList: List<QualifiedIDEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.deleteMembersByQualifiedID(userIDList, conversationID) }

    override suspend fun createGroupConversation(
        name: String?,
        members: List<Member>,
        options: ConversationOptions
    ): Either<CoreFailure, Conversation> = wrapStorageRequest {
        userRepository.observeSelfUser().first()
    }.flatMap { selfUser ->
        wrapApiRequest {
            conversationApi.createNewConversation(
                conversationMapper.toApiModel(name, members, selfUser.teamId, options)
            )
        }.flatMap { conversationResponse ->
            val teamId = selfUser.teamId?.let { TeamId(it) }
            val conversationEntity = conversationMapper.fromApiModelToDaoModel(
                conversationResponse,
                mlsGroupState = ConversationEntity.GroupState.PENDING,
                teamId
            )
            val conversation = conversationMapper.fromDaoModel(conversationEntity)

            wrapStorageRequest {
                conversationDAO.insertConversation(conversationEntity)
            }.flatMap {
                when (conversationEntity.protocolInfo) {
                    is ProtocolInfo.Proteus -> persistMembersFromConversationResponse(conversationResponse)
                    is ProtocolInfo.MLS -> persistMembersFromConversationResponseMLS(conversationResponse, members)
                }
            }.flatMap {
                when (conversationEntity.protocolInfo) {
                    is ProtocolInfo.Proteus -> Either.Right(conversation)
                    is ProtocolInfo.MLS -> mlsConversationRepository.establishMLSGroup((conversationEntity.protocolInfo as ProtocolInfo.MLS).groupId)
                        .flatMap { Either.Right(conversation) }
                }
            }
        }
    }

    override suspend fun getConversationsForNotifications(): Flow<List<Conversation>> =
        conversationDAO.getConversationsForNotifications()
            .filterNotNull()
            .map { it.map(conversationMapper::fromDaoModel) }

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
        members: List<Member>
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationId = idMapper.fromApiToDao(conversationResponse.id)
            val selfUserId = userRepository.getSelfUserId()
            val selfMember = Member(selfUserId)
            conversationDAO.insertMembers((members + selfMember).map(memberMapper::toDaoModel), conversationId)
        }
    }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        getConversationMembers(conversationId)
            .map { it.map(idMapper::toApiModel) }
            .flatMap {
                wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
            }

    override suspend fun getOneToOneConversationDetailsByUserId(otherUserId: UserId): Either<StorageFailure, ConversationDetails.OneOne> {
        return wrapStorageRequest {
            conversationDAO.getAllConversationWithOtherUser(idMapper.toDaoModel(otherUserId))
                .firstOrNull { it.type == ConversationEntity.Type.ONE_ON_ONE }
                ?.let { conversationEntity ->
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
