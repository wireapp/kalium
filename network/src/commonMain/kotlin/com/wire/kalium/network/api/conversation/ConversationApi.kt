package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.models.backend.ConversationId
import com.wire.kalium.models.backend.UserId

interface ConversationApi {

    suspend fun conversationsByBatch(queryStart: String?, querySize: Int): KaliumHttpResult<ConversationPagingResponse>

    suspend fun fetchConversationsDetails(queryStart: String?, queryIds: List<String>): KaliumHttpResult<ConversationPagingResponse>

    suspend fun removeConversationMember(userId: UserId, conversationId: ConversationId): KaliumHttpResult<Unit>
}
