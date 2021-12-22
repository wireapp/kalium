package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.ConversationApi
import com.wire.kalium.network.utils.isSuccessful

class ConversationRepository(
    private val conversationApi: ConversationApi,
    private val conversationMapper: ConversationMapper
) {

    suspend fun getConversationList(): List<Conversation> {
        val conversationsResponse = conversationApi.conversationsByBatch(null, 100)
        if (!conversationsResponse.isSuccessful()) {
            TODO("Error handling. Repository layer, a good place to use Either<Failure,Success> ?")
        }
        return conversationsResponse.value.conversations.map(conversationMapper::fromApiModel)
    }

}
