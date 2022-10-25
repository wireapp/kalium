package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
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
import com.wire.kalium.logic.functional.flatMapRight
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    @DelicateKaliumApi("this function does not get values from cache")
    suspend fun getSelfConversationId(): Either<StorageFailure, ConversationId>
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun observeConversationListDetails(): Flow<List<ConversationDetails>>
    suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>>
    suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun observeById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>>
    suspend fun getConversationById(conversationId: ConversationId): Conversation?
    suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationRecipientsForCalling(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Conversation.ProtocolInfo>
    suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Conversation.Member>>

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

    suspend fun deleteMembersFromEvent(userIDList: List<UserId>, conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun getOneToOneConversationWithOtherUser(otherUserId: UserId): Either<CoreFailure, Conversation>

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

    suspend fun deleteUserFromConversations(userId: UserId): Either<CoreFailure, Unit>

    suspend fun getConversationIdsByUserId(userId: UserId): Either<CoreFailure, List<ConversationId>>
    suspend fun insertConversationFromMigration(conversations: List<Conversation>): Either<CoreFailure, Unit>

}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConversationDataSource internal constructor(
    private val userRepository: UserRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val conversationDAO: ConversationDAO,
    private val conversationApi: ConversationApi,
    private val messageDAO: MessageDAO,
    private val clientDAO: ClientDAO,
    private val clientApi: ClientApi,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val conversationStatusMapper: ConversationStatusMapper = MapperProvider.conversationStatusMapper(),
    private val conversationRoleMapper: ConversationRoleMapper = MapperProvider.conversationRoleMapper(),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
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
        hasEstablishedMLSGroup(groupId).fold({
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

    private suspend fun hasEstablishedMLSGroup(groupID: GroupID): Either<CoreFailure, Boolean> =
        mlsClientProvider.getMLSClient()
            .flatMap {
                wrapMLSRequest {
                    it.conversationExists(idMapper.toCryptoModel(groupID))
                }
            }

    override suspend fun getSelfConversationId(): Either<StorageFailure, ConversationId> =
        wrapStorageRequest { conversationDAO.getSelfConversationId() }
            .map { idMapper.fromDaoModel(it) }

    override suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>> = wrapStorageRequest {
        observeConversationList()
    }

    override suspend fun observeConversationList(): Flow<List<Conversation>> {
        return conversationDAO.getAllConversations().map { it.map(conversationMapper::fromDaoModel) }
    }

    override suspend fun observeConversationListDetails(): Flow<List<ConversationDetails>> =
        conversationDAO.getAllConversationDetails().map { it.map(conversationMapper::fromDaoModelToDetails) }

    /**
     * Gets a flow that allows observing of
     */
    override suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>> =
        conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationID))
            .wrapStorageRequest()
            .mapRight { conversationMapper.fromDaoModelToDetails(it) }
            .flatMapRight { conversationDetails ->
                when (conversationDetails) {
                    is ConversationDetails.OneOne -> observeLastUnreadMessage(conversationID)
                        .map { conversationDetails.copy(lastUnreadMessage = it) }

                    is ConversationDetails.Group -> observeLastUnreadMessage(conversationID)
                        .map { conversationDetails.copy(lastUnreadMessage = it) }

                    else -> flowOf(conversationDetails)
                }
            }
            .distinctUntilChanged()

    private suspend fun observeLastUnreadMessage(conversationId: ConversationId): Flow<Message?> =
        messageDAO.observeLastUnreadMessage(idMapper.toDaoModel(conversationId))
            .map { it?.let { messageMapper.fromEntityToMessage(it) } }
            .distinctUntilChanged()

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

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Conversation.ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(conversationId)).first()?.protocolInfo?.let {
                protocolInfoMapper.fromEntity(it)
            }
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

    override suspend fun deleteUserFromConversations(userId: UserId): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.revokeOneOnOneConversationsWithDeletedUser(idMapper.toDaoModel(userId))
    }

    override suspend fun getConversationIdsByUserId(userId: UserId): Either<CoreFailure, List<ConversationId>> {
        return wrapStorageRequest {
            conversationDAO.getConversationIdsByUserId(idMapper.toDaoModel(userId))
        }
            .map { it.map { conversationIdEntity -> idMapper.fromDaoModel(conversationIdEntity) } }
    }

    override suspend fun insertConversationFromMigration(conversations: List<Conversation>): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationEntities = conversations.map { conversation ->
                conversationMapper.toDaoModel(conversation)
            }
            conversationDAO.insertConversations(conversationEntities)
        }
    }

    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
    }
}
