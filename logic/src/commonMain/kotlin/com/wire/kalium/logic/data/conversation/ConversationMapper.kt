package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.ConversationResponse
import com.wire.kalium.persistence.dao.Conversation as PersistedConversation

interface ConversationMapper {
    fun fromDTO(apiModel: ConversationResponse): Conversation
    fun fromApiModelToDaoModel(apiModel: ConversationResponse): PersistedConversation

    fun fromEntity(daoModel: PersistedConversation): Conversation
    fun toEntity(conv: Conversation): PersistedConversation
}

internal class ConversationMapperImpl(private val idMapper: IdMapper, private val memberMapper: MemberMapper) : ConversationMapper {

    override fun fromDTO(apiModel: ConversationResponse): Conversation = Conversation(
        idMapper.fromApiModel(apiModel.id), apiModel.name
    )

    override fun fromEntity(daoModel: PersistedConversation): Conversation = Conversation(
        idMapper.fromDaoModel(daoModel.id), daoModel.name
    )

    override fun toEntity(conv: Conversation): PersistedConversation = with(conv) {
        PersistedConversation(idMapper.toDaoModel(id), name)
    }

    override fun fromApiModelToDaoModel(apiModel: ConversationResponse): PersistedConversation = PersistedConversation(
        idMapper.fromApiToDao(apiModel.id), apiModel.name
    )
}
