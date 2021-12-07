package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.wrapKaliumResponse
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class ConversationApiImp(private val httpClient: HttpClient) : ConversationApi {
    override suspend fun conversationsByBatch(queryStart: String?, querySize: Int): KaliumHttpResult<ConversationPagingResponse> =
        wrapKaliumResponse<ConversationPagingResponse> {
            httpClient.get<HttpResponse>(path = PATH_CONVERSATIONS) {
                queryStart?.let { parameter(QUERY_KEY_START, it) }
                parameter(QUERY_KEY_SIZE, querySize)
            }.receive()
        }

    override suspend fun fetchConversationsDetails(
        queryStart: String?,
        queryIds: List<String>
    ): KaliumHttpResult<ConversationPagingResponse> = wrapKaliumResponse<ConversationPagingResponse> {
        httpClient.get<HttpResponse>(path = PATH_CONVERSATIONS) {
            queryStart?.let { parameter(QUERY_KEY_START, it) }
            parameter(QUERY_IDS, queryIds)
        }.receive()
    }

    /**
     * returns 200 Member removed and 204 No change
     */
    override suspend fun removeConversationMember(userId: UserId, conversationId: ConversationId): KaliumHttpResult<Unit> =
        wrapKaliumResponse {
            httpClient.delete<HttpResponse>(
                path = "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}" +
                        PATH_Members +
                        "/${userId.domain}/${userId.value}"
            ).receive()
        }

    /**
     * returns 201 when a new conversation is created,
     * and 200 when the conversation already existed
     */
    override suspend fun createNewConversation(createConversationRequest: CreateConversationRequest): KaliumHttpResult<ConversationResponse> =
        wrapKaliumResponse {
            httpClient.post<HttpResponse>(path = PATH_CONVERSATIONS) {
                body = createConversationRequest
            }.receive()
        }

    override suspend fun createOne2OneConversation(createConversationRequest: CreateConversationRequest): KaliumHttpResult<ConversationResponse> =
        wrapKaliumResponse {
            httpClient.post<HttpResponse>(path = "$PATH_CONVERSATIONS$PATH_ONE_2_ONE") {
                body = createConversationRequest
            }.receive()
        }


    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_Members = "/members"
        const val PATH_ONE_2_ONE = "/one2one"


        const val QUERY_KEY_START = "start"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_IDS = "ids"
    }
}
