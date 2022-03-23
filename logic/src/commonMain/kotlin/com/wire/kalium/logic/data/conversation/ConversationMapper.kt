package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationEntity as PersistedConversation

interface ConversationMapper {
    fun fromApiModelToDaoModel(apiModel: ConversationResponse, selfUserTeamId: TeamId?): PersistedConversation
    fun fromDaoModel(daoModel: PersistedConversation): Conversation
}

internal class ConversationMapperImpl(private val idMapper: IdMapper) : ConversationMapper {

    override fun fromApiModelToDaoModel(apiModel: ConversationResponse, selfUserTeamId: TeamId?): PersistedConversation =
        PersistedConversation(
            idMapper.fromApiToDao(apiModel.id), apiModel.name, apiModel.getConversationType(selfUserTeamId), apiModel.teamId
        )

    override fun fromDaoModel(daoModel: PersistedConversation): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id), daoModel.name, daoModel.type.fromDaoModel(), daoModel.teamId?.let { TeamId(it) }
    )

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
