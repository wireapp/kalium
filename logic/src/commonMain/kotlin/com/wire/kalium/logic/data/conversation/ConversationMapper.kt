package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.ConversationResponse

class ConversationMapper {

    fun fromApiModel(apiModel: ConversationResponse): Conversation = Conversation(apiModel.id, apiModel.name)
}
