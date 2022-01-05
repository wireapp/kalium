package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.network.api.conversation.ConversationResponse

class ConversationMapper(private val idMapper: IdMapper, private val memberMapper: MemberMapper) {

    fun fromApiModel(apiModel: ConversationResponse): Conversation = Conversation(
        idMapper.fromApiModel(apiModel.id), apiModel.name, memberMapper.fromApiModel(apiModel.members)
    )
}
