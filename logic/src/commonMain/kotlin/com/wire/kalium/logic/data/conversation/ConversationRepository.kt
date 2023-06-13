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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.selfDeletingMessages.SelfDeletionTimer
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapProteusRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface ConversationRepository {
    @DelicateKaliumApi("This function does not get values from cache")
    suspend fun getProteusSelfConversationId(): Either<StorageFailure, ConversationId>

    @DelicateKaliumApi("This function does not get values from cache")
    suspend fun getMLSSelfConversationId(): Either<StorageFailure, ConversationId>

    suspend fun fetchGlobalTeamConversation(): Either<CoreFailure, Unit>
    suspend fun fetchConversations(): Either<CoreFailure, Unit>

    // TODO make all functions to have only logic models
    suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: String?,
        originatedFromEvent: Boolean = false
    ): Either<CoreFailure, Unit>

    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun getProteusTeamConversations(teamId: TeamId): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun getProteusTeamConversationsReadyForFinalisation(teamId: TeamId): Either<StorageFailure, Flow<List<QualifiedID>>>
    suspend fun observeConversationListDetails(): Flow<List<ConversationDetails>>
    suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>>
    suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun fetchConversationIfUnknown(conversationID: ConversationId): Either<CoreFailure, Unit>
    suspend fun observeById(conversationId: ConversationId): Flow<Either<StorageFailure, Conversation>>
    suspend fun getConversationById(conversationId: ConversationId): Conversation?
    suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation>
    suspend fun baseInfoById(conversationId: ConversationId): Either<StorageFailure, Conversation>
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
    suspend fun observeOneToOneConversationWithOtherUser(otherUserId: UserId): Flow<Either<CoreFailure, Conversation>>

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

    suspend fun getConversationsByGroupState(
        groupState: Conversation.ProtocolInfo.MLSCapable.GroupState
    ): Either<StorageFailure, List<Conversation>>

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

    suspend fun deleteUserFromConversations(userId: UserId): Either<CoreFailure, Unit>

    suspend fun getConversationIdsByUserId(userId: UserId): Either<CoreFailure, List<ConversationId>>
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
    suspend fun getUserSelfDeletionTimer(conversationId: ConversationId): Either<StorageFailure, SelfDeletionTimer?>
    suspend fun updateUserSelfDeletionTimer(conversationId: ConversationId, selfDeletionTimer: SelfDeletionTimer): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ConversationDataSource internal constructor(
    private val selfUserId: UserId,
    private val mlsClientProvider: MLSClientProvider,
    private val selfTeamIdProvider: SelfTeamIdProvider,
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
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : ConversationRepository {

    // TODO:I would suggest preparing another suspend func getSelfUser to get nullable self user,
    // this will help avoid some functions getting stuck when observeSelfUser will filter nullable values
    override suspend fun fetchConversations(): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS).d("Fetching conversations")
        return fetchAllConversationsFromAPI()
    }

    // TODO temporary method until backend API is changed: https://wearezeta.atlassian.net/browse/FS-1260
    override suspend fun fetchGlobalTeamConversation(): Either<CoreFailure, Unit> =
        selfTeamIdProvider().flatMap { teamId ->
            teamId?.let {
                wrapApiRequest {
                    conversationApi.fetchGlobalTeamConversationDetails(selfUserId.toApi(), teamId.value)
                }.flatMap {
                    persistConversations(listOf(it), teamId.value)
                }
            } ?: Either.Right(Unit)
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
                            .d("Skipping ${conversations.conversationsFailed.size} conversations failed")
                    }
                    if (conversations.conversationsNotFound.isNotEmpty()) {
                        kaliumLogger.withFeatureId(CONVERSATIONS)
                            .d("Skipping ${conversations.conversationsNotFound.size} conversations not found")
                    }
                    persistConversations(conversations.conversationsFound, selfTeamIdProvider().getOrNull()?.value)
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

    override suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: String?,
        originatedFromEvent: Boolean,
    ) = wrapStorageRequest {
        val conversationEntities = conversations
            // TODO work-around for a bug in the backend. Can be removed when fixed: https://wearezeta.atlassian.net/browse/FS-1262
            .filter { !(it.type == ConversationResponse.Type.GLOBAL_TEAM && it.protocol == ConvProtocol.PROTEUS) }
            .map { conversationResponse ->
                conversationMapper.fromApiModelToDaoModel(
                    conversationResponse,
                    mlsGroupState = conversationResponse.groupId?.let {
                        mlsGroupState(
                            idMapper.fromGroupIDEntity(it),
                            originatedFromEvent
                        )
                    },
                    selfTeamIdProvider().getOrNull(),
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

    override suspend fun getProteusTeamConversations(teamId: TeamId): Either<StorageFailure, Flow<List<Conversation>>> =
        wrapStorageRequest {
            conversationDAO.getAllProteusTeamConversations(teamId.value)
                .map { it.map(conversationMapper::fromDaoModel) }
        }

    override suspend fun getProteusTeamConversationsReadyForFinalisation(teamId: TeamId): Either<StorageFailure, Flow<List<QualifiedID>>> =
        wrapStorageRequest {
            conversationDAO.getAllProteusTeamConversationsReadyToBeFinalised(teamId.value)
                .map { it.map(QualifiedIDEntity::toModel) }
        }

    override suspend fun observeConversationListDetails(): Flow<List<ConversationDetails>> =
        combine(
            conversationDAO.getAllConversationDetails(),
            messageDAO.observeLastMessages(),
            messageDAO.observeConversationsUnreadEvents(),
        ) { conversationList, lastMessageList, unreadEvents ->
            val lastMessageMap = lastMessageList.associateBy { it.conversationId }
            conversationList.map { conversation ->
                conversationMapper.fromDaoModelToDetails(conversation,
                    lastMessageMap[conversation.id]?.let { messageMapper.fromEntityToMessagePreview(it) },
                    unreadEvents.firstOrNull { it.conversationId == conversation.id }?.unreadEvents?.mapKeys {
                        when (it.key) {
                            UnreadEventTypeEntity.KNOCK -> UnreadEventType.KNOCK
                            UnreadEventTypeEntity.MISSED_CALL -> UnreadEventType.MISSED_CALL
                            UnreadEventTypeEntity.MENTION -> UnreadEventType.MENTION
                            UnreadEventTypeEntity.REPLY -> UnreadEventType.REPLY
                            UnreadEventTypeEntity.MESSAGE -> UnreadEventType.MESSAGE
                        }
                    }
                )
            }
        }

    /**
     * Gets a flow that allows observing of
     */
    override suspend fun observeConversationDetailsById(conversationID: ConversationId): Flow<Either<StorageFailure, ConversationDetails>> =
        conversationDAO.observeGetConversationByQualifiedID(conversationID.toDao())
            .wrapStorageRequest()
            // TODO we don't need last message and unread count here, we should discuss to divide model for list and for details
            .mapRight { conversationMapper.fromDaoModelToDetails(it, null, mapOf()) }
            .distinctUntilChanged()

    override suspend fun fetchConversation(conversationID: ConversationId): Either<CoreFailure, Unit> {
        return wrapApiRequest {
            conversationApi.fetchConversationDetails(conversationID.toApi())
        }.flatMap {
            val selfUserTeamId = selfTeamIdProvider().getOrNull()
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
        conversationDAO.observeGetConversationByQualifiedID(conversationId.toDao()).filterNotNull()
            .map(conversationMapper::fromDaoModel)
            .wrapStorageRequest()

    // TODO: refactor. 3 Ways different ways to return conversation details?!
    override suspend fun getConversationById(conversationId: ConversationId): Conversation? =
        conversationDAO.observeGetConversationByQualifiedID(conversationId.toDao())
            .map { conversationEntity ->
                conversationEntity?.let { conversationMapper.fromDaoModel(it) }
            }.firstOrNull()

    override suspend fun detailsById(conversationId: ConversationId): Either<StorageFailure, Conversation> = wrapStorageRequest {
        conversationDAO.getConversationByQualifiedID(conversationId.toDao())?.let {
            conversationMapper.fromDaoModel(it)
        }
    }

    override suspend fun baseInfoById(conversationId: ConversationId): Either<StorageFailure, Conversation> = wrapStorageRequest {
        conversationDAO.getConversationBaseInfoByQualifiedID(conversationId.toDao())?.let {
            conversationMapper.fromDaoModel(it)
        }
    }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, Conversation.ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.getConversationProtocolInfo(conversationId.toDao())?.let {
                protocolInfoMapper.fromEntity(it)
            }
        }

    override suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Conversation.Member>> =
        conversationDAO.getAllMembers(conversationID.toDao()).map { members ->
            members.map(memberMapper::fromDaoModel)
        }

    override suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        conversationDAO.getAllMembers(conversationId.toDao()).first().map { it.user.toModel() }
    }

    override suspend fun persistMembers(
        members: List<Conversation.Member>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.insertMembersWithQualifiedId(
            members.map(memberMapper::toDaoModel), conversationID.toDao()
        )
    }

    override suspend fun updateMemberFromEvent(member: Conversation.Member, conversationID: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateMember(memberMapper.toDaoModel(member), conversationID.toDao())
        }

    override suspend fun deleteMembersFromEvent(
        userIDList: List<UserId>,
        conversationID: ConversationId
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.deleteMembersByQualifiedID(
                userIDList.map { it.toDao() },
                conversationID.toDao()
            )
        }

    override suspend fun getConversationsByGroupState(
        groupState: Conversation.ProtocolInfo.MLSCapable.GroupState
    ): Either<StorageFailure, List<Conversation>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByGroupState(conversationMapper.toDAOGroupState(groupState))
                .map(conversationMapper::fromDaoModel)
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
            memberMapper.fromMapOfClientsEntityToRecipients(
                clientDAO.conversationRecipient(conversationId.toDao())
            )
        }

    /**
     * Fetches a list of all recipients for a given conversation including this very client
     */
    override suspend fun getConversationRecipientsForCalling(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> =
        getConversationMembers(conversationId).map { it.map { userId -> userId.toApi() } }.flatMap {
            wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
        }

    override suspend fun observeOneToOneConversationWithOtherUser(otherUserId: UserId): Flow<Either<StorageFailure, Conversation>> {
        return conversationDAO.observeConversationWithOtherUser(otherUserId.toDao())
            .wrapStorageRequest()
            .mapRight { conversationMapper.fromDaoModel(it) }
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
            conversationDAO.updateConversationMemberRole(
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
                        wrapProteusRequest {
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

    override suspend fun getAssetMessages(
        conversationId: ConversationId,
    ): Either<StorageFailure, List<Message>> =
        wrapStorageRequest {
            messageDAO.getConversationMessagesByContentType(
                conversationId.toDao(),
                MessageEntity.ContentType.ASSET
            ).map(messageMapper::fromEntityToMessage)
        }

    override suspend fun deleteAllMessages(conversationId: ConversationId): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            messageDAO.deleteAllConversationMessages(conversationId.toDao())
        }

    override suspend fun observeIsUserMember(conversationId: ConversationId, userId: UserId): Flow<Either<CoreFailure, Boolean>> =
        conversationDAO.observeIsUserMember(conversationId.toDao(), userId.toDao())
            .wrapStorageRequest()

    override suspend fun whoDeletedMe(conversationId: ConversationId): Either<CoreFailure, UserId?> = wrapStorageRequest {
        conversationDAO.whoDeletedMeInConversation(
            conversationId.toDao(),
            idMapper.toStringDaoModel(selfUserId)
        )?.toModel()
    }

    override suspend fun deleteUserFromConversations(userId: UserId): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.revokeOneOnOneConversationsWithDeletedUser(userId.toDao())
    }

    override suspend fun getConversationIdsByUserId(userId: UserId): Either<CoreFailure, List<ConversationId>> {
        return wrapStorageRequest { conversationDAO.getConversationIdsByUserId(userId.toDao()) }
            .map { it.map { conversationIdEntity -> conversationIdEntity.toModel() } }
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

    override suspend fun getUserSelfDeletionTimer(conversationId: ConversationId): Either<StorageFailure, SelfDeletionTimer> =
        wrapStorageRequest {
            SelfDeletionTimer.Enabled(
                conversationDAO.getConversationByQualifiedID(conversationId.toDao())?.messageTimer?.toDuration(
                    DurationUnit.MILLISECONDS
                ) ?: ZERO
            )
        }

    override suspend fun updateUserSelfDeletionTimer(
        conversationId: ConversationId,
        selfDeletionTimer: SelfDeletionTimer
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationDAO.updateUserMessageTimer(
            conversationId = conversationId.toDao(),
            messageTimer = selfDeletionTimer.toDuration().inWholeMilliseconds
        )
    }

    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
    }
}
