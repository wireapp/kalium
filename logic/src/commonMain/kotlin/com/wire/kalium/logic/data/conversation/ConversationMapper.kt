package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.ConversationResponse

interface ConversationMapper {
    fun fromApiModel(apiModel: ConversationResponse): Conversation
}

internal class ConversationMapperImpl(private val idMapper: IdMapper, private val memberMapper: MemberMapper) : ConversationMapper {

    override fun fromApiModel(apiModel: ConversationResponse): Conversation = Conversation(
        idMapper.fromApiModel(apiModel.id), apiModel.name, memberMapper.fromApiModel(apiModel.members)
    )
}
