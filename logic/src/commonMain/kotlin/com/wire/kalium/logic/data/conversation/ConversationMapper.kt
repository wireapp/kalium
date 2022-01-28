package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.Conversation as ConversationPersisted

interface ConversationMapper {
    fun fromApiModel(apiModel: ConversationResponse): Conversation
    fun fromApiModelToDao(apiModel: ConversationResponse): ConversationPersisted
    fun fromDaoModel(daoModel: ConversationPersisted): Conversation
}

internal class ConversationMapperImpl(private val idMapper: IdMapper, private val memberMapper: MemberMapper) : ConversationMapper {

    override fun fromApiModel(apiModel: ConversationResponse): Conversation = Conversation(
        idMapper.fromApiModel(apiModel.id), apiModel.name
    )

    override fun fromDaoModel(daoModel: ConversationPersisted): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id), daoModel.name
    )

    override fun fromApiModelToDao(apiModel: ConversationResponse): ConversationPersisted = ConversationPersisted(
        idMapper.fromApiToDao(apiModel.id), apiModel.name
    )
}
