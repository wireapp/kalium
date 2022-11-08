package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.connection.ConnectionStatusMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.BotService
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.type.DomainUserTypeMapper
import com.wire.kalium.logic.util.EPOCH_FIRST_DAY
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConvTeamInfo
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.ConversationEntity.Protocol
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo
import com.wire.kalium.persistence.dao.ConversationViewEntity
import com.wire.kalium.persistence.dao.ProposalTimerEntity
import com.wire.kalium.persistence.util.requireField
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Suppress("TooManyFunctions")
interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, mlsGroupState: GroupState?, selfUserTeamId: TeamId?): ConversationEntity
    fun fromApiModelToDaoModel(apiModel: ConvProtocol): Protocol
    fun fromDaoModel(daoModel: ConversationViewEntity): Conversation
    fun fromDaoModelToDetails(daoModel: ConversationViewEntity): ConversationDetails
    fun fromDaoModel(daoModel: ProposalTimerEntity): ProposalTimer
    fun toDAOAccess(accessList: Set<ConversationAccessDTO>): List<ConversationEntity.Access>
    fun toDAOAccessRole(accessRoleList: Set<ConversationAccessRoleDTO>): List<ConversationEntity.AccessRole>
    fun toDAOGroupState(groupState: Conversation.ProtocolInfo.MLS.GroupState): GroupState
    fun toDAOProposalTimer(proposalTimer: ProposalTimer): ProposalTimerEntity
    fun toApiModel(access: Conversation.Access): ConversationAccessDTO
    fun toApiModel(accessRole: Conversation.AccessRole): ConversationAccessRoleDTO
    fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol
    fun toApiModel(name: String?, members: List<UserId>, teamId: String?, options: ConversationOptions): CreateConversationRequest

    @Suppress("LongParameterList")
    fun toConversationDetailsOneToOne(
        conversation: Conversation,
        otherUser: OtherUser,
        selfUser: SelfUser,
        unreadMessageCount: Int,
        unreadMentionsCount: Long,
        lastUnreadMessage: Message?
    ): ConversationDetails.OneOne

    fun toDaoModel(conversation: Conversation): ConversationEntity
}

@Suppress("TooManyFunctions")
internal class ConversationMapperImpl(
    private val idMapper: IdMapper,
    private val conversationStatusMapper: ConversationStatusMapper,
    private val protocolInfoMapper: ProtocolInfoMapper,
    private val userAvailabilityStatusMapper: AvailabilityStatusMapper,
    private val domainUserTypeMapper: DomainUserTypeMapper,
    private val connectionStatusMapper: ConnectionStatusMapper
) : ConversationMapper {

    override fun fromApiModelToDaoModel(
        apiModel: ConversationResponse,
        mlsGroupState: GroupState?,
        selfUserTeamId: TeamId?
    ): ConversationEntity = ConversationEntity(
        id = idMapper.fromApiToDao(apiModel.id),
        name = apiModel.name,
        type = apiModel.getConversationType(selfUserTeamId),
        teamId = apiModel.teamId,
        protocolInfo = apiModel.getProtocolInfo(mlsGroupState),
        mutedStatus = conversationStatusMapper.fromMutedStatusApiToDaoModel(apiModel.members.self.otrMutedStatus),
        mutedTime = apiModel.members.self.otrMutedRef?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
        removedBy = null,
        creatorId = apiModel.creator,
        lastReadDate = EPOCH_FIRST_DAY,
        lastNotificationDate = null,
        lastModifiedDate = apiModel.lastEventTime,
        access = apiModel.access.map { it.toDAO() },
        accessRole = apiModel.accessRole.map { it.toDAO() }
    )

    override fun fromApiModelToDaoModel(apiModel: ConvProtocol): Protocol = when (apiModel) {
        ConvProtocol.PROTEUS -> Protocol.PROTEUS
        ConvProtocol.MLS -> Protocol.MLS
    }

    override fun fromDaoModel(daoModel: ConversationViewEntity): Conversation = with(daoModel) {
        val lastReadDateEntity = if (type == ConversationEntity.Type.CONNECTION_PENDING) EPOCH_FIRST_DAY else lastReadDate

        Conversation(
            id = idMapper.fromDaoModel(id),
            name = name,
            type = type.fromDaoModelToType(),
            teamId = teamId?.let { TeamId(it) },
            protocol = protocolInfoMapper.fromEntity(protocolInfo),
            mutedStatus = conversationStatusMapper.fromMutedStatusDaoModel(mutedStatus),
            removedBy = removedBy?.let { conversationStatusMapper.fromRemovedByToLogicModel(it) },
            lastNotificationDate = lastNotificationDate,
            lastModifiedDate = lastModifiedDate,
            lastReadDate = lastReadDateEntity,
            access = accessList.map { it.toDAO() },
            accessRole = accessRoleList.map { it.toDAO() },
            creatorId = creatorId
        )
    }

    @Suppress("ComplexMethod", "LongMethod")
    override fun fromDaoModelToDetails(daoModel: ConversationViewEntity): ConversationDetails = with(daoModel) {
        when (type) {

            ConversationEntity.Type.SELF -> {
                ConversationDetails.Self(fromDaoModel(daoModel))
            }

            ConversationEntity.Type.ONE_ON_ONE -> {
                ConversationDetails.OneOne(
                    conversation = fromDaoModel(daoModel),
                    otherUser = OtherUser(
                        id = idMapper.fromDaoModel(otherUserId.requireField("otherUserID in OneOnOne")),
                        name = name,
                        accentId = 0,
                        userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                        availabilityStatus = userAvailabilityStatusMapper.fromDaoAvailabilityStatusToModel(userAvailabilityStatus),
                        deleted = userDeleted ?: false,
                        botService = botService?.let { BotService(it.id, it.provider) },
                        handle = null,
                        completePicture = previewAssetId?.let { idMapper.fromDaoModel(it) },
                        previewPicture = previewAssetId?.let { idMapper.fromDaoModel(it) },
                        teamId = teamId?.let { TeamId(it) },
                        connectionStatus = connectionStatusMapper.fromDaoModel(connectionStatus)
                    ),
                    legalHoldStatus = LegalHoldStatus.DISABLED,
                    userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                    unreadMessagesCount = unreadContentCountEntity.values.sum(),
                    unreadMentionsCount = unreadMentionsCount,
                    lastUnreadMessage = null
                )
            }

            ConversationEntity.Type.GROUP -> {
                ConversationDetails.Group(
                    conversation = fromDaoModel(daoModel),
                    legalHoldStatus = LegalHoldStatus.DISABLED,
                    hasOngoingCall = callStatus != null, // todo: we can do better!
                    unreadMessagesCount = unreadContentCountEntity.values.sum(),
                    unreadMentionsCount = unreadMentionsCount,
                    lastUnreadMessage = null,
                    isSelfUserMember = isMember == 1L,
                    isSelfUserCreator = isCreator == 1L
                )
            }

            ConversationEntity.Type.CONNECTION_PENDING -> {
                val otherUser = OtherUser(
                    id = idMapper.fromDaoModel(otherUserId.requireField("otherUserID in OneOnOne")),
                    name = name,
                    accentId = 0,
                    userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                    availabilityStatus = userAvailabilityStatusMapper.fromDaoAvailabilityStatusToModel(userAvailabilityStatus),
                    deleted = userDeleted ?: false,
                    botService = botService?.let { BotService(it.id, it.provider) },
                    handle = null,
                    completePicture = previewAssetId?.let { idMapper.fromDaoModel(it) },
                    previewPicture = previewAssetId?.let { idMapper.fromDaoModel(it) },
                    teamId = teamId?.let { TeamId(it) }
                )

                ConversationDetails.Connection(
                    conversationId = idMapper.fromDaoModel(id),
                    otherUser = otherUser,
                    userType = domainUserTypeMapper.fromUserTypeEntity(userType),
                    lastModifiedDate = lastModifiedDate.orEmpty(),
                    connection = Connection(
                        conversationId = id.value,
                        from = "",
                        lastUpdate = "",
                        qualifiedConversationId = idMapper.fromDaoModel(id),
                        qualifiedToId = otherUserId.let { idMapper.fromDaoModel(it!!) },
                        status = connectionStatusMapper.fromDaoModel(connectionStatus),
                        toId = "", // todo
                        fromUser = otherUser
                    ),
                    protocolInfo = protocolInfoMapper.fromEntity(protocolInfo),
                    access = accessList.map { it.toDAO() },
                    accessRole = accessRoleList.map { it.toDAO() },
                )
            }
        }
    }

    override fun fromDaoModel(daoModel: ProposalTimerEntity): ProposalTimer =
        ProposalTimer(idMapper.fromGroupIDEntity(daoModel.groupID), daoModel.firingDate)

    override fun toDAOAccess(accessList: Set<ConversationAccessDTO>): List<ConversationEntity.Access> = accessList.map {
        when (it) {
            ConversationAccessDTO.PRIVATE -> ConversationEntity.Access.PRIVATE
            ConversationAccessDTO.CODE -> ConversationEntity.Access.CODE
            ConversationAccessDTO.INVITE -> ConversationEntity.Access.INVITE
            ConversationAccessDTO.LINK -> ConversationEntity.Access.LINK
        }
    }

    override fun toDAOAccessRole(accessRoleList: Set<ConversationAccessRoleDTO>): List<ConversationEntity.AccessRole> = accessRoleList.map {
        when (it) {
            ConversationAccessRoleDTO.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
            ConversationAccessRoleDTO.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
            ConversationAccessRoleDTO.GUEST -> ConversationEntity.AccessRole.GUEST
            ConversationAccessRoleDTO.SERVICE -> ConversationEntity.AccessRole.SERVICE
            ConversationAccessRoleDTO.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
        }
    }

    override fun toDAOGroupState(groupState: Conversation.ProtocolInfo.MLS.GroupState): GroupState =
        when (groupState) {
            Conversation.ProtocolInfo.MLS.GroupState.ESTABLISHED -> GroupState.ESTABLISHED
            Conversation.ProtocolInfo.MLS.GroupState.PENDING_JOIN -> GroupState.PENDING_JOIN
            Conversation.ProtocolInfo.MLS.GroupState.PENDING_WELCOME_MESSAGE -> GroupState.PENDING_WELCOME_MESSAGE
            Conversation.ProtocolInfo.MLS.GroupState.PENDING_CREATION -> GroupState.PENDING_CREATION
        }

    override fun toDAOProposalTimer(proposalTimer: ProposalTimer): ProposalTimerEntity =
        ProposalTimerEntity(idMapper.toGroupIDEntity(proposalTimer.groupID), proposalTimer.timestamp)

    override fun toApiModel(
        name: String?,
        members: List<UserId>,
        teamId: String?,
        options: ConversationOptions
    ) = CreateConversationRequest(
        qualifiedUsers = if (options.protocol == ConversationOptions.Protocol.PROTEUS) members.map {
            idMapper.toApiModel(it)
        } else emptyList(),
        name = name,
        access = options.access?.toList()?.map { toApiModel(it) },
        accessRole = options.accessRole?.toList()?.map { toApiModel(it) },
        convTeamInfo = teamId?.let { ConvTeamInfo(false, it) },
        messageTimer = null,
        receiptMode = if (options.readReceiptsEnabled) ReceiptMode.ENABLED else ReceiptMode.DISABLED,
        conversationRole = ConversationDataSource.DEFAULT_MEMBER_ROLE,
        protocol = toApiModel(options.protocol),
        creatorClient = options.creatorClientId
    )

    // TODO looks like could be removed
    override fun toConversationDetailsOneToOne(
        conversation: Conversation,
        otherUser: OtherUser,
        selfUser: SelfUser,
        unreadMessageCount: Int,
        unreadMentionsCount: Long,
        lastUnreadMessage: Message?
    ): ConversationDetails.OneOne {
        return ConversationDetails.OneOne(
            conversation = conversation,
            otherUser = otherUser,
            // TODO(user-metadata) get actual legal hold status
            legalHoldStatus = LegalHoldStatus.DISABLED,
            userType = otherUser.userType,
            unreadMessagesCount = unreadMessageCount,
            unreadMentionsCount = unreadMentionsCount,
            lastUnreadMessage = lastUnreadMessage
        )
    }

    override fun toApiModel(access: Conversation.Access): ConversationAccessDTO = when (access) {
        Conversation.Access.PRIVATE -> ConversationAccessDTO.PRIVATE
        Conversation.Access.CODE -> ConversationAccessDTO.CODE
        Conversation.Access.INVITE -> ConversationAccessDTO.INVITE
        Conversation.Access.LINK -> ConversationAccessDTO.LINK
    }

    override fun toApiModel(accessRole: Conversation.AccessRole): ConversationAccessRoleDTO = when (accessRole) {
        Conversation.AccessRole.TEAM_MEMBER -> ConversationAccessRoleDTO.TEAM_MEMBER
        Conversation.AccessRole.NON_TEAM_MEMBER -> ConversationAccessRoleDTO.NON_TEAM_MEMBER
        Conversation.AccessRole.GUEST -> ConversationAccessRoleDTO.GUEST
        Conversation.AccessRole.SERVICE -> ConversationAccessRoleDTO.SERVICE
        Conversation.AccessRole.EXTERNAL -> ConversationAccessRoleDTO.EXTERNAL
    }

    override fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol = when (protocol) {
        ConversationOptions.Protocol.PROTEUS -> ConvProtocol.PROTEUS
        ConversationOptions.Protocol.MLS -> ConvProtocol.MLS
    }

    override fun toDaoModel(conversation: Conversation): ConversationEntity = with(conversation) {
        ConversationEntity(
            id = idMapper.toDaoModel(conversation.id),
            name = name,
            type = type.toDAO(),
            teamId = conversation.teamId.toString(),
            protocolInfo = protocolInfoMapper.toEntity(conversation.protocol),
            mutedStatus = conversationStatusMapper.toMutedStatusDaoModel(conversation.mutedStatus),
            mutedTime = 0,
            removedBy = null,
            creatorId = creatorId.orEmpty(),
            lastNotificationDate = "",
            lastModifiedDate = "",
            lastReadDate = "",
            access = conversation.access.map { it.toDAO() },
            accessRole = conversation.accessRole.map { it.toDAO() }
        )
    }

    private fun ConversationResponse.getProtocolInfo(mlsGroupState: GroupState?): ProtocolInfo {
        return when (protocol) {
            ConvProtocol.MLS -> ProtocolInfo.MLS(
                groupId ?: "",
                mlsGroupState ?: GroupState.PENDING_JOIN,
                epoch ?: 0UL,
                keyingMaterialLastUpdate = Clock.System.now(),
                ConversationEntity.CipherSuite.fromTag(mlsCipherSuiteTag)
            )

            ConvProtocol.PROTEUS -> ProtocolInfo.Proteus
        }
    }

    private fun ConversationResponse.getConversationType(selfUserTeamId: TeamId?): ConversationEntity.Type {
        return when (type) {
            ConversationResponse.Type.SELF -> ConversationEntity.Type.SELF
            ConversationResponse.Type.GROUP -> {
                // Fake team 1:1 conversations
                val onlyOneOtherMember = members.otherMembers.size == 1
                val noCustomName = name.isNullOrBlank()
                val belongsToSelfTeam = selfUserTeamId != null && selfUserTeamId.value == teamId
                val isTeamOneOne = onlyOneOtherMember && noCustomName && belongsToSelfTeam
                if (isTeamOneOne) {
                    ConversationEntity.Type.ONE_ON_ONE
                } else {
                    ConversationEntity.Type.GROUP
                }
            }

            ConversationResponse.Type.ONE_TO_ONE -> ConversationEntity.Type.ONE_ON_ONE
            ConversationResponse.Type.INCOMING_CONNECTION,
            ConversationResponse.Type.WAIT_FOR_CONNECTION -> ConversationEntity.Type.CONNECTION_PENDING
        }
    }
}

private fun ConversationEntity.Type.fromDaoModelToType(): Conversation.Type = when (this) {
    ConversationEntity.Type.SELF -> Conversation.Type.SELF
    ConversationEntity.Type.ONE_ON_ONE -> Conversation.Type.ONE_ON_ONE
    ConversationEntity.Type.GROUP -> Conversation.Type.GROUP
    ConversationEntity.Type.CONNECTION_PENDING -> Conversation.Type.CONNECTION_PENDING
}

private fun ConversationAccessRoleDTO.toDAO(): ConversationEntity.AccessRole = when (this) {
    ConversationAccessRoleDTO.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
    ConversationAccessRoleDTO.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
    ConversationAccessRoleDTO.GUEST -> ConversationEntity.AccessRole.GUEST
    ConversationAccessRoleDTO.SERVICE -> ConversationEntity.AccessRole.SERVICE
    ConversationAccessRoleDTO.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
}

private fun ConversationAccessDTO.toDAO(): ConversationEntity.Access = when (this) {
    ConversationAccessDTO.PRIVATE -> ConversationEntity.Access.PRIVATE
    ConversationAccessDTO.CODE -> ConversationEntity.Access.CODE
    ConversationAccessDTO.INVITE -> ConversationEntity.Access.INVITE
    ConversationAccessDTO.LINK -> ConversationEntity.Access.LINK
}

private fun ConversationEntity.Access.toDAO(): Conversation.Access = when (this) {
    ConversationEntity.Access.PRIVATE -> Conversation.Access.PRIVATE
    ConversationEntity.Access.INVITE -> Conversation.Access.INVITE
    ConversationEntity.Access.LINK -> Conversation.Access.LINK
    ConversationEntity.Access.CODE -> Conversation.Access.CODE
}

private fun ConversationEntity.AccessRole.toDAO(): Conversation.AccessRole = when (this) {
    ConversationEntity.AccessRole.TEAM_MEMBER -> Conversation.AccessRole.TEAM_MEMBER
    ConversationEntity.AccessRole.NON_TEAM_MEMBER -> Conversation.AccessRole.NON_TEAM_MEMBER
    ConversationEntity.AccessRole.GUEST -> Conversation.AccessRole.GUEST
    ConversationEntity.AccessRole.SERVICE -> Conversation.AccessRole.SERVICE
    ConversationEntity.AccessRole.EXTERNAL -> Conversation.AccessRole.EXTERNAL
}

private fun Conversation.Type.toDAO(): ConversationEntity.Type = when (this) {
    Conversation.Type.SELF -> ConversationEntity.Type.SELF
    Conversation.Type.ONE_ON_ONE -> ConversationEntity.Type.ONE_ON_ONE
    Conversation.Type.GROUP -> ConversationEntity.Type.GROUP
    Conversation.Type.CONNECTION_PENDING -> ConversationEntity.Type.CONNECTION_PENDING
}

private fun Conversation.AccessRole.toDAO(): ConversationEntity.AccessRole = when (this) {
    Conversation.AccessRole.TEAM_MEMBER -> ConversationEntity.AccessRole.TEAM_MEMBER
    Conversation.AccessRole.NON_TEAM_MEMBER -> ConversationEntity.AccessRole.NON_TEAM_MEMBER
    Conversation.AccessRole.GUEST -> ConversationEntity.AccessRole.GUEST
    Conversation.AccessRole.SERVICE -> ConversationEntity.AccessRole.SERVICE
    Conversation.AccessRole.EXTERNAL -> ConversationEntity.AccessRole.EXTERNAL
}

private fun Conversation.Access.toDAO(): ConversationEntity.Access = when (this) {
    Conversation.Access.PRIVATE -> ConversationEntity.Access.PRIVATE
    Conversation.Access.INVITE -> ConversationEntity.Access.INVITE
    Conversation.Access.LINK -> ConversationEntity.Access.LINK
    Conversation.Access.CODE -> ConversationEntity.Access.CODE
}
