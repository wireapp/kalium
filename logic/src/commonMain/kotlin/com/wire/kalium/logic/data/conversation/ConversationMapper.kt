package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.Conversation as PersistedConversation

interface ConversationMapper {
    fun fromApiModel(apiModel: ConversationResponse): Conversation
    fun fromApiModelToDaoModel(apiModel: ConversationResponse): PersistedConversation
    fun fromDaoModel(daoModel: PersistedConversation): Conversation
}

internal class ConversationMapperImpl(
    private val idMapper: IdMapper,
    private val legalHoldStatusMapper: LegalHoldStatusMapper
) : ConversationMapper {

    override fun fromApiModel(apiModel: ConversationResponse): Conversation = Conversation(
        idMapper.fromApiModel(apiModel.id), apiModel.name
    )

    override fun fromDaoModel(daoModel: PersistedConversation): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id), daoModel.name
    )

    override fun fromApiModelToDaoModel(apiModel: ConversationResponse): PersistedConversation = PersistedConversation(
        idMapper.fromApiToDao(apiModel.id), apiModel.name
    )

    private fun getOneOnOneConnectionState(apiModel: ConversationResponse) = when (apiModel.type) {
        ConversationResponse.Type.WAIT_FOR_CONNECTION -> Conversation.OneOne.ConnectionState.OUTGOING
        ConversationResponse.Type.INCOMING_CONNECTION -> Conversation.OneOne.ConnectionState.INCOMING
        else -> Conversation.OneOne.ConnectionState.ACCEPTED
    }
}
