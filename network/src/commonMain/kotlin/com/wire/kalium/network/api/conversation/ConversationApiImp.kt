package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse

class ConversationApiImp(private val httpClient: HttpClient) : ConversationApi {
    override suspend fun conversationsByBatch(queryStart: String?, querySize: Int): NetworkResponse<ConversationPagingResponse> =
        wrapKaliumResponse {
            httpClient.get(path = PATH_CONVERSATIONS) {
                queryStart?.let { parameter(QUERY_KEY_START, it) }
                parameter(QUERY_KEY_SIZE, querySize)
            }
        }

    override suspend fun fetchConversationsDetails(
        queryStart: String?,
        queryIds: List<String>
    ): NetworkResponse<ConversationPagingResponse> = wrapKaliumResponse {
        httpClient.get(path = PATH_CONVERSATIONS) {
            queryStart?.let { parameter(QUERY_KEY_START, it) }
            parameter(QUERY_KEY_IDS, queryIds)
        }
    }

    /**
     * returns 200 Member removed and 204 No change
     */
    override suspend fun removeConversationMember(userId: UserId, conversationId: ConversationId): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(
                path = "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}" +
                        PATH_MEMBERS +
                        "/${userId.domain}/${userId.value}"
            )
        }

    /**
     * returns 201 when a new conversation is created or 200 if the conversation already existed
     */
    override suspend fun createNewConversation(createConversationRequest: CreateConversationRequest): NetworkResponse<ConversationResponse> =
        wrapKaliumResponse {
            httpClient.post(path = PATH_CONVERSATIONS) {
                body = createConversationRequest
            }
        }

    override suspend fun createOne2OneConversation(createConversationRequest: CreateConversationRequest): NetworkResponse<ConversationResponse> =
        wrapKaliumResponse {
            httpClient.post(path = "$PATH_CONVERSATIONS$PATH_ONE_2_ONE") {
                body = createConversationRequest
            }
        }

    /**
     * returns 200 conversation created or 204 conversation unchanged
     */
    override suspend fun addParticipant(
        addParticipantRequest: AddParticipantRequest,
        conversationId: ConversationId
    ): NetworkResponse<AddParticipantResponse> {
        val response =
            httpClient.post<HttpResponse>(path = "$PATH_CONVERSATIONS/${conversationId.value}$PATH_MEMBERS$PATH_V2") {
                body = addParticipantRequest
            }

        return when (response.status.value) {
            200 -> wrapKaliumResponse<AddParticipantResponse.UserAdded> { response }
            204 -> wrapKaliumResponse<AddParticipantResponse.ConversationUnchanged> { response }
            else -> wrapKaliumResponse { response }
        }
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_MEMBERS = "/members"
        const val PATH_ONE_2_ONE = "/one2one"
        const val PATH_V2 = "/v2"

        const val QUERY_KEY_START = "start"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_KEY_IDS = "ids"
    }
}
