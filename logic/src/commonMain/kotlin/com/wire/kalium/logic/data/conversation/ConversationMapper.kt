package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.ConversationEntity

interface ConversationMapper {
    fun fromDTO(apiModel: ConversationResponse): Conversation
    fun fromApiModelToDaoModel(apiModel: ConversationResponse): ConversationEntity

    fun fromEntity(daoModel: ConversationEntity): Conversation
    fun toEntity(conv: Conversation): ConversationEntity
}

internal class ConversationMapperImpl(private val idMapper: IdMapper, private val memberMapper: MemberMapper) : ConversationMapper {

    override fun fromDTO(apiModel: ConversationResponse): Conversation = Conversation(
        idMapper.fromApiModel(apiModel.id), apiModel.name
    )

    override fun fromEntity(daoModel: ConversationEntity): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id), daoModel.name
    )

    override fun toEntity(conv: Conversation): ConversationEntity = with(conv) {
        ConversationEntity(idMapper.toDaoModel(id), name)
    }

    override fun fromApiModelToDaoModel(apiModel: ConversationResponse): ConversationEntity = ConversationEntity(
        idMapper.fromApiToDao(apiModel.id), apiModel.name
    )
}
