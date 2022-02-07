package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
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

    override fun fromApiModel(apiModel: ConversationResponse): Conversation {
        val convId = idMapper.fromApiModel(apiModel.id)
        return when (apiModel.type) {
            ConversationResponse.Type.GROUP -> Conversation.Group(convId, apiModel.name)
            ConversationResponse.Type.SELF -> Conversation.Group(convId, apiModel.name)
            ConversationResponse.Type.ONE_TO_ONE,
            ConversationResponse.Type.WAIT_FOR_CONNECTION,
            ConversationResponse.Type.INCOMING_CONNECTION -> {
                val connectionState = getOneOnOneConnectionState(apiModel)
                Conversation.OneOne(
                    convId,
                    apiModel.name,
                    connectionState,
                    Conversation.OneOne.FederationStatus.NONE,
                    LegalHoldStatus.DISABLED // TODO Fetch
                )
            }
        }
    }

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
}
