package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.network.api.conversation.ConvProtocol
import com.wire.kalium.network.api.conversation.ConvTeamInfo
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.conversation.CreateConversationRequest
import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.api.model.ConversationAccessRole
import kotlinx.coroutines.flow.firstOrNull
import com.wire.kalium.persistence.dao.ConversationEntity as PersistedConversation
import com.wire.kalium.persistence.dao.ConversationEntity.Protocol as PersistedProtocol
import com.wire.kalium.persistence.dao.ConversationEntity.GroupState as PersistedGroupState

interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, groupCreation: Boolean, selfUserTeamId: TeamId?): PersistedConversation
    fun fromApiModelToDaoModel(apiModel: ConvProtocol): PersistedProtocol
    fun fromDaoModel(daoModel: PersistedConversation): Conversation
    fun toApiModel(access: ConverationOptions.Access): ConversationAccess
    fun toApiModel(accessRole: ConverationOptions.AccessRole): ConversationAccessRole
    fun toApiModel(protocol: ConverationOptions.Protocol): ConvProtocol
    fun toApiModel(name: String, members: List<Member>, teamId: String?, options: ConverationOptions): CreateConversationRequest
}

internal class ConversationMapperImpl(private val idMapper: IdMapper) : ConversationMapper {

    override fun fromApiModelToDaoModel(apiModel: ConversationResponse, groupCreation: Boolean, selfUserTeamId: TeamId?): PersistedConversation =
        PersistedConversation(
            idMapper.fromApiToDao(apiModel.id),
            apiModel.name,
            apiModel.getConversationType(selfUserTeamId),
            apiModel.teamId,
            apiModel.groupId,
            groupState = if (groupCreation) PersistedGroupState.PENDING else PersistedGroupState.PENDING_WELCOME_MESSAGE,
            fromApiModelToDaoModel(apiModel.protocol)
        )

    override fun fromApiModelToDaoModel(apiModel: ConvProtocol): PersistedProtocol = when (apiModel) {
        ConvProtocol.PROTEUS -> PersistedProtocol.PROTEUS
        ConvProtocol.MLS -> PersistedProtocol.MLS
    }

    override fun fromDaoModel(daoModel: PersistedConversation): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id), daoModel.name, daoModel.type.fromDaoModel(), daoModel.teamId?.let { TeamId(it) }, daoModel.groupId
    )

    override fun toApiModel(name: String, members: List<Member>, teamId: String?, options: ConverationOptions) =
        CreateConversationRequest(
            if (options.protocol == ConverationOptions.Protocol.PROTEUS) members.map { idMapper.toApiModel(it.id) } else emptyList(),
            name,
            options.access.toList().map { toApiModel(it) },
            options.accessRole.toList().map { toApiModel(it) },
            teamId?.let { ConvTeamInfo(false, it) },
            null,
            if (options.readReceiptsEnabled) 1 else 0,
            ConversationDataSource.DEFAULT_MEMBER_ROLE,
            toApiModel(options.protocol)
        )

    override fun toApiModel(access: ConverationOptions.Access): ConversationAccess = when (access) {
        ConverationOptions.Access.PRIVATE -> ConversationAccess.PRIVATE
        ConverationOptions.Access.CODE -> ConversationAccess.CODE
        ConverationOptions.Access.INVITE -> ConversationAccess.INVITE
        ConverationOptions.Access.LINK -> ConversationAccess.LINK
    }

    override fun toApiModel(access: ConverationOptions.AccessRole): ConversationAccessRole = when (access) {
        ConverationOptions.AccessRole.TEAM_MEMBER -> ConversationAccessRole.TEAM_MEMBER
        ConverationOptions.AccessRole.NON_TEAM_MEMBER -> ConversationAccessRole.NON_TEAM_MEMBER
        ConverationOptions.AccessRole.GUEST -> ConversationAccessRole.GUEST
        ConverationOptions.AccessRole.SERVICE -> ConversationAccessRole.SERVICE
    }

    override fun toApiModel(protocol: ConverationOptions.Protocol): ConvProtocol = when (protocol) {
        ConverationOptions.Protocol.PROTEUS -> ConvProtocol.PROTEUS
        ConverationOptions.Protocol.MLS -> ConvProtocol.MLS
    }

    private fun PersistedConversation.Type.fromDaoModel(): Conversation.Type = when (this) {
        PersistedConversation.Type.SELF -> Conversation.Type.SELF
        PersistedConversation.Type.ONE_ON_ONE -> Conversation.Type.ONE_ON_ONE
        PersistedConversation.Type.GROUP -> Conversation.Type.GROUP
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
