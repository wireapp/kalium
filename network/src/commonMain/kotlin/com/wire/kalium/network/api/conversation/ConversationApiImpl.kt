package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.conversation.model.ConversationAccessData
import com.wire.kalium.network.api.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.pagination.PaginationRequest
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode

class ConversationApiImpl internal constructor(private val authenticatedNetworkClient: AuthenticatedNetworkClient) : ConversationApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun fetchConversationsIds(pagingState: String?): NetworkResponse<ConversationPagingResponse> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_LIST_IDS") {
                setBody(PaginationRequest(pagingState = pagingState, size = MAX_CONVERSATION_DETAILS_COUNT))
            }
        }

    override suspend fun fetchConversationsListDetails(conversationsIds: List<ConversationId>): NetworkResponse<ConversationResponseDTO> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_CONVERSATIONS_LIST/$PATH_V2") {
                setBody(ConversationsDetailsRequest(conversationsIds = conversationsIds))
            }
        }

    /**
     * returns 200 Member removed and 204 No change
     */
    override suspend fun removeConversationMember(userId: UserId, conversationId: ConversationId): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/${userId.domain}/${userId.value}"
            )
        }

    override suspend fun fetchConversationDetails(conversationId: ConversationId): NetworkResponse<ConversationResponse> =
        wrapKaliumResponse {
            httpClient.get(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}"
            )
        }

    /**
     * returns 201 when a new conversation is created or 200 if the conversation already existed
     */
    override suspend fun createNewConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse {
        httpClient.post(PATH_CONVERSATIONS) {
            setBody(createConversationRequest)
        }
    }

    override suspend fun createOne2OneConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse {
        httpClient.post("$PATH_CONVERSATIONS/$PATH_ONE_2_ONE") {
            setBody(createConversationRequest)
        }
    }

    /**
     * returns 200 conversation created or 204 conversation unchanged
     */
    override suspend fun addParticipant(
        addParticipantRequest: AddParticipantRequest,
        conversationId: ConversationId
    ): NetworkResponse<AddParticipantResponse> {
        val response = httpClient.post("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_MEMBERS/$PATH_V2") {
            setBody(addParticipantRequest)
        }
        return when (response.status) {
            HttpStatusCode.OK -> wrapKaliumResponse<AddParticipantResponse.UserAdded> { response }
            HttpStatusCode.NoContent -> wrapKaliumResponse<AddParticipantResponse.ConversationUnchanged> { response }
            else -> wrapKaliumResponse { response }
        }
    }

    override suspend fun updateConversationMemberState(
        memberUpdateRequest: MemberUpdateDTO,
        conversationId: ConversationId,
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_SELF") {
            setBody(memberUpdateRequest)
        }
    }

    override suspend fun updateAccessRole(
        conversationId: ConversationId,
        conversationAccessData: ConversationAccessData
    ): NetworkResponse<UpdateConversationAccessResponse> {
        return httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_ACCESS") {
            setBody(conversationAccessData)
        }.let { httpResponse ->
            when (httpResponse.status) {
                HttpStatusCode.NoContent -> NetworkResponse.Success(UpdateConversationAccessResponse.AccessUnchanged, httpResponse)
                else -> wrapKaliumResponse<EventContentDTO.Conversation.AccessUpdate> { httpResponse }
                    .mapSuccess {
                        UpdateConversationAccessResponse.AccessUpdated(it)
                    }
            }
        }
    }

    private companion object {
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_SELF = "self"
        const val PATH_MEMBERS = "members"
        const val PATH_ONE_2_ONE = "one2one"
        const val PATH_V2 = "v2"
        const val PATH_CONVERSATIONS_LIST = "list"
        const val PATH_LIST_IDS = "list-ids"
        const val PATH_ACCESS = "access"

        const val QUERY_KEY_START = "start"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_KEY_IDS = "qualified_ids"

        const val MAX_CONVERSATION_DETAILS_COUNT = 200 // fixme: adjust "size" value later on when server can handle the load
    }
}
