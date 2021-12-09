package com.wire.kalium.logic

import com.wire.kalium.network.AuthenticatedNetworkContainer
import com.wire.kalium.network.api.conversation.ConversationResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ConversationScope(
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer
) {

    //  TODO: Map it to something instead of using the serialization models
    suspend fun getConversations(): Flow<List<ConversationResponse>> = flow {
        val firstConversationPage = authenticatedNetworkContainer.conversationApi
            .conversationsByBatch(null, 50)
            .resultBody.conversations

        emit(firstConversationPage)
    }
}
