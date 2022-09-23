package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.TimeParser
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.api.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.coroutines.cancellation.CancellationException

interface ConversationRepository {
    suspend fun getSelfConversationId(): ConversationId
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>>
    suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun observeById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>>
    suspend fun getConversationById(conversationId: ConversationId): Conversation?
    suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationRecipientsForCalling(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo>
    suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Conversation.Member>>
    suspend fun requestToJoinMLSGroup(conversation: Conversation): Either<CoreFailure, Unit>

    /**
     * Fetches a list of all members' IDs or a given conversation including self user
     */
    suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>>
    suspend fun persistMembers(
        members: List<Conversation.Member>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit>

    suspend fun updateMemberFromEvent(
        member: Conversation.Member,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit>

    suspend fun addMembers(userIdList: List<UserId>, conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun deleteMember(userId: UserId, conversationId: ConversationId): Either<CoreFailure, MemberChangeResult>
    suspend fun deleteMembersFromEvent(userIDList: List<UserId>, conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun getOneToOneConversationWithOtherUser(otherUserId: UserId): Either<CoreFailure, Conversation>
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
    suspend fun getConversationsByGroupState(
        groupState: Conversation.ProtocolInfo.MLS.GroupState
    ): Either<StorageFailure, List<Conversation>>

    suspend fun updateConversationNotificationDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit>
    suspend fun updateAllConversationsNotificationDate(date: String): Either<StorageFailure, Unit>
    suspend fun updateConversationModifiedDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit>
    suspend fun updateConversationReadDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit>
    suspend fun getUnreadConversationCount(): Either<StorageFailure, Long>
    suspend fun updateAccessInfo(
        conversationID: ConversationId,
        access: List<Conversation.Access>,
        accessRole: List<Conversation.AccessRole>
    ): Either<CoreFailure, Unit>

    suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        role: Conversation.Member.Role
    ): Either<CoreFailure, Unit>

    suspend fun deleteConversation(conversationId: ConversationId): Either<CoreFailure, Unit>

    /**
     * Gets all of the conversation messages that are assets
     */
    suspend fun getAssetMessages(
        conversationId: ConversationId,
    ): Either<CoreFailure, List<Message>>

    /**
     * Deletes all conversation messages
     */
    suspend fun deleteAllMessages(conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun observeIsUserMember(conversationId: ConversationId, userId: UserId): Flow<Either<CoreFailure, Boolean>>
    suspend fun whoDeletedMe(conversationId: ConversationId): Either<CoreFailure, UserId?>
    suspend fun updateConversationName(
        conversationId: ConversationId,
        conversationName: String,
        timestamp: String
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConversationDataSource internal constructor(
    private val userRepository: UserRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val messageDAO: MessageDAO,
    private val clientDAO: ClientDAO,
    private val clientApi: ClientApi,
    private val timeParser: TimeParser,
    private val selfUserId: UserId,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val conversationStatusMapper: ConversationStatusMapper = MapperProvider.conversationStatusMapper(),
    private val conversationRoleMapper: ConversationRoleMapper = MapperProvider.conversationRoleMapper(),
    private val messageMapper: MessageMapper = MapperProvider.messageMapper()
) : ConversationRepository {

    // TODO:I would suggest preparing another suspend func getSelfUser to get nullable self user,
    // this will help avoid some functions getting stuck when observeSelfUser will filter nullable values
    override suspend fun fetchConversations(): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS).d("Fetching conversations")
        return fetchAllConversationsFromAPI()
    }

    // TODO: Vitor: he UseCase could observeSelfUser and update the flow.
    // But the Repository is too smart, does it by itself, and doesn't let the UseCase handle this.
    override suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> {
        val selfUserTeamId = userRepository.observeSelfUser().first().teamId
        return persistConversations(listOf(event.conversation), selfUserTeamId?.value, originatedFromEvent = true)
    }

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

    private suspend fun fetchAllConversationsFromAPI(): Either<NetworkFailure, Unit> {
        // TODO: mo: I would suggest to use the same approach as in the selfUserId

        val selfUserTeamId = userRepository.observeSelfUser().first().teamId
        var hasMore = true
        var lastPagingState: String? = null
        var latestResult: Either<NetworkFailure, Unit> = Either.Right(Unit)

        while (hasMore && latestResult.isRight()) {
            latestResult = wrapApiRequest {
                kaliumLogger.withFeatureId(CONVERSATIONS).v("Fetching conversation page starting with pagingState $lastPagingState")
                conversationApi.fetchConversationsIds(pagingState = lastPagingState)
            }.onSuccess { pagingResponse ->
                wrapApiRequest {
                    conversationApi.fetchConversationsListDetails(pagingResponse.conversationsIds.toList())
                }.onSuccess { conversations ->
                    if (conversations.conversationsFailed.isNotEmpty()) {
                        kaliumLogger.withFeatureId(CONVERSATIONS)
                            .d("Skipping ${conversations.conversationsFailed.size} conversations failed")
                    }
                    if (conversations.conversationsNotFound.isNotEmpty()) {
                        kaliumLogger.withFeatureId(CONVERSATIONS)
                            .d("Skipping ${conversations.conversationsNotFound.size} conversations not found")
                    }
                    persistConversations(conversations.conversationsFound, selfUserTeamId?.value)
                }.onFailure {
                    kaliumLogger.withFeatureId(CONVERSATIONS).e("Error fetching conversation details $it")
                }

                lastPagingState = pagingResponse.pagingState
                hasMore = pagingResponse.hasMore
            }.onFailure {
                kaliumLogger.withFeatureId(CONVERSATIONS).e("Error fetching conversation ids $it")
                Either.Left(it)
            }.map { }
        }

        return latestResult
    }

    private suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: String?,
        originatedFromEvent: Boolean = false
    ) = wrapStorageRequest {
        val conversationEntities = conversations.map { conversationResponse ->
            conversationMapper.fromApiModelToDaoModel(
                conversationResponse,
                mlsGroupState = conversationResponse.groupId?.let { mlsGroupState(idMapper.fromGroupIDEntity(it), originatedFromEvent) },
                selfUserTeamId?.let { TeamId(it) }
            )
        }
        conversationDAO.insertConversations(conversationEntities)
        conversations.forEach { conversationsResponse ->
            conversationDAO.insertMembersWithQualifiedId(
                memberMapper.fromApiModelToDaoModel(conversationsResponse.members), idMapper.fromApiToDao(conversationsResponse.id)
            )
        }
    }

    private suspend fun mlsGroupState(groupId: GroupID, originatedFromEvent: Boolean = false): ConversationEntity.GroupState =
        mlsConversationRepository.hasEstablishedMLSGroup(groupId).fold({
            throw IllegalStateException(it.toString()) // TODO find a more fitting exception?
        }, { exists ->
            if (exists) {
                ConversationEntity.GroupState.ESTABLISHED
            } else {
                if (originatedFromEvent) {
                    ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE
                } else {
                    ConversationEntity.GroupState.PENDING_JOIN
                }
            }
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
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>> =
        conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationID))
            .wrapStorageRequest()
            .flatMapLatest {
                it.fold(
                    { flowOf(Either.Left(it)) },
                    { getConversationDetailsFlow(conversationMapper.fromDaoModel(it)) }
                )
            }

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

    private suspend fun getConversationDetailsFlow(conversation: Conversation): Flow<Either<StorageFailure, ConversationDetails>> =
        when (conversation.type) {
            Conversation.Type.SELF -> flowOf(Either.Right(ConversationDetails.Self(conversation)))
            // TODO(user-metadata): get actual legal hold status
            Conversation.Type.GROUP -> getGroupConversationDetailsFlow(conversation)
            Conversation.Type.CONNECTION_PENDING, Conversation.Type.ONE_ON_ONE -> getOneToOneConversationDetailsFlow(conversation)
        }

    private suspend fun observeLastUnreadMessage(conversation: Conversation): Flow<Message?> =
        messageDAO.observeLastUnreadMessage(idMapper.toDaoModel(conversation.id))
            .map { it?.let { messageMapper.fromEntityToMessage(it) } }
            .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getGroupConversationDetailsFlow(conversation: Conversation): Flow<Either<StorageFailure, ConversationDetails>> {
        return userRepository.observeSelfUser() // todo : why are we observing self user if we have it injected in the constructor
            .flatMapLatest { selfUser ->
                combine(
                    observeUnreadMessageCount(conversation),
                    observeLastUnreadMessage(conversation),
                    observeUnreadMentionsCount(conversation, selfUser.id)
                ) { unreadMessageCount: Long, lastUnreadMessage: Message?, unreadMentionsCount: Long ->
                    Either.Right(
                        ConversationDetails.Group(
                            conversation = conversation,
                            legalHoldStatus = LegalHoldStatus.DISABLED,
                            unreadMessagesCount = unreadMessageCount,
                            unreadMentionsCount = unreadMentionsCount,
                            lastUnreadMessage = lastUnreadMessage,
                        )
                    )
                }
            }.distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun getOneToOneConversationDetailsFlow(conversation: Conversation): Flow<Either<StorageFailure, ConversationDetails>> {
        return observeConversationMembers(conversation.id)
            .combine(userRepository.observeSelfUser()) { members, selfUser ->
                selfUser to members.firstOrNull { item -> item.id != selfUser.id }
            }
            .flatMapLatest { (selfUser, otherMember) ->
                val otherUserFlow = if (otherMember != null) userRepository.getKnownUser(otherMember.id) else flowOf(otherMember)
                otherUserFlow
                    .wrapStorageRequest()
                    .mapRight { selfUser to it }
            }
            .flatMapLatest {
                it.fold(
                    { storageFailure ->
                        logMemberDetailsError(conversation, storageFailure)
                        flowOf(Either.Left(storageFailure))
                    },
                    { (selfUser, otherUser) ->
                        combine(
                            observeUnreadMessageCount(conversation),
                            observeLastUnreadMessage(conversation),
                            observeUnreadMentionsCount(conversation, selfUser.id)
                        ) { unreadMessageCount: Long, lastUnreadMessage: Message?, unreadMentionsCount: Long ->
                            Either.Right(
                                conversationMapper.toConversationDetailsOneToOne(
                                    conversation = conversation,
                                    otherUser = otherUser,
                                    selfUser = selfUser,
                                    unreadMessageCount = unreadMessageCount,
                                    unreadMentionsCount = unreadMentionsCount,
                                    lastUnreadMessage = lastUnreadMessage
                                )
                            )
                        }
                    }
                )
            }.distinctUntilChanged()
    }

    private suspend fun observeUnreadMessageCount(conversation: Conversation): Flow<Long> {
        return if (conversation.supportsUnreadMessageCount) {
            messageDAO.observeUnreadMessageCount(idMapper.toDaoModel(conversation.id), idMapper.toDaoModel(selfUserId))
        } else {
            flowOf(0L)
        }
    }

    private suspend fun observeUnreadMentionsCount(conversation: Conversation, selfUserId: UserId): Flow<Long> {
        return if (conversation.supportsUnreadMessageCount) {
            messageDAO.observeUnreadMentionsCount(
                idMapper.toDaoModel(conversation.id),
                idMapper.toDaoModel(selfUserId)
            )
        } else {
            flowOf(0L)
        }
    }

    // TODO: as for now lastModifiedDate and lastReadDate is saved as String
    // in ISO format, using a timestamp would make it possible to just do a comparance
    // on if the timestamp is bigger inside the domain model or on a Instant object
    private fun hasNewMessages(conversation: Conversation) =
        with(conversation) {
            if (lastModifiedDate != null) {
                timeParser.isTimeBefore(lastReadDate, lastModifiedDate)
            } else {
                false
            }
        }

    private fun logMemberDetailsError(conversation: Conversation, error: StorageFailure): Unit = when (error) {
        is StorageFailure.DataNotFound ->
            kaliumLogger.withFeatureId(CONVERSATIONS).e("DataNotFound when fetching conversation members: $error")

        is StorageFailure.Generic -> {
            if (error.rootCause !is CancellationException) {
                // Ignore CancellationException
            } else {
                kaliumLogger.withFeatureId(CONVERSATIONS).e("Failure getting other 1:1 user for $conversation", error.rootCause)
            }
        }
    }

    // Deprecated notice, so we can use newer versions of Kalium on Reloaded without breaking things.
    @Deprecated("This doesn't return conversation details", ReplaceWith("detailsById"))
    override suspend fun observeById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>> =
        conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId)).filterNotNull()
            .map(conversationMapper::fromDaoModel)
            .wrapStorageRequest()

    // TODO: refactor. 3 Ways different ways to return conversation details?!
    override suspend fun getConversationById(conversationId: ConversationId): Conversation? =
        conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId))
            .map { conversationEntity ->
                conversationEntity?.let { conversationMapper.fromDaoModel(it) }
            }.firstOrNull()

    override suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation> = wrapStorageRequest {
        conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationId))?.let {
            conversationMapper.fromDaoModel(it)
        }
    }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId)).first()?.protocolInfo
        }

    override suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Conversation.Member>> =
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationID)).map { members ->
            members.map(memberMapper::fromDaoModel)
        }

    override suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

    override suspend fun persistMembers(
        members: List<Conversation.Member>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> =
        userRepository.fetchUsersIfUnknownByIds(members.map { it.id }.toSet()).flatMap {
            wrapStorageRequest {
                conversationDAO.insertMembersWithQualifiedId(
                    members.map(memberMapper::toDaoModel), idMapper.toDaoModel(conversationID)
                )
            }
        }

    override suspend fun updateMemberFromEvent(member: Conversation.Member, conversationID: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateMember(memberMapper.toDaoModel(member), idMapper.toDaoModel(conversationID))
        }

    override suspend fun addMembers(
        userIdList: List<UserId>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> = wrapApiRequest {
        val users = userIdList.map {
            idMapper.toApiModel(it)
        }
        val addParticipantRequest = AddConversationMembersRequest(users, DEFAULT_MEMBER_ROLE)
        conversationApi.addMember(
            addParticipantRequest, idMapper.toApiModel(conversationID)
        )
    }.flatMap {
        userIdList.map { userId ->
            // TODO: mapping the user id list to members with a made up role is incorrect and a recipe for disaster
            Conversation.Member(userId, Conversation.Member.Role.Member)
        }.let { membersList ->
            persistMembers(membersList, conversationID)
        }
    }

    override suspend fun deleteMember(
        userId: UserId,
        conversationId: ConversationId
    ): Either<CoreFailure, MemberChangeResult> =
        detailsById(conversationId).flatMap { conversation ->
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

    override suspend fun deleteMembersFromEvent(
        userIDList: List<UserId>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.deleteMembersByQualifiedID(
                userIDList.map { idMapper.toDaoModel(it) },
                idMapper.toDaoModel(conversationID)
            )
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
        }.flatMap { conversationResponse ->
            val teamId = selfUser.teamId
            val conversationEntity = conversationMapper.fromApiModelToDaoModel(
                conversationResponse, mlsGroupState = ConversationEntity.GroupState.PENDING_CREATION, teamId
            )
            val conversation = conversationMapper.fromDaoModel(conversationEntity)

            wrapStorageRequest {
                conversationDAO.insertConversation(conversationEntity)
            }.flatMap {
                when (conversation.protocol) {
                    is Conversation.ProtocolInfo.Proteus -> persistMembersFromConversationResponse(conversationResponse)
                    is Conversation.ProtocolInfo.MLS -> persistMembersFromConversationResponseMLS(
                        conversationResponse, usersList
                    )
                }
            }.flatMap {
                when (conversation.protocol) {
                    is Conversation.ProtocolInfo.Proteus -> Either.Right(conversation)
                    is Conversation.ProtocolInfo.MLS ->
                        mlsConversationRepository
                            .establishMLSGroup(conversation.protocol.groupId)
                            .flatMap { Either.Right(conversation) }
                }
            }
        }
    }

    override suspend fun getConversationsForNotifications(): Flow<List<Conversation>> =
        conversationDAO.getConversationsForNotifications()
            .filterNotNull()
            .map { it.map(conversationMapper::fromDaoModel) }

    override suspend fun getConversationsByGroupState(
        groupState: Conversation.ProtocolInfo.MLS.GroupState
    ): Either<StorageFailure, List<Conversation>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByGroupState(conversationMapper.toDAOGroupState(groupState))
                .map(conversationMapper::fromDaoModel)
        }

    override suspend fun updateConversationNotificationDate(
        qualifiedID: QualifiedID,
        date: String
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateConversationNotificationDate(idMapper.toDaoModel(qualifiedID), date)
        }

    override suspend fun updateAllConversationsNotificationDate(date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateAllConversationsNotificationDate(date) }

    override suspend fun updateConversationModifiedDate(
        qualifiedID: QualifiedID,
        date: String
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationModifiedDate(idMapper.toDaoModel(qualifiedID), date) }

    override suspend fun updateAccessInfo(
        conversationID: ConversationId,
        access: List<Conversation.Access>,
        accessRole: List<Conversation.AccessRole>
    ): Either<CoreFailure, Unit> =
        ConversationAccessInfoDTO(
            access.map { conversationMapper.toApiModel(it) }.toSet(),
            accessRole.map { conversationMapper.toApiModel(it) }.toSet()
        ).let { updateConversationAccessRequest ->
            wrapApiRequest {
                conversationApi.updateAccessRole(idMapper.toApiModel(conversationID), updateConversationAccessRequest)
            }
        }.flatMap { response ->
            when (response) {
                UpdateConversationAccessResponse.AccessUnchanged -> {
                    // no need to update conversation
                    Either.Right(Unit)
                }

                is UpdateConversationAccessResponse.AccessUpdated -> {
                    wrapStorageRequest {
                        conversationDAO.updateAccess(
                            idMapper.fromDtoToDao(response.event.qualifiedConversation),
                            conversationMapper.toDAOAccess(response.event.data.access),
                            response.event.data.accessRole.let { conversationMapper.toDAOAccessRole(it) }
                        )
                    }
                }
            }
        }

    /**
     * Update the conversation seen date, which is a date when the user sees the content of the conversation.
     */
    override suspend fun updateConversationReadDate(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationReadDate(idMapper.toDaoModel(qualifiedID), date) }

    override suspend fun getUnreadConversationCount(): Either<StorageFailure, Long> =
        wrapStorageRequest { conversationDAO.getUnreadConversationCount() }

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

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        wrapStorageRequest {
            memberMapper.fromMapOfClientsEntityToRecipients(
                clientDAO.getClientsOfConversation(idMapper.toDaoModel(conversationId))
            )
        }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipientsForCalling(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        getConversationMembers(conversationId).map { it.map(idMapper::toApiModel) }.flatMap {
            wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
        }

    override suspend fun getOneToOneConversationWithOtherUser(otherUserId: UserId): Either<StorageFailure, Conversation> {
        return wrapStorageRequest {
            val conversationEntity = conversationDAO.getConversationWithOtherUser(idMapper.toDaoModel(otherUserId))
            conversationEntity?.let { conversationMapper.fromDaoModel(it) }
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
            memberUpdateRequest = conversationStatusMapper.toMutedStatusApiModel(mutedStatus, mutedStatusTimestamp),
            conversationId = idMapper.toApiModel(conversationId)
        )
    }.flatMap {
        wrapStorageRequest {
            conversationDAO.updateConversationMutedStatus(
                conversationId = idMapper.toDaoModel(conversationId),
                mutedStatus = conversationStatusMapper.toMutedStatusDaoModel(mutedStatus),
                mutedStatusTimestamp = mutedStatusTimestamp
            )
        }
    }

    /**
     * Updates the conversation member role, both remotely and local
     */
    override suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        role: Conversation.Member.Role
    ): Either<CoreFailure, Unit> = wrapApiRequest {
        conversationApi.updateConversationMemberRole(
            conversationId = idMapper.toApiModel(conversationId),
            userId = idMapper.toApiModel(userId),
            conversationMemberRoleDTO = ConversationMemberRoleDTO(conversationRole = conversationRoleMapper.toApi(role))
        )
    }.flatMap {
        wrapStorageRequest {
            conversationDAO.updateConversationMemberRole(
                conversationId = idMapper.toDaoModel(conversationId),
                userId = idMapper.toDaoModel(userId),
                role = conversationRoleMapper.toDAO(role)
            )
        }
    }

    override suspend fun deleteConversation(conversationId: ConversationId) = wrapStorageRequest {
        conversationDAO.deleteConversationByQualifiedID(idMapper.toDaoModel(conversationId))
    }

    override suspend fun getAssetMessages(
        conversationId: ConversationId,
    ): Either<StorageFailure, List<Message>> =
        wrapStorageRequest {
            messageDAO.getConversationMessagesByContentType(
                idMapper.toDaoModel(conversationId),
                MessageEntity.ContentType.ASSET
            ).map(messageMapper::fromEntityToMessage)
        }

    override suspend fun deleteAllMessages(conversationId: ConversationId): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            messageDAO.deleteAllConversationMessages(idMapper.toDaoModel(conversationId))
        }

    override suspend fun observeIsUserMember(conversationId: ConversationId, userId: UserId): Flow<Either<CoreFailure, Boolean>> =
        conversationDAO.observeIsUserMember(idMapper.toDaoModel(conversationId), idMapper.toDaoModel(userId))
            .wrapStorageRequest()

    override suspend fun whoDeletedMe(conversationId: ConversationId): Either<CoreFailure, UserId?> = wrapStorageRequest {
        val selfUserId = userRepository.observeSelfUser().first().id

        conversationDAO.whoDeletedMeInConversation(
            idMapper.toDaoModel(conversationId),
            idMapper.toStringDaoModel(selfUserId)
        )?.let { idMapper.fromDaoModel(it) }
    }

    override suspend fun updateConversationName(conversationId: ConversationId, conversationName: String, timestamp: String) =
        wrapStorageRequest { conversationDAO.updateConversationName(idMapper.toDaoModel(conversationId), conversationName, timestamp) }

    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
    }
}
