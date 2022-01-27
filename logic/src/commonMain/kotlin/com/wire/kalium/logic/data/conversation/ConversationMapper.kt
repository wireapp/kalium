package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.network.api.user.details.UserDetailsResponse

interface ConversationMapper {
    fun fromApiModel(apiModel: ConversationResponse, oneOneContactInfo: UserDetailsResponse?): Conversation
}

internal class ConversationMapperImpl(
    private val idMapper: IdMapper,
    private val memberMapper: MemberMapper,
    private val legalHoldStatusMapper: LegalHoldStatusMapper
) : ConversationMapper {

    // TODO Replace mapping, it will use DB models, not remote data
    override fun fromApiModel(apiModel: ConversationResponse, oneOneContactInfo: UserDetailsResponse?): Conversation {
        val id = idMapper.fromApiModel(apiModel.id)
        val membersInfo = memberMapper.fromApiModel(apiModel.members)
        return when (apiModel.type) {
            ConversationResponse.Type.SELF -> Conversation.Self(id, membersInfo, "") //TODO: Get self name
            ConversationResponse.Type.GROUP -> Conversation.Group(id, membersInfo, apiModel.name)
            else -> mapOneOnOneConversation(apiModel, membersInfo, oneOneContactInfo, id)
        }
    }

    private fun mapOneOnOneConversation(
        apiModel: ConversationResponse,
        membersInfo: MembersInfo,
        oneOneContactInfo: UserDetailsResponse?,
        id: QualifiedID
    ): Conversation.OneOne {
        val contactInfo = oneOneContactInfo!!
        val connectionState = getOneOnOneConnectionState(apiModel)
        return Conversation.OneOne(
            id,
            membersInfo,
            contactInfo.name,
            connectionState,
            // TODO Figure out if external or guest
            Conversation.OneOne.FederationStatus.NONE,
            legalHoldStatusMapper.fromApiModel(contactInfo.legalHoldStatus)
        )
    }

    private fun getOneOnOneConnectionState(apiModel: ConversationResponse) = when (apiModel.type) {
        ConversationResponse.Type.WAIT_FOR_CONNECTION -> Conversation.OneOne.ConnectionState.OUTGOING
        ConversationResponse.Type.INCOMING_CONNECTION -> Conversation.OneOne.ConnectionState.INCOMING
        else -> Conversation.OneOne.ConnectionState.ACCEPTED
    }
}
