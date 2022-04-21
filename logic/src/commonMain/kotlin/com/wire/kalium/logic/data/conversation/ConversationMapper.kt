package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConvTeamInfo
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.CreateConversationRequest
import com.wire.kalium.network.api.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.api.model.ConversationAccessRole
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationEntity.GroupState
import com.wire.kalium.persistence.dao.ConversationEntity.ProtocolInfo
import kotlinx.datetime.Instant
import com.wire.kalium.persistence.dao.ConversationEntity as PersistedConversation
import com.wire.kalium.persistence.dao.ConversationEntity.Protocol as PersistedProtocol

interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, groupCreation: Boolean, selfUserTeamId: TeamId?): PersistedConversation
    fun fromApiModelToDaoModel(apiModel: ConvProtocol): PersistedProtocol
    fun fromDaoModel(daoModel: PersistedConversation): Conversation
    fun toDaoModel(welcomeEvent: Event.Conversation.MLSWelcome, groupId: String): ConversationEntity
    fun toApiModel(access: ConversationOptions.Access): ConversationAccess
    fun toApiModel(accessRole: ConversationOptions.AccessRole): ConversationAccessRole
    fun toApiModel(protocol: ConversationOptions.Protocol): ConvProtocol
    fun toApiModel(name: String?, members: List<Member>, teamId: String?, options: ConversationOptions): CreateConversationRequest
}

internal class ConversationMapperImpl(
    private val idMapper: IdMapper,
    private val conversationStatusMapper: ConversationStatusMapper
) : ConversationMapper {

    override fun fromApiModelToDaoModel(
        apiModel: ConversationResponse,
        groupCreation: Boolean,
        selfUserTeamId: TeamId?
    ): PersistedConversation =
        PersistedConversation(
            idMapper.fromApiToDao(apiModel.id),
            apiModel.name,
            apiModel.getConversationType(selfUserTeamId),
            apiModel.teamId,
            apiModel.getProtocolInfo(groupCreation),
            conversationStatusMapper.fromApiToDaoModel(apiModel.members.self.otrMutedStatus),
            apiModel.members.self.otrMutedRef?.let { Instant.parse(it) }?.toEpochMilliseconds() ?: 0,
            null,
            null
        )

    override fun fromApiModelToDaoModel(apiModel: ConvProtocol): PersistedProtocol = when (apiModel) {
        ConvProtocol.PROTEUS -> PersistedProtocol.PROTEUS
        ConvProtocol.MLS -> PersistedProtocol.MLS
    }

    override fun fromDaoModel(daoModel: PersistedConversation): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id),
        daoModel.name,
        daoModel.type.fromDaoModel(),
        daoModel.teamId?.let { TeamId(it) },
        conversationStatusMapper.fromDaoModel(daoModel.mutedStatus),
        daoModel.lastNotificationDate,
        daoModel.lastModifiedDate
    )

    override fun toDaoModel(welcomeEvent: Event.Conversation.MLSWelcome, groupId: String): ConversationEntity =
        ConversationEntity(
            idMapper.toDaoModel(welcomeEvent.conversationId),
            name = null,
            type = ConversationEntity.Type.GROUP,
            teamId = null,
            protocolInfo = ProtocolInfo.MLS(groupId, GroupState.ESTABLISHED),
            lastNotificationDate = null,
            lastModifiedDate = null
        )

    override fun toApiModel(name: String?, members: List<Member>, teamId: String?, options: ConversationOptions) =
        CreateConversationRequest(
            qualifiedUsers = if (options.protocol == ConversationOptions.Protocol.PROTEUS) members.map { idMapper.toApiModel(it.id) } else emptyList(),
            name = name,
            access = options.access.toList().map { toApiModel(it) },
            accessRole = options.accessRole.toList().map { toApiModel(it) },
            convTeamInfo = teamId?.let { ConvTeamInfo(false, it) },
            messageTimer = null,
            receiptMode = if (options.readReceiptsEnabled) ReceiptMode.ENABLED else ReceiptMode.DISABLED,
            conversationRole = ConversationDataSource.DEFAULT_MEMBER_ROLE,
            protocol = toApiModel(options.protocol)
        )

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

    private fun PersistedConversation.Type.fromDaoModel(): ConversationEntity.Type = when (this) {
        PersistedConversation.Type.SELF -> ConversationEntity.Type.SELF
        PersistedConversation.Type.ONE_ON_ONE -> ConversationEntity.Type.ONE_ON_ONE
        PersistedConversation.Type.GROUP -> ConversationEntity.Type.GROUP
    }

    private fun ConversationResponse.getProtocolInfo(groupCreation: Boolean): ProtocolInfo {
        return when (protocol) {
            ConvProtocol.MLS -> ProtocolInfo.MLS(
                groupId ?: "",
                if (groupCreation) GroupState.PENDING else GroupState.PENDING_WELCOME_MESSAGE
            )
            ConvProtocol.PROTEUS -> ProtocolInfo.Proteus
        }
    }

    private fun ConversationResponse.getConversationType(selfUserTeamId: TeamId?): PersistedConversation.Type {
        return when (type) {
            ConversationResponse.Type.SELF -> PersistedConversation.Type.SELF
            ConversationResponse.Type.GROUP -> {
                // Fake team 1:1 conversations
                val onlyOneOtherMember = members.otherMembers.size == 1
                val noCustomName = name.isNullOrBlank()
                val belongsToSelfTeam = selfUserTeamId != null && selfUserTeamId.value == teamId
                val isTeamOneOne = onlyOneOtherMember && noCustomName && belongsToSelfTeam
                if (isTeamOneOne) {
                    PersistedConversation.Type.ONE_ON_ONE
                } else {
                    PersistedConversation.Type.GROUP
                }
            }
            ConversationResponse.Type.ONE_TO_ONE,
            ConversationResponse.Type.INCOMING_CONNECTION,
            ConversationResponse.Type.WAIT_FOR_CONNECTION,
            -> PersistedConversation.Type.ONE_ON_ONE
        }
    }
}
