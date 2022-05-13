package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConvTeamInfo
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.CreateConversationRequest
import com.wire.kalium.network.api.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.api.model.ConversationAccessRole
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.ConversationEntity.Protocol
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo
import kotlinx.datetime.Instant

interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, mlsGroupState: GroupState?, selfUserTeamId: TeamId?): ConversationEntity
    fun fromApiModelToDaoModel(apiModel: ConvProtocol): Protocol
    fun fromDaoModel(daoModel: ConversationEntity): Conversation
    fun toApiModel(access: ConversationOptions.Access): ConversationAccess
    fun toApiModel(accessRole: ConversationOptions.AccessRole): ConversationAccessRole
    fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol
    fun toApiModel(name: String?, members: List<Member>, teamId: String?, options: ConversationOptions): CreateConversationRequest
    fun toConversationDetailsOneToOne(conversation: Conversation, otherUser: OtherUser, selfUser: SelfUser): ConversationDetails.OneOne
}

internal class ConversationMapperImpl(
    private val idMapper: IdMapper, private val conversationStatusMapper: ConversationStatusMapper
) : ConversationMapper {

    override fun fromApiModelToDaoModel(
        apiModel: ConversationResponse, mlsGroupState: GroupState?, selfUserTeamId: TeamId?
    ): ConversationEntity = ConversationEntity(
        idMapper.fromApiToDao(apiModel.id),
        apiModel.name,
        apiModel.getConversationType(selfUserTeamId),
        apiModel.teamId,
        apiModel.getProtocolInfo(mlsGroupState),
        conversationStatusMapper.fromApiToDaoModel(apiModel.members.self.otrMutedStatus),
        apiModel.members.self.otrMutedRef?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
        null,
        lastModifiedDate = apiModel.lastEventTime
    )

    override fun fromApiModelToDaoModel(apiModel: ConvProtocol): Protocol = when (apiModel) {
        ConvProtocol.PROTEUS -> Protocol.PROTEUS
        ConvProtocol.MLS -> Protocol.MLS
    }

    override fun fromDaoModel(daoModel: ConversationEntity): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id),
        daoModel.name,
        daoModel.type.fromDaoModelToType(),
        daoModel.teamId?.let { TeamId(it) },
        conversationStatusMapper.fromDaoModel(daoModel.mutedStatus),
        daoModel.lastNotificationDate,
        daoModel.lastModifiedDate
    )

    override fun toApiModel(name: String?, members: List<Member>, teamId: String?, options: ConversationOptions) =
        CreateConversationRequest(qualifiedUsers = if (options.protocol == ConversationOptions.Protocol.PROTEUS) members.map {
            idMapper.toApiModel(it.id)
        } else emptyList(),
            name = name,
            access = options.access.toList().map { toApiModel(it) },
            accessRole = options.accessRole.toList().map { toApiModel(it) },
            convTeamInfo = teamId?.let { ConvTeamInfo(false, it) },
            messageTimer = null,
            receiptMode = if (options.readReceiptsEnabled) ReceiptMode.ENABLED else ReceiptMode.DISABLED,
            conversationRole = ConversationDataSource.DEFAULT_MEMBER_ROLE,
            protocol = toApiModel(options.protocol)
        )

    override fun toConversationDetailsOneToOne(
        conversation: Conversation, otherUser: OtherUser, selfUser: SelfUser
    ): ConversationDetails.OneOne {
        return ConversationDetails.OneOne(
            conversation = conversation, otherUser = otherUser, connectionState = otherUser.connectionStatus,
            //TODO(user-metadata) get actual legal hold status
            legalHoldStatus = LegalHoldStatus.DISABLED, userType = determineOneToOneUserType(otherUser, selfUser)
        )
    }

    private fun determineOneToOneUserType(otherUser: OtherUser, selfUser: SelfUser): UserType {
        if (otherUser.isUsingWireCloudBackEnd()) {
            if (areNotInTheSameTeam(otherUser, selfUser)) {
                return UserType.GUEST
            }
        } else {
            if (areNotInTheSameTeam(otherUser, selfUser)) {
                return UserType.FEDERATED
            }
        }

        return UserType.INTERNAL
    }

    // if either self user has no team or other user,
    // does not make sense to compare them and we return false as of they are not on the same team
    private fun areNotInTheSameTeam(otherUser: OtherUser, selfUser: SelfUser): Boolean =
        !(selfUser.team != null && otherUser.team != null) || (selfUser.team != otherUser.team)

    override fun toApiModel(access: ConversationOptions.Access): ConversationAccess = when (access) {
        ConversationOptions.Access.PRIVATE -> ConversationAccess.PRIVATE
        ConversationOptions.Access.CODE -> ConversationAccess.CODE
        ConversationOptions.Access.INVITE -> ConversationAccess.INVITE
        ConversationOptions.Access.LINK -> ConversationAccess.LINK
    }

    override fun toApiModel(access: ConversationOptions.AccessRole): ConversationAccessRole = when (access) {
        ConversationOptions.AccessRole.TEAM_MEMBER -> ConversationAccessRole.TEAM_MEMBER
        ConversationOptions.AccessRole.NON_TEAM_MEMBER -> ConversationAccessRole.NON_TEAM_MEMBER
        ConversationOptions.AccessRole.GUEST -> ConversationAccessRole.GUEST
        ConversationOptions.AccessRole.SERVICE -> ConversationAccessRole.SERVICE
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
            ConversationResponse.Type.ONE_TO_ONE,
            ConversationResponse.Type.INCOMING_CONNECTION,
            ConversationResponse.Type.WAIT_FOR_CONNECTION,
            -> ConversationEntity.Type.ONE_ON_ONE
        }
    }

    private fun ConversationEntity.Type.fromDaoModelToType(): Conversation.Type = when (this) {
        ConversationEntity.Type.SELF -> Conversation.Type.SELF
        ConversationEntity.Type.ONE_ON_ONE -> Conversation.Type.ONE_ON_ONE
        ConversationEntity.Type.GROUP -> Conversation.Type.GROUP
    }
}
