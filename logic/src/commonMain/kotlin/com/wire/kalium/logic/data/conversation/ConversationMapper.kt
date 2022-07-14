package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConvTeamInfo
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.CreateConversationRequest
import com.wire.kalium.network.api.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.ConversationEntity.Protocol
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo
import kotlinx.datetime.Instant

interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, mlsGroupState: GroupState?, selfUserTeamId: TeamId?): ConversationEntity
    fun fromApiModelToDaoModel(apiModel: ConvProtocol): Protocol
    fun fromDaoModel(daoModel: ConversationEntity): Conversation
    fun toApiModel(access: Conversation.Access): ConversationAccessDTO
    fun toApiModel(accessRole: Conversation.AccessRole): ConversationAccessRoleDTO
    fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol
    fun toApiModel(name: String?, members: List<UserId>, teamId: String?, options: ConversationOptions): CreateConversationRequest
    fun toConversationDetailsOneToOne(conversation: Conversation, otherUser: OtherUser, selfUser: SelfUser): ConversationDetails.OneOne
}

internal class ConversationMapperImpl(
    private val idMapper: IdMapper,
    private val conversationStatusMapper: ConversationStatusMapper,
    private val protocolInfoMapper: ProtocolInfoMapper
) : ConversationMapper {

    override fun fromApiModelToDaoModel(
        apiModel: ConversationResponse, mlsGroupState: GroupState?, selfUserTeamId: TeamId?
    ): ConversationEntity = ConversationEntity(
        id = idMapper.fromApiToDao(apiModel.id),
        name = apiModel.name,
        type = apiModel.getConversationType(selfUserTeamId),
        teamId = apiModel.teamId,
        protocolInfo = apiModel.getProtocolInfo(mlsGroupState),
        mutedStatus = conversationStatusMapper.fromApiToDaoModel(apiModel.members.self.otrMutedStatus),
        mutedTime = apiModel.members.self.otrMutedRef?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
        lastNotificationDate = null,
        lastModifiedDate = apiModel.lastEventTime,
        access = apiModel.access.map { it.toDAO() },
        accessRole = apiModel.accessRole?.map { it.toDAO() }
    )

    override fun fromApiModelToDaoModel(apiModel: ConvProtocol): Protocol = when (apiModel) {
        ConvProtocol.PROTEUS -> Protocol.PROTEUS
        ConvProtocol.MLS -> Protocol.MLS
    }

    override fun fromDaoModel(daoModel: ConversationEntity): Conversation = Conversation(
        id = idMapper.fromDaoModel(daoModel.id),
        name = daoModel.name,
        type = daoModel.type.fromDaoModelToType(),
        teamId = daoModel.teamId?.let { TeamId(it) },
        protocol = protocolInfoMapper.fromEntity(daoModel.protocolInfo),
        mutedStatus = conversationStatusMapper.fromDaoModel(daoModel.mutedStatus),
        lastNotificationDate = daoModel.lastNotificationDate,
        lastModifiedDate = daoModel.lastModifiedDate,
        access = daoModel.access.map { it.toDomain() },
        accessRole = daoModel.accessRole?.map { it.toDomain() }
    )

    override fun toApiModel(name: String?, members: List<UserId>, teamId: String?, options: ConversationOptions) =
        CreateConversationRequest(qualifiedUsers = if (options.protocol == ConversationOptions.Protocol.PROTEUS) members.map {
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

    override fun toConversationDetailsOneToOne(
        conversation: Conversation,
        otherUser: OtherUser,
        selfUser: SelfUser
    ): ConversationDetails.OneOne {
        return ConversationDetails.OneOne(
            conversation = conversation,
            otherUser = otherUser,
            connectionState = otherUser.connectionStatus,
            // TODO(user-metadata) get actual legal hold status
            legalHoldStatus = LegalHoldStatus.DISABLED,
            userType = otherUser.userType
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
    }

    override fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol = when (protocol) {
        ConversationOptions.Protocol.PROTEUS -> ConvProtocol.PROTEUS
        ConversationOptions.Protocol.MLS -> ConvProtocol.MLS
    }

    private fun ConversationResponse.getProtocolInfo(mlsGroupState: GroupState?): ProtocolInfo {
        return when (protocol) {
            ConvProtocol.MLS -> ProtocolInfo.MLS(groupId ?: "", mlsGroupState ?: GroupState.PENDING)
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
}

private fun ConversationAccessDTO.toDAO(): ConversationEntity.Access = when (this) {
    ConversationAccessDTO.PRIVATE -> ConversationEntity.Access.PRIVATE
    ConversationAccessDTO.CODE -> ConversationEntity.Access.CODE
    ConversationAccessDTO.INVITE -> ConversationEntity.Access.INVITE
    ConversationAccessDTO.LINK -> ConversationEntity.Access.LINK
}

private fun ConversationEntity.Access.toDomain(): Conversation.Access = when (this) {
    ConversationEntity.Access.PRIVATE -> Conversation.Access.PRIVATE
    ConversationEntity.Access.INVITE -> Conversation.Access.INVITE
    ConversationEntity.Access.LINK -> Conversation.Access.LINK
    ConversationEntity.Access.CODE -> Conversation.Access.CODE
}

private fun ConversationEntity.AccessRole.toDomain(): Conversation.AccessRole = when (this) {
    ConversationEntity.AccessRole.TEAM_MEMBER -> Conversation.AccessRole.TEAM_MEMBER
    ConversationEntity.AccessRole.NON_TEAM_MEMBER -> Conversation.AccessRole.NON_TEAM_MEMBER
    ConversationEntity.AccessRole.GUEST -> Conversation.AccessRole.GUEST
    ConversationEntity.AccessRole.SERVICE -> Conversation.AccessRole.SERVICE
}
