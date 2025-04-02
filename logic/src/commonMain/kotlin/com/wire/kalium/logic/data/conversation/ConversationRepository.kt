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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.mapToRightOr
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation.ProtocolInfo.MLSCapable.GroupState
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.conversation.mls.EpochChangesData
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddPermissionResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationMetaDataDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.draft.MessageDraftDAO
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlinx.serialization.builtins.SetSerializer

@Suppress("TooManyFunctions")
interface ConversationRepository {
    val extensions: ConversationRepositoryExtensions

    // region Get/Observe by id

    suspend fun observeConversationById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>>
    suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>>
    suspend fun getConversationById(conversationId: ConversationId): Either<StorageFailure, Conversation>
    suspend fun getConversationTypeById(conversationId: ConversationId): Either<StorageFailure, Conversation.Type>

    // TODO: rename it to getChannelAddUserPermission
    suspend fun getChannelAddPermission(conversationId: ConversationId): Either<StorageFailure, ChannelAddPermission>
    // endregion

    @DelicateKaliumApi("This function does not get values from cache")
    suspend fun getProteusSelfConversationId(): Either<StorageFailure, ConversationId>

    @DelicateKaliumApi("This function does not get values from cache")
    suspend fun getMLSSelfConversationId(): Either<StorageFailure, ConversationId>

    suspend fun fetchConversations(): Either<CoreFailure, Unit>

    // TODO make all functions to have only logic models
    suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: TeamId?,
        originatedFromEvent: Boolean = false,
        invalidateMembers: Boolean = false
    ): Either<CoreFailure, Unit>

    /**
     * Creates a conversation from a new event
     *
     * @param conversation from event
     * @param selfUserTeamId - self user team id if team user
     * @return Either<CoreFailure, Boolean> - true if the conversation was created, false if it was already present
     */
    suspend fun persistConversation(
        conversation: ConversationResponse,
        selfUserTeamId: String?,
        originatedFromEvent: Boolean = false
    ): Either<CoreFailure, Boolean>

    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun observeConversationListDetails(
        fromArchive: Boolean,
        conversationFilter: ConversationFilter = ConversationFilter.All
    ): Flow<List<ConversationDetails>>

    suspend fun observeConversationListDetailsWithEvents(
        fromArchive: Boolean = false,
        conversationFilter: ConversationFilter = ConversationFilter.All
    ): Flow<List<ConversationDetailsWithEvents>>

    /**
     * Gets conversations based on [type] and [protocol].
     * [Conversation.Type.Group.Channel] and [Conversation.Type.Group.Regular] are treated the same, as both are Groups
     */
    suspend fun getConversationIds(
        type: Conversation.Type,
        protocol: Conversation.Protocol,
        teamId: TeamId? = null
    ): Either<StorageFailure, List<QualifiedID>>

    suspend fun fetchMlsOneToOneConversation(userId: UserId): Either<CoreFailure, Conversation>
    suspend fun getTeamConversationIdsReadyToCompleteMigration(teamId: TeamId): Either<StorageFailure, List<QualifiedID>>
    suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun fetchSentConnectionConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getRecipientById(conversationId: ConversationId, userIDList: List<UserId>): Either<StorageFailure, List<Recipient>>
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

    suspend fun observeOneToOneConversationWithOtherUser(otherUserId: UserId): Flow<Either<CoreFailure, Conversation>>

    suspend fun getOneOnOneConversationsWithOtherUser(
        otherUserId: UserId,
        protocol: Conversation.Protocol
    ): Either<StorageFailure, List<ConversationId>>

    suspend fun updateMutedStatusLocally(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<StorageFailure, Unit>

    suspend fun updateMutedStatusRemotely(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<NetworkFailure, Unit>

    suspend fun updateArchivedStatusLocally(
        conversationId: ConversationId,
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    ): Either<StorageFailure, Unit>

    suspend fun updateArchivedStatusRemotely(
        conversationId: ConversationId,
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    ): Either<NetworkFailure, Unit>

    suspend fun getConversationsByGroupState(
        groupState: GroupState
    ): Either<StorageFailure, List<Conversation>>

    suspend fun updateConversationGroupState(groupID: GroupID, groupState: GroupState): Either<StorageFailure, Unit>
    suspend fun updateConversationNotificationDate(qualifiedID: QualifiedID): Either<StorageFailure, Unit>
    suspend fun updateAllConversationsNotificationDate(): Either<StorageFailure, Unit>
    suspend fun updateConversationModifiedDate(qualifiedID: QualifiedID, date: Instant): Either<StorageFailure, Unit>
    suspend fun updateConversationReadDate(qualifiedID: QualifiedID, date: Instant): Either<StorageFailure, Unit>
    suspend fun updateAccessInfo(
        conversationID: ConversationId,
        access: Set<Conversation.Access>,
        accessRole: Set<Conversation.AccessRole>
    ): Either<CoreFailure, Unit>

    suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        role: Conversation.Member.Role
    ): Either<CoreFailure, Unit>

    suspend fun updateChannelAddPermission(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermission
    ): Either<CoreFailure, Unit>

    suspend fun deleteConversation(conversationId: ConversationId): Either<CoreFailure, Unit>

    suspend fun updateChannelAddPermissionLocally(
        conversationId: ConversationId,
        channelAddPermission: Conversation.ChannelAddPermission
    ): Either<CoreFailure, Unit>

    suspend fun updateChannelAddPermissionRemotely(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermission
    ): Either<NetworkFailure, UpdateChannelAddPermissionResponse>

    /**
     * Deletes all conversation messages
     */
    suspend fun clearContent(conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun observeIsUserMember(conversationId: ConversationId, userId: UserId): Flow<Either<CoreFailure, Boolean>>
    suspend fun whoDeletedMe(conversationId: ConversationId): Either<CoreFailure, UserId?>
    suspend fun getConversationsByUserId(userId: UserId): Either<CoreFailure, List<Conversation>>
    suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit>
    suspend fun changeConversationName(
        conversationId: ConversationId,
        conversationName: String
    ): Either<CoreFailure, ConversationRenameResponse>

    suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ): Either<CoreFailure, Unit>

    suspend fun getConversationUnreadEventsCount(conversationId: ConversationId): Either<StorageFailure, Long>
    suspend fun updateUserSelfDeletionTimer(conversationId: ConversationId, selfDeletionTimer: SelfDeletionTimer): Either<CoreFailure, Unit>
    suspend fun syncConversationsWithoutMetadata(): Either<CoreFailure, Unit>
    suspend fun isInformedAboutDegradedMLSVerification(conversationId: ConversationId): Either<StorageFailure, Boolean>
    suspend fun setInformedAboutDegradedMLSVerificationFlag(
        conversationId: ConversationId,
        isInformed: Boolean
    ): Either<StorageFailure, Unit>

    suspend fun getGroupConversationsWithMembersWithBothDomains(
        firstDomain: String,
        secondDomain: String
    ): Either<CoreFailure, GroupConversationMembers>

    suspend fun getOneOnOneConversationsWithFederatedMembers(
        domain: String
    ): Either<CoreFailure, OneOnOneMembers>

    suspend fun updateMlsVerificationStatus(
        verificationStatus: Conversation.VerificationStatus,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit>

    suspend fun getConversationByMLSGroupId(mlsGroupId: GroupID): Either<CoreFailure, Conversation>

    suspend fun observeUnreadArchivedConversationsCount(): Flow<Long>

    suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit>

    /**
     * Update a conversation's protocol remotely.
     *
     *  This also fetches the newly assigned `groupID` from the backend, if this operation fails the whole
     *  operation is cancelled and protocol change is not persisted.
     *
     * @return **true** if the protocol was changed or **false** if the protocol was unchanged.
     */
    suspend fun updateProtocolRemotely(conversationId: ConversationId, protocol: Conversation.Protocol): Either<CoreFailure, Boolean>

    /**
     * Update a conversation's protocol locally.
     *
     * This also fetches the newly assigned `groupID` from the backend, if this operation fails the whole
     * operation is cancelled and protocol change is not persisted.
     *
     * @return **true** if the protocol was changed or **false** if the protocol was unchanged.
     */
    suspend fun updateProtocolLocally(conversationId: ConversationId, protocol: Conversation.Protocol): Either<CoreFailure, Boolean>

    suspend fun observeDegradedConversationNotified(conversationId: QualifiedID): Flow<Boolean>
    suspend fun setDegradedConversationNotifiedFlag(
        conversationId: QualifiedID,
        value: Boolean
    ): Either<CoreFailure, Unit>

    suspend fun updateLegalHoldStatus(
        conversationId: ConversationId,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): Either<CoreFailure, Boolean>

    suspend fun setLegalHoldStatusChangeNotified(conversationId: ConversationId): Either<CoreFailure, Boolean>

    suspend fun observeLegalHoldStatus(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation.LegalHoldStatus>>

    suspend fun observeLegalHoldStatusChangeNotified(conversationId: ConversationId): Flow<Either<StorageFailure, Boolean>>

    suspend fun getGroupStatusMembersNamesAndHandles(groupID: GroupID): Either<StorageFailure, EpochChangesData>
    suspend fun selectMembersNameAndHandle(conversationId: ConversationId): Either<StorageFailure, Map<UserId, NameAndHandle>>
    suspend fun addConversationToDeleteQueue(conversationId: ConversationId)
    suspend fun removeConversationFromDeleteQueue(conversationId: ConversationId)
    suspend fun getConversationsDeleteQueue(): List<ConversationId>
    suspend fun observeOneToOneConversationDetailsWithOtherUser(
        otherUserId: UserId
    ): Flow<Either<StorageFailure, ConversationDetails.OneOne>>
}

@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
internal class ConversationDataSource internal constructor(
    private val selfUserId: UserId,
    private val mlsClientProvider: MLSClientProvider,
    private val selfTeamIdProvider: SelfTeamIdProvider,
    private val conversationDAO: ConversationDAO,
    private val memberDAO: MemberDAO,
    private val conversationApi: ConversationApi,
    private val messageDAO: MessageDAO,
    private val messageDraftDAO: MessageDraftDAO,
    private val clientDAO: ClientDAO,
    private val clientApi: ClientApi,
    private val conversationMetaDataDAO: ConversationMetaDataDAO,
    private val metadataDAO: MetadataDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val memberMapper: MemberMapper = MapperProvider.memberMapper(),
    private val conversationStatusMapper: ConversationStatusMapper = MapperProvider.conversationStatusMapper(),
    private val conversationRoleMapper: ConversationRoleMapper = MapperProvider.conversationRoleMapper(),
    private val protocolInfoMapper: ProtocolInfoMapper = MapperProvider.protocolInfoMapper(),
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ConversationRepository {
    override val extensions: ConversationRepositoryExtensions =
        ConversationRepositoryExtensionsImpl(conversationDAO, conversationMapper)

    // region Get/Observe by id

    override suspend fun observeConversationById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>> =
        conversationDAO.observeConversationById(conversationId.toDao()).filterNotNull()
            .map(conversationMapper::fromDaoModel)
            .wrapStorageRequest()

    override suspend fun getConversationById(conversationId: ConversationId): Either<StorageFailure, Conversation> = wrapStorageRequest {
        conversationDAO.getConversationById(conversationId.toDao())?.let {
            conversationMapper.fromDaoModel(it)
        }
    }

    override suspend fun getConversationTypeById(conversationId: ConversationId): Either<StorageFailure, Conversation.Type> =
        wrapStorageRequest {
            conversationDAO.getConversationTypeById(conversationId.toDao())?.let {
                val isChannel = conversationDAO.isAChannel(conversationId.toDao())
                conversationMapper.fromConversationEntityType(it, isChannel)
            }
        }

    override suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>> =
        conversationDAO.observeConversationDetailsById(conversationID.toDao())
            .wrapStorageRequest()
            .map { eitherConversationView ->
                eitherConversationView.flatMap {
                    try {
                        Either.Right(conversationMapper.fromDaoModelToDetails(it))
                    } catch (error: IllegalArgumentException) {
                        kaliumLogger.e("require field in conversation Details", error)
                        Either.Left(StorageFailure.DataNotFound)
                    }
                }
            }
            .distinctUntilChanged()

    // endregion

    override suspend fun fetchConversations(): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS).d("Fetching conversations")
        return fetchAllConversationsFromAPI()
    }

    private suspend fun fetchAllConversationsFromAPI(): Either<NetworkFailure, Unit> {
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
                            .d("Handling ${conversations.conversationsFailed.size} conversations failed")
                        persistIncompleteConversations(conversations.conversationsFailed)
                    }
                    if (conversations.conversationsNotFound.isNotEmpty()) {
                        kaliumLogger.withFeatureId(CONVERSATIONS)
                            .d("Skipping ${conversations.conversationsNotFound.size} conversations not found")
                    }
                    persistConversations(
                        conversations = conversations.conversationsFound,
                        selfUserTeamId = selfTeamIdProvider().getOrNull(),
                        invalidateMembers = true
                    )

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

    override suspend fun persistConversation(
        conversation: ConversationResponse,
        selfUserTeamId: String?,
        originatedFromEvent: Boolean
    ): Either<CoreFailure, Boolean> = wrapStorageRequest {
        val existingConversation = conversationDAO.getConversationById(conversation.id.toDao())
        val isNewConversation = existingConversation?.let { conversationEntity ->
            (conversationEntity.protocolInfo as? ConversationEntity.ProtocolInfo.MLSCapable)?.groupState?.let {
                it != ConversationEntity.GroupState.ESTABLISHED
            } ?: false
        } ?: true
        if (isNewConversation) {
            val mlsGroupState = conversation.groupId?.let { mlsGroupState(idMapper.fromGroupIDEntity(it), originatedFromEvent) }
            conversationDAO.insertConversation(
                conversationMapper.fromApiModelToDaoModel(
                    conversation,
                    mlsGroupState = mlsGroupState,
                    selfTeamIdProvider().getOrNull(),
                )
            )
            memberDAO.insertMembersWithQualifiedId(
                memberMapper.fromApiModelToDaoModel(conversation.members), idMapper.fromApiToDao(conversation.id)
            )
        }
        isNewConversation
    }

    override suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: TeamId?,
        originatedFromEvent: Boolean,
        invalidateMembers: Boolean
    ) = wrapStorageRequest {
        val conversationEntities = conversations
            .map { conversationResponse ->
                val mlsGroupState = conversationResponse.groupId?.let {
                    mlsGroupState(idMapper.fromGroupIDEntity(it), originatedFromEvent)
                }
                conversationMapper.fromApiModelToDaoModel(
                    conversationResponse,
                    mlsGroupState = mlsGroupState,
                    selfTeamIdProvider().getOrNull(),
                )
            }
        conversationDAO.insertConversations(conversationEntities)
        conversations.forEach { conversationsResponse ->
            // do the cleanup of members from conversation in case when self user rejoined conversation
            // and may not received any member remove or leave events
            if (invalidateMembers && conversationsResponse.toConversationType(selfUserTeamId) == ConversationEntity.Type.GROUP) {
                memberDAO.updateFullMemberList(
                    memberMapper.fromApiModelToDaoModel(conversationsResponse.members),
                    idMapper.fromApiToDao(conversationsResponse.id)
                )
            } else {
                memberDAO.insertMembersWithQualifiedId(
                    memberMapper.fromApiModelToDaoModel(conversationsResponse.members),
                    idMapper.fromApiToDao(conversationsResponse.id)
                )
            }
        }
    }

    private suspend fun mlsGroupState(
        groupId: GroupID,
        originatedFromEvent: Boolean = false
    ): ConversationEntity.GroupState = hasEstablishedMLSGroup(groupId)
        .fold({ failure ->
            kaliumLogger.withFeatureId(CONVERSATIONS)
                .w("Error checking MLS group state, setting to ${ConversationEntity.GroupState.PENDING_JOIN}")
            ConversationEntity.GroupState.PENDING_JOIN
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

    @DelicateKaliumApi("This function does not get values from cache")
    override suspend fun getProteusSelfConversationId(): Either<StorageFailure, ConversationId> =
        wrapStorageRequest { conversationDAO.getSelfConversationId(ConversationEntity.Protocol.PROTEUS) }
            .map { it.toModel() }

    @DelicateKaliumApi("This function does not get values from cache")
    override suspend fun getMLSSelfConversationId(): Either<StorageFailure, ConversationId> =
        wrapStorageRequest { conversationDAO.getSelfConversationId(ConversationEntity.Protocol.MLS) }
            .map { it.toModel() }

    override suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>> = wrapStorageRequest {
        observeConversationList()
    }

    override suspend fun observeConversationList(): Flow<List<Conversation>> {
        return conversationDAO.getAllConversations().map { it.map(conversationMapper::fromDaoModel) }
    }

    override suspend fun observeConversationListDetails(
        fromArchive: Boolean,
        conversationFilter: ConversationFilter
    ): Flow<List<ConversationDetails>> =
        conversationDAO.getAllConversationDetails(fromArchive, conversationFilter.toDao()).map { conversationViewEntityList ->
            conversationViewEntityList.map { conversationViewEntity -> conversationMapper.fromDaoModelToDetails(conversationViewEntity) }
        }

    override suspend fun observeConversationListDetailsWithEvents(
        fromArchive: Boolean,
        conversationFilter: ConversationFilter
    ): Flow<List<ConversationDetailsWithEvents>> =
        combine(
            conversationDAO.getAllConversationDetails(fromArchive, conversationFilter.toDao()),
            if (fromArchive) flowOf(listOf()) else messageDAO.observeLastMessages(),
            messageDAO.observeConversationsUnreadEvents(),
            messageDraftDAO.observeMessageDrafts()
        ) { conversationList, lastMessageList, unreadEvents, drafts ->
            val lastMessageMap = lastMessageList.associateBy { it.conversationId }
            val messageDraftMap = drafts.filter { it.text.isNotBlank() }.associateBy { it.conversationId }
            val unreadEventsMap = unreadEvents.associateBy { it.conversationId }

            conversationList.map { conversation ->
                conversationMapper.fromDaoModelToDetailsWithEvents(
                    ConversationDetailsWithEventsEntity(
                        conversationViewEntity = conversation,
                        lastMessage = lastMessageMap[conversation.id],
                        messageDraft = messageDraftMap[conversation.id],
                        unreadEvents = unreadEventsMap[conversation.id] ?: ConversationUnreadEventEntity(conversation.id, mapOf()),
                    )
                )
            }
        }

    override suspend fun fetchMlsOneToOneConversation(userId: UserId): Either<CoreFailure, Conversation> =
        wrapApiRequest {
            conversationApi.fetchMlsOneToOneConversation(userId.toApi())
        }.map { conversationResponse ->
            // question: do we need to do this? since it's one on one!
            addOtherMemberIfMissing(conversationResponse, userId)
        }.flatMap { conversationResponse ->
            val selfUserTeamId = selfTeamIdProvider().getOrNull()
            persistConversations(
                conversations = listOf(conversationResponse),
                selfUserTeamId = selfUserTeamId
            ).map { conversationResponse }
        }.flatMap { response ->
            this.getConversationById(response.id.toModel()).map {
                it.copy(mlsPublicKeys = conversationMapper.fromApiModel(response.publicKeys))
            }
        }

    private fun addOtherMemberIfMissing(
        conversationResponse: ConversationResponse,
        otherMemberId: UserId
    ): ConversationResponse {
        val currentOtherMembers = conversationResponse.members.otherMembers
        val hasOtherUser = currentOtherMembers.any { it.id == otherMemberId.toApi() }
        val otherMembers = if (hasOtherUser) {
            currentOtherMembers
        } else {
            listOf(
                ConversationMemberDTO.Other(
                    id = otherMemberId.toApi(),
                    conversationRole = "",
                    service = null
                )
            )
        }
        return conversationResponse.copy(
            members = conversationResponse.members.copy(
                otherMembers = otherMembers
            )
        )
    }

    override suspend fun getConversationIds(
        type: Conversation.Type,
        protocol: Conversation.Protocol,
        teamId: TeamId?
    ): Either<StorageFailure, List<QualifiedID>> =
        wrapStorageRequest {
            conversationDAO.getConversationIds(type.toDAO(), protocol.toDao(), teamId?.value)
                .map { it.toModel() }
        }

    override suspend fun getTeamConversationIdsReadyToCompleteMigration(teamId: TeamId): Either<StorageFailure, List<QualifiedID>> =
        wrapStorageRequest {
            conversationDAO.getTeamConversationIdsReadyToCompleteMigration(teamId.value)
                .map { it.toModel() }
        }

    override suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            conversationApi.fetchConversationDetails(conversationID.toApi())
        }.flatMap {
            val selfUserTeamId = selfTeamIdProvider().getOrNull()
            persistConversations(listOf(it), selfUserTeamId, invalidateMembers = true)
        }
    }

    // TODO: this function should/might be need to be removed when BE implements https://wearezeta.atlassian.net/browse/WPB-3560
    override suspend fun fetchSentConnectionConversation(conversationID: ConversationId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            conversationApi.fetchConversationDetails(conversationID.toApi())
        }.flatMap {
            val selfUserTeamId = selfTeamIdProvider().getOrNull()
            val conversation = it.copy(
                type = ConversationResponse.Type.WAIT_FOR_CONNECTION,
            )
            persistConversations(listOf(conversation), selfUserTeamId, invalidateMembers = true)
        }
    }

    override suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.getConversationDetailsById(QualifiedIDEntity(conversationID.value, conversationID.domain))
    }.run {
        if (isLeft()) {
            fetchConversation(conversationID)
        } else {
            Either.Right(Unit)
        }
    }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Conversation.ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.getConversationProtocolInfo(conversationId.toDao())?.let {
                protocolInfoMapper.fromEntity(it)
            }
        }

    override suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Conversation.Member>> =
        memberDAO.observeConversationMembers(conversationID.toDao()).map { members ->
            members.map(memberMapper::fromDaoModel)
        }

    override suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        memberDAO.observeConversationMembers(conversationId.toDao()).first().map { it.user.toModel() }
    }

    override suspend fun persistMembers(
        members: List<Conversation.Member>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        memberDAO.insertMembersWithQualifiedId(
            members.map(memberMapper::toDaoModel), conversationID.toDao()
        )
    }

    override suspend fun updateMemberFromEvent(member: Conversation.Member, conversationID: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            memberDAO.updateMemberRole(member.id.toDao(), conversationID.toDao(), conversationRoleMapper.toDAO(member.role))
        }

    override suspend fun getConversationsByGroupState(
        groupState: GroupState
    ): Either<StorageFailure, List<Conversation>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByGroupState(conversationMapper.toDAOGroupState(groupState))
                .map(conversationMapper::fromDaoModel)
        }

    override suspend fun updateConversationGroupState(
        groupID: GroupID,
        groupState: GroupState
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateConversationGroupState(groupState.toDao(), groupID.value)
        }

    override suspend fun updateConversationNotificationDate(
        qualifiedID: QualifiedID
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateConversationNotificationDate(qualifiedID.toDao())
        }

    override suspend fun updateAllConversationsNotificationDate(): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateAllConversationsNotificationDate() }

    override suspend fun updateConversationModifiedDate(
        qualifiedID: QualifiedID,
        date: Instant
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationModifiedDate(qualifiedID.toDao(), date) }

    override suspend fun updateAccessInfo(
        conversationID: ConversationId,
        access: Set<Conversation.Access>,
        accessRole: Set<Conversation.AccessRole>
    ): Either<CoreFailure, Unit> =
        UpdateConversationAccessRequest(
            access.map { conversationMapper.toApiModel(it) }.toSet(),
            accessRole.map { conversationMapper.toApiModel(it) }.toSet()
        ).let { updateConversationAccessRequest ->
            wrapApiRequest {
                conversationApi.updateAccess(conversationID.toApi(), updateConversationAccessRequest)
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
                            response.event.qualifiedConversation.toDao(),
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
    override suspend fun updateConversationReadDate(qualifiedID: QualifiedID, date: Instant): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationReadDate(qualifiedID.toDao(), date) }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        wrapStorageRequest {
            clientDAO.conversationRecipient(conversationId.toDao())
        }.map(memberMapper::fromMapOfClientsEntityToRecipients)

    override suspend fun getRecipientById(
        conversationId: ConversationId,
        userIDList: List<UserId>
    ): Either<StorageFailure, List<Recipient>> = wrapStorageRequest {
        clientDAO.recipientsIfTheyArePartOfConversation(conversationId.toDao(), userIDList.map(UserId::toDao).toSet())
    }.map(memberMapper::fromMapOfClientsEntityToRecipients)

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipientsForCalling(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        getConversationMembers(conversationId).map { it.map { userId -> userId.toApi() } }.flatMap {
            wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
        }

    override suspend fun observeOneToOneConversationWithOtherUser(
        otherUserId: UserId
    ): Flow<Either<StorageFailure, Conversation>> {
        return conversationDAO.observeOneOnOneConversationWithOtherUser(otherUserId.toDao())
            .wrapStorageRequest()
            .mapRight { conversationMapper.fromDaoModel(it) }
    }

    override suspend fun observeOneToOneConversationDetailsWithOtherUser(
        otherUserId: UserId
    ): Flow<Either<StorageFailure, ConversationDetails.OneOne>> {
        return conversationDAO.observeOneOnOneConversationDetailsWithOtherUser(otherUserId.toDao())
            .map { it?.let { conversationMapper.fromDaoModelToDetails(it) as? ConversationDetails.OneOne } }
            .wrapStorageRequest()
    }

    override suspend fun getOneOnOneConversationsWithOtherUser(
        otherUserId: UserId,
        protocol: Conversation.Protocol
    ): Either<StorageFailure, List<ConversationId>> = wrapStorageRequest {
        conversationDAO.getOneOnOneConversationIdsWithOtherUser(otherUserId.toDao(), protocol.toDao()).map { it.toModel() }
    }

    override suspend fun updateMutedStatusLocally(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationMutedStatus(
            conversationId = conversationId.toDao(),
            mutedStatus = conversationStatusMapper.toMutedStatusDaoModel(mutedStatus),
            mutedStatusTimestamp = mutedStatusTimestamp
        )
    }

    override suspend fun updateMutedStatusRemotely(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<NetworkFailure, Unit> = wrapApiRequest {
        conversationApi.updateConversationMemberState(
            memberUpdateRequest = conversationStatusMapper.toMutedStatusApiModel(mutedStatus, mutedStatusTimestamp),
            conversationId = conversationId.toApi()
        )
    }

    override suspend fun updateArchivedStatusLocally(
        conversationId: ConversationId,
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateConversationArchivedStatus(
            conversationId = conversationId.toDao(),
            isArchived = isArchived,
            archivedStatusTimestamp = archivedStatusTimestamp
        )
    }

    override suspend fun updateArchivedStatusRemotely(
        conversationId: ConversationId,
        isArchived: Boolean,
        archivedStatusTimestamp: Long
    ): Either<NetworkFailure, Unit> = wrapApiRequest {
        conversationApi.updateConversationMemberState(
            memberUpdateRequest = conversationStatusMapper.toArchivedStatusApiModel(isArchived, archivedStatusTimestamp),
            conversationId = conversationId.toApi()
        )
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
            conversationId = conversationId.toApi(),
            userId = userId.toApi(),
            conversationMemberRoleDTO = ConversationMemberRoleDTO(conversationRole = conversationRoleMapper.toApi(role))
        )
    }.flatMap {
        wrapStorageRequest {
            memberDAO.updateConversationMemberRole(
                conversationId = conversationId.toDao(),
                userId = userId.toDao(),
                role = conversationRoleMapper.toDAO(role)
            )
        }
    }

    override suspend fun deleteConversation(conversationId: ConversationId) =
        getConversationProtocolInfo(conversationId).flatMap {
            when (it) {
                is Conversation.ProtocolInfo.MLSCapable ->
                    mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                        wrapMLSRequest {
                            mlsClient.wipeConversation(it.groupId.toCrypto())
                        }
                    }.flatMap {
                        wrapStorageRequest {
                            conversationDAO.deleteConversationByQualifiedID(conversationId.toDao())
                        }
                    }

                is Conversation.ProtocolInfo.Proteus -> wrapStorageRequest {
                    conversationDAO.deleteConversationByQualifiedID(conversationId.toDao())
                }
            }
        }

    override suspend fun clearContent(conversationId: ConversationId): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.clearContent(conversationId.toDao())
        }

    override suspend fun observeIsUserMember(conversationId: ConversationId, userId: UserId): Flow<Either<CoreFailure, Boolean>> =
        memberDAO.observeIsUserMember(conversationId.toDao(), userId.toDao())
            .wrapStorageRequest()

    override suspend fun whoDeletedMe(conversationId: ConversationId): Either<CoreFailure, UserId?> = wrapStorageRequest {
        conversationDAO.whoDeletedMeInConversation(
            conversationId.toDao(),
            idMapper.toStringDaoModel(selfUserId)
        )?.toModel()
    }

    override suspend fun getConversationsByUserId(userId: UserId): Either<CoreFailure, List<Conversation>> {
        return wrapStorageRequest { conversationDAO.getConversationsByUserId(userId.toDao()) }
            .map { it.map { entity -> conversationMapper.fromDaoModel(entity) } }
    }

    override suspend fun insertConversations(conversations: List<Conversation>): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            val conversationEntities = conversations.map { conversation ->
                conversationMapper.fromMigrationModel(conversation)
            }
            conversationDAO.insertConversations(conversationEntities)
        }
    }

    override suspend fun changeConversationName(
        conversationId: ConversationId,
        conversationName: String
    ): Either<CoreFailure, ConversationRenameResponse> = wrapApiRequest {
        conversationApi.updateConversationName(conversationId.toApi(), conversationName)
    }

    override suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: Conversation.ReceiptMode
    ): Either<CoreFailure, Unit> = ConversationReceiptModeDTO(
        receiptMode = receiptModeMapper.fromModelToApi(receiptMode)
    ).let { conversationReceiptModeDTO ->
        wrapApiRequest {
            conversationApi.updateReceiptMode(
                conversationId = conversationId.toApi(),
                receiptMode = conversationReceiptModeDTO
            )
        }
    }.flatMap { response ->
        when (response) {
            UpdateConversationReceiptModeResponse.ReceiptModeUnchanged -> {
                // no need to update conversation
                Either.Right(Unit)
            }

            is UpdateConversationReceiptModeResponse.ReceiptModeUpdated -> {
                wrapStorageRequest {
                    conversationDAO.updateConversationReceiptMode(
                        conversationID = response.event.qualifiedConversation.toDao(),
                        receiptMode = receiptModeMapper.fromApiToDaoModel(response.event.data.receiptMode)
                    )
                }
            }
        }
    }

    override suspend fun getConversationUnreadEventsCount(conversationId: ConversationId): Either<StorageFailure, Long> =
        wrapStorageRequest { messageDAO.getConversationUnreadEventsCount(conversationId.toDao()) }

    override suspend fun updateUserSelfDeletionTimer(
        conversationId: ConversationId,
        selfDeletionTimer: SelfDeletionTimer
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateUserMessageTimer(
            conversationId = conversationId.toDao(),
            messageTimer = selfDeletionTimer.duration?.inWholeMilliseconds
        )
    }

    override suspend fun syncConversationsWithoutMetadata(): Either<CoreFailure, Unit> = wrapStorageRequest {
        val conversationsWithoutMetadata = conversationDAO.getConversationsWithoutMetadata()
        if (conversationsWithoutMetadata.isNotEmpty()) {
            kaliumLogger.d("Numbers of conversations to refresh: ${conversationsWithoutMetadata.size}")
            val conversationsWithoutMetadataIds = conversationsWithoutMetadata.map { it.toApi() }
            wrapApiRequest {
                conversationApi.fetchConversationsListDetails(conversationsWithoutMetadataIds)
            }.onSuccess {
                persistConversations(it.conversationsFound, null)
            }
        }
    }

    override suspend fun updateChannelAddPermissionLocally(
        conversationId: ConversationId,
        channelAddPermission: Conversation.ChannelAddPermission
    ): Either<CoreFailure, Unit> {
        TODO("Not yet implemented")
    }

    override suspend fun updateChannelAddPermissionRemotely(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermission
    ): Either<NetworkFailure, UpdateChannelAddPermissionResponse> = wrapApiRequest {
        conversationApi.updateChannelAddPermission(
            conversationId = conversationId.toApi(),
            channelAddPermission = channelAddPermission.toApi()
        )
    }

    override suspend fun isInformedAboutDegradedMLSVerification(conversationId: ConversationId): Either<StorageFailure, Boolean> =
        wrapStorageRequest {
            conversationMetaDataDAO.isInformedAboutDegradedMLSVerification(conversationId.toDao())
        }

    override suspend fun setInformedAboutDegradedMLSVerificationFlag(
        conversationId: ConversationId,
        isInformed: Boolean
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            conversationMetaDataDAO.setInformedAboutDegradedMLSVerificationFlag(conversationId.toDao(), isInformed)
        }

    override suspend fun getGroupConversationsWithMembersWithBothDomains(
        firstDomain: String,
        secondDomain: String
    ): Either<CoreFailure, GroupConversationMembers> = wrapStorageRequest {
        memberDAO.getGroupConversationWithUserIdsWithBothDomains(firstDomain, secondDomain)
            .mapKeys { it.key.toModel() }
            .mapValues { it.value.map { userId -> userId.toModel() } }
    }

    override suspend fun getOneOnOneConversationsWithFederatedMembers(
        domain: String
    ): Either<CoreFailure, OneOnOneMembers> = wrapStorageRequest {
        memberDAO.getOneOneConversationWithFederatedMembers(domain)
            .mapKeys { it.key.toModel() }
            .mapValues { it.value.toModel() }
    }

    override suspend fun updateMlsVerificationStatus(
        verificationStatus: Conversation.VerificationStatus,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateMlsVerificationStatus(
                conversationMapper.verificationStatusToEntity(verificationStatus),
                conversationID.toDao()
            )
        }

    override suspend fun getConversationByMLSGroupId(mlsGroupId: GroupID): Either<CoreFailure, Conversation> =
        wrapStorageRequest { conversationDAO.getConversationByGroupID(mlsGroupId.value) }
            .map { conversationMapper.fromDaoModel(it) }

    override suspend fun observeUnreadArchivedConversationsCount(): Flow<Long> =
        conversationDAO.observeUnreadArchivedConversationsCount()
            .wrapStorageRequest()
            .mapToRightOr(0L)

    override suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Either<CoreFailure, Unit> = wrapApiRequest {
        conversationApi.sendTypingIndicatorNotification(conversationId.toApi(), typingStatus.toStatusDto())
    }

    private suspend fun persistIncompleteConversations(
        conversationsFailed: List<NetworkQualifiedId>
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            if (conversationsFailed.isNotEmpty()) {
                conversationDAO.insertConversations(conversationsFailed.map { conversationId ->
                    conversationMapper.fromFailedGroupConversationToEntity(conversationId)
                })
            }
        }
    }

    override suspend fun updateProtocolRemotely(
        conversationId: ConversationId,
        protocol: Conversation.Protocol
    ): Either<CoreFailure, Boolean> =
        wrapApiRequest {
            conversationApi.updateProtocol(conversationId.toApi(), protocol.toApi())
        }.flatMap {
            updateProtocolLocally(conversationId, protocol)
        }

    override suspend fun updateProtocolLocally(
        conversationId: ConversationId,
        protocol: Conversation.Protocol
    ): Either<CoreFailure, Boolean> =
        wrapApiRequest {
            conversationApi.fetchConversationDetails(conversationId.toApi())
        }.flatMap { conversationResponse ->
            wrapStorageRequest {
                conversationDAO.updateConversationProtocolAndCipherSuite(
                    conversationId = conversationId.toDao(),
                    groupID = conversationResponse.groupId,
                    protocol = protocol.toDao(),
                    cipherSuite = ConversationEntity.CipherSuite.fromTag(conversationResponse.mlsCipherSuiteTag)
                )
            }.flatMap { updated ->
                if (updated) {
                    return@flatMap true.right()
                }
                val selfUserTeamId = selfTeamIdProvider().getOrNull()
                persistConversations(listOf(conversationResponse), selfUserTeamId, invalidateMembers = true)
                    .map { true }
            }
        }

    override suspend fun setDegradedConversationNotifiedFlag(
        conversationId: QualifiedID,
        value: Boolean
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateDegradedConversationNotifiedFlag(conversationId.toDao(), value)
        }

    override suspend fun observeDegradedConversationNotified(conversationId: QualifiedID): Flow<Boolean> =
        conversationDAO.observeDegradedConversationNotified(conversationId.toDao())
            .wrapStorageRequest()
            .mapToRightOr(true)

    override suspend fun updateLegalHoldStatus(
        conversationId: ConversationId,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): Either<CoreFailure, Boolean> {
        val legalHoldStatusEntity = conversationMapper.legalHoldStatusToEntity(legalHoldStatus)
        return wrapStorageRequest {
            conversationId.toDao().let { conversationIdEntity ->
                conversationDAO.updateLegalHoldStatus(
                    conversationId = conversationIdEntity,
                    legalHoldStatus = legalHoldStatusEntity
                ).also { legalHoldUpdated ->
                    if (legalHoldUpdated) {
                        conversationDAO.updateLegalHoldStatusChangeNotified(conversationId = conversationIdEntity, notified = false)
                    }
                }
            }
        }
    }

    override suspend fun setLegalHoldStatusChangeNotified(conversationId: ConversationId): Either<CoreFailure, Boolean> =
        wrapStorageRequest {
            conversationDAO.updateLegalHoldStatusChangeNotified(conversationId = conversationId.toDao(), notified = true)
        }

    override suspend fun observeLegalHoldStatus(conversationId: ConversationId) =
        conversationDAO.observeLegalHoldStatus(conversationId.toDao())
            .map { conversationMapper.legalHoldStatusFromEntity(it) }
            .wrapStorageRequest()
            .distinctUntilChanged()

    override suspend fun observeLegalHoldStatusChangeNotified(conversationId: ConversationId): Flow<Either<StorageFailure, Boolean>> =
        conversationDAO.observeLegalHoldStatusChangeNotified(conversationId.toDao())
            .wrapStorageRequest()
            .distinctUntilChanged()

    override suspend fun getGroupStatusMembersNamesAndHandles(groupID: GroupID): Either<StorageFailure, EpochChangesData> =
        wrapStorageRequest {
            conversationDAO.selectGroupStatusMembersNamesAndHandles(groupID.value)
        }.map { EpochChangesData.fromEntity(it) }

    override suspend fun selectMembersNameAndHandle(conversationId: ConversationId): Either<StorageFailure, Map<UserId, NameAndHandle>> =
        wrapStorageRequest {
            memberDAO.selectMembersNameAndHandle(conversationId.toDao())
                .mapValues { NameAndHandle.fromEntity(it.value) }
                .mapKeys { it.key.toModel() }
        }

    override suspend fun addConversationToDeleteQueue(conversationId: ConversationId) {
        val queue = metadataDAO.getSerializable(CONVERSATIONS_TO_DELETE_KEY, SetSerializer(QualifiedIDEntity.serializer()))
            ?.toMutableSet()
            ?.plus(conversationId.toDao())
            ?: setOf(conversationId.toDao())

        metadataDAO.putSerializable(
            CONVERSATIONS_TO_DELETE_KEY,
            queue,
            SetSerializer(QualifiedIDEntity.serializer())
        )
    }

    override suspend fun removeConversationFromDeleteQueue(conversationId: ConversationId) {
        val queue = metadataDAO.getSerializable(CONVERSATIONS_TO_DELETE_KEY, SetSerializer(QualifiedIDEntity.serializer()))
            ?.toMutableSet()
            ?.minus(conversationId.toDao())
            ?: return

        metadataDAO.putSerializable(
            CONVERSATIONS_TO_DELETE_KEY,
            queue,
            SetSerializer(QualifiedIDEntity.serializer())
        )
    }

    override suspend fun getConversationsDeleteQueue(): List<ConversationId> =
        metadataDAO.getSerializable(CONVERSATIONS_TO_DELETE_KEY, SetSerializer(QualifiedIDEntity.serializer()))
            ?.map { it.toModel() } ?: listOf()

    override suspend fun updateChannelAddPermission(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermission
    ): Either<CoreFailure, Unit> = updateChannelAddPermissionRemotely(conversationId, channelAddPermission)
        .flatMap {
            when (it) {
                is UpdateChannelAddPermissionResponse.PermissionUnchanged -> {
                    Either.Right(Unit)
                }

                is UpdateChannelAddPermissionResponse.PermissionUpdated -> {
                    wrapStorageRequest {
                        conversationDAO.updateChannelAddPermission(
                            conversationId.toDao(),
                            channelAddPermission.toDaoChannelPermission()
                        )
                    }
                }
            }
        }

    override suspend fun getChannelAddPermission(conversationId: ConversationId): Either<StorageFailure, ChannelAddPermission> =
        wrapStorageRequest {
            conversationDAO.getChannelAddPermission(conversationId.toDao()).toModelChannelPermission()
        }

    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
        private const val CONVERSATIONS_TO_DELETE_KEY = "conversations_to_delete"
    }
}
