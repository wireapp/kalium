package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.isRight
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.suspending
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
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.dao.Member as MemberEntity

interface ConversationRepository {
    suspend fun getSelfConversationId(): ConversationId
    suspend fun fetchConversations(): Either<CoreFailure, Unit>
    suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit>
    suspend fun getConversationList(): Either<StorageFailure, Flow<List<Conversation>>>
    suspend fun observeConversationList(): Flow<List<Conversation>>
    suspend fun getConversationDetailsById(conversationID: ConversationId): Flow<ConversationDetails>
    suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>>
    suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>>
    suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo>
    suspend fun observeConversationMembers(conversationID: ConversationId): Flow<List<Member>>
    suspend fun persistMember(member: MemberEntity, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun persistMembers(members: List<MemberEntity>, conversationID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun deleteMember(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity): Either<CoreFailure, Unit>
    suspend fun getOneToOneConversationDetailsByUserId(otherUserId: UserId): Either<CoreFailure, ConversationDetails.OneOne?>
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
    suspend fun setConversationAsNotified(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit>
    suspend fun setAllConversationsAsNotified(date: String): Either<StorageFailure, Unit>
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

    override suspend fun fetchConversations(): Either<CoreFailure, Unit> = suspending {
        kaliumLogger.d("Fetching conversations")
        val selfUserTeamId = userRepository.getSelfUser().first().team

        val conversationsResult = fetchAllConversationsFromAPI()

        conversationsResult.onFailure { networkFailure ->
            val throwable = (networkFailure as? NetworkFailure.ServerMiscommunication)?.rootCause
            kaliumLogger.e("Failed to fetch all conversations due to network error", throwable)
        }

        conversationsResult.flatMap { conversations ->
            kaliumLogger.d("Persisting fetched conversations into storage")
            persistConversations(conversations, selfUserTeamId)
        }
    }

    override suspend fun insertConversationFromEvent(event: Event.Conversation.NewConversation): Either<CoreFailure, Unit> = suspending {
        val selfUserTeamId = userRepository.getSelfUser().first().team
        persistConversations(listOf(event.conversation), selfUserTeamId)
    }

    private suspend fun fetchAllConversationsFromAPI(): Either<NetworkFailure, List<ConversationResponse>> {
        var hasMore = true
        val allConversations = mutableListOf<ConversationResponse>()
        var latestResult: Either<NetworkFailure, Unit> = Either.Right(Unit)
        while (hasMore && latestResult.isRight()) {
            latestResult = wrapApiRequest {
                val lastConversationIdValue = allConversations.lastOrNull()?.id?.value
                kaliumLogger.v("Fetching conversation page starting with id $lastConversationIdValue")
                conversationApi.conversationsByBatch(lastConversationIdValue, 100)
            }.onSuccess {
                allConversations += it.conversations
                hasMore = it.hasMore
            }.map { }
        }
        return latestResult.map {
            allConversations
        }
    }

    private suspend fun persistConversations(
        conversations: List<ConversationResponse>,
        selfUserTeamId: String?
    ) = wrapStorageRequest {
        val conversationEntities = conversations.map { conversationResponse ->
            conversationMapper.fromApiModelToDaoModel(
                conversationResponse,
                groupCreation = false,
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
    override suspend fun getConversationDetailsById(conversationID: ConversationId): Flow<ConversationDetails> =
        conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationID))
            .filterNotNull()
            .map(conversationMapper::fromDaoModel)
            .flatMapLatest(::getDetailsFlowConversation)

    private suspend fun getDetailsFlowConversation(conversation: Conversation): Flow<ConversationDetails> =
        when (conversation.type) {
            ConversationEntity.Type.SELF -> flowOf(ConversationDetails.Self(conversation))
            ConversationEntity.Type.GROUP -> flowOf(ConversationDetails.Group(conversation))
            ConversationEntity.Type.ONE_ON_ONE -> {
                suspending {
                    val selfUserId = userRepository.getSelfUser().map { it.id }.first()
                    getConversationMembers(conversation.id).map { members ->
                        members.first { itemId -> itemId != selfUserId }
                    }.coFold({
                        // TODO: How to Handle failure when dealing with flows?
                        throw IOException("Failure to fetch other user of 1:1 Conversation")
                    }, { otherUserId ->
                        userRepository.getKnownUser(otherUserId)
                    }).filterNotNull().map { otherUser ->
                        ConversationDetails.OneOne(
                            conversation, otherUser,
                            otherUser.connectionStatus,
                            LegalHoldStatus.DISABLED //TODO get actual legal hold status
                        )
                    }
                }
            }
        }

    //Deprecated notice, so we can use newer versions of Kalium on Reloaded without breaking things.
    @Deprecated("This doesn't return conversation details", ReplaceWith("getConversationDetailsById"))
    override suspend fun getConversationDetails(conversationId: ConversationId): Either<StorageFailure, Flow<Conversation>> =
        wrapStorageRequest {
            conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationId))
                .filterNotNull()
                .map(conversationMapper::fromDaoModel)
        }

    override suspend fun getConversationProtocolInfo(conversationId: ConversationId): Either<StorageFailure, ProtocolInfo> =
        wrapStorageRequest {
            conversationDAO.getConversationByQualifiedID(idMapper.toDaoModel(conversationId)).first()?.protocolInfo
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

    override suspend fun deleteMember(conversationID: QualifiedIDEntity, userID: QualifiedIDEntity): Either<CoreFailure, Unit> =
        wrapStorageRequest { conversationDAO.deleteMemberByQualifiedID(conversationID, userID) }

    override suspend fun createGroupConversation(
        name: String?,
        members: List<Member>,
        options: ConversationOptions
    ): Either<CoreFailure, Conversation> = suspending {
        wrapStorageRequest {
            userRepository.getSelfUser().first()
        }.flatMap { selfUser ->
            wrapApiRequest {
                conversationApi.createNewConversation(
                    conversationMapper.toApiModel(name, members, selfUser.team, options)
                )
            }.flatMap { conversationResponse ->
                val teamId = selfUser.team?.let { TeamId(it) }
                val conversationEntity = conversationMapper.fromApiModelToDaoModel(conversationResponse, groupCreation = true, teamId)
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
    }

    override suspend fun getConversationsForNotifications(): Flow<List<Conversation>> =
        conversationDAO.getConversationsForNotifications()
            .filterNotNull()
            .map { it.map(conversationMapper::fromDaoModel) }

    override suspend fun setConversationAsNotified(qualifiedID: QualifiedID, date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateConversationNotificationDate(idMapper.toDaoModel(qualifiedID), date) }

    override suspend fun setAllConversationsAsNotified(date: String): Either<StorageFailure, Unit> =
        wrapStorageRequest { conversationDAO.updateAllConversationsNotificationDate(date) }

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
    override suspend fun getConversationRecipients(conversationId: ConversationId): Either<CoreFailure, List<Recipient>> = suspending {
        getConversationMembers(conversationId)
            .map { it.map(idMapper::toApiModel) }
            .flatMap {
                wrapApiRequest { clientApi.listClientsOfUsers(it) }.map { memberMapper.fromMapOfClientsResponseToRecipients(it) }
            }
    }

    //TODO: this needs some kind of optimization, we could directly get the conversation by otherUserId and
    // not to get all the conversation first and filter them to look for the id, this could be done on DAO level
    override suspend fun getOneToOneConversationDetailsByUserId(otherUserId: UserId): Either<StorageFailure, ConversationDetails.OneOne?> {
        return wrapStorageRequest {
            observeConversationList()
                .flatMapMerge { it.asFlow() }
                .flatMapMerge { getConversationDetailsById(it.id) }
                .filterIsInstance<ConversationDetails.OneOne>()
                .firstOrNull { otherUserId == it.otherUser.id }
        }
    }

    /**
     * Updates the conversation muting options status and the timestamp of the applied change, both remotely and local
     */
    override suspend fun updateMutedStatus(
        conversationId: ConversationId,
        mutedStatus: MutedConversationStatus,
        mutedStatusTimestamp: Long
    ): Either<CoreFailure, Unit> = suspending {
        wrapApiRequest {
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
    }

    companion object {
        const val DEFAULT_MEMBER_ROLE = "wire_member"
    }
}
