package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId

interface ConversationApi {

    suspend fun conversationsByBatch(queryStart: String?, querySize: Int): KaliumHttpResult<ConversationPagingResponse>

    suspend fun fetchConversationsDetails(queryStart: String?, queryIds: List<String>): KaliumHttpResult<ConversationPagingResponse>

    suspend fun removeConversationMember(userId: UserId, conversationId: ConversationId): KaliumHttpResult<Unit>

    suspend fun createNewConversation(createConversationRequest: CreateConversationRequest): KaliumHttpResult<ConversationResponse>

    suspend fun createOne2OneConversation(createConversationRequest: CreateConversationRequest): KaliumHttpResult<ConversationResponse>

    suspend fun addParticipant(addParticipantRequest: AddParticipantRequest, conversationId: ConversationId): KaliumHttpResult<AddParticipantResponse>
}
