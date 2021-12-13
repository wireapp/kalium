package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.conversation.ConversationApi

class ConversationRepository(
    private val conversationApi: ConversationApi,
    private val conversationMapper: ConversationMapper
) {

    //TODO: Rework and add error handling after NetworkResponse changes in PR #151
    // Repository layer, a good place to use Either<Failure,Success> ?
    suspend fun getConversationList(): List<Conversation> =
        conversationApi.conversationsByBatch(null, 100).resultBody
            .conversations.map(conversationMapper::fromApiModel)

}
