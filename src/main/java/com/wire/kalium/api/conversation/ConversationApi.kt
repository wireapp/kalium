package com.wire.kalium.api.conversation

import com.wire.kalium.api.KaliumHttpResult

interface ConversationApi {

    suspend fun conversationsByBatch(queryStart: String, querySize: Int): KaliumHttpResult<ConversationPagingResponse>

    suspend fun fetchConversationsDetails(queryStart: String, queryIds: List<String>): KaliumHttpResult<ConversationPagingResponse>
}
