package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.PaginationRequest
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import okio.IOException

internal open class ConversationApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApi {

    protected val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun fetchConversationsIds(
        pagingState: String?
    ): NetworkResponse<ConversationPagingResponse> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_LIST_IDS") {
                setBody(PaginationRequest(pagingState = pagingState, size = MAX_CONVERSATION_DETAILS_COUNT))
            }
        }

    override suspend fun fetchConversationsListDetails(
        conversationsIds: List<ConversationId>
    ): NetworkResponse<ConversationResponseDTO> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_CONVERSATIONS_LIST/$PATH_V2") {
                setBody(ConversationsDetailsRequest(conversationsIds = conversationsIds))
            }
        }

    override suspend fun fetchConversationDetails(
        conversationId: ConversationId
    ): NetworkResponse<ConversationResponse> =
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
    override suspend fun addMember(
        request: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedDTO> = try {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/v2") {
            setBody(request)
        }.let { response ->
            when (response.status) {
                HttpStatusCode.OK -> wrapKaliumResponse<ConversationMemberAddedDTO.Changed> { response }
                HttpStatusCode.NoContent -> NetworkResponse.Success(ConversationMemberAddedDTO.Unchanged, response)
                else -> wrapKaliumResponse { response }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    /**
     * returns 200 Member removed and 204 No change
     */
    override suspend fun removeMember(
        userId: UserId,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberRemovedDTO> = try {
        httpClient.delete(
            "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/${userId.domain}/${userId.value}"
        ).let { response ->
            when (response.status) {
                HttpStatusCode.OK -> wrapKaliumResponse<ConversationMemberRemovedDTO.Changed> { response }
                HttpStatusCode.NoContent -> NetworkResponse.Success(ConversationMemberRemovedDTO.Unchanged, response)
                else -> wrapKaliumResponse { response }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun updateConversationMemberState(
        memberUpdateRequest: MemberUpdateDTO,
        conversationId: ConversationId,
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put(
            "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_SELF"
        ) {
            setBody(memberUpdateRequest)
        }
    }

    override suspend fun updateAccessRole(
        conversationId: ConversationId,
        conversationAccessInfoDTO: ConversationAccessInfoDTO
    ): NetworkResponse<UpdateConversationAccessResponse> = try {
        httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_ACCESS") {
            setBody(conversationAccessInfoDTO)
        }.let { httpResponse ->
            when (httpResponse.status) {
                HttpStatusCode.NoContent -> NetworkResponse.Success(UpdateConversationAccessResponse.AccessUnchanged, httpResponse)
                else -> wrapKaliumResponse<EventContentDTO.Conversation.AccessUpdate> { httpResponse }
                    .mapSuccess {
                        UpdateConversationAccessResponse.AccessUpdated(it)
                    }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        conversationMemberRoleDTO: ConversationMemberRoleDTO
    ): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.put(
            "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/${userId.domain}/${userId.value}"
        ) {
            setBody(conversationMemberRoleDTO)
        }
    }

    protected companion object {
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

        const val MAX_CONVERSATION_DETAILS_COUNT = 1000
    }
}
