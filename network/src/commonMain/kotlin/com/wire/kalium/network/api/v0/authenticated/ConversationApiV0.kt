/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationRenameRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.base.authenticated.conversation.guestroomlink.GenerateGuestRoomLinkResponse
import com.wire.kalium.network.api.base.authenticated.conversation.messagetimer.ConversationMessageTimerDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.AddServiceResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.JoinConversationRequest
import com.wire.kalium.network.api.base.model.PaginationRequest
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.ServiceAddedResponse
import com.wire.kalium.network.api.base.model.SubconversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import okio.IOException

@Suppress("TooManyFunctions")
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
        addParticipantRequest: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedResponse> = try {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_MEMBERS/$PATH_V2") {
            setBody(addParticipantRequest)
        }.let { response ->
            handleConversationMemberAddedResponse(response)
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun addService(
        addServiceRequest: AddServiceRequest,
        conversationId: ConversationId
    ): NetworkResponse<ServiceAddedResponse> = try {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_BOTS") {
            setBody(addServiceRequest)
        }.let { response ->
            handleServiceAddedResponse(response)
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
    ): NetworkResponse<ConversationMemberRemovedResponse> = try {
        httpClient.delete(
            "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/${userId.domain}/${userId.value}"
        ).let { response ->
            when (response.status) {
                HttpStatusCode.OK -> wrapKaliumResponse<EventContentDTO.Conversation.MemberLeaveDTO> { response }
                    .mapSuccess { ConversationMemberRemovedResponse.Changed(it) }

                HttpStatusCode.NoContent -> NetworkResponse.Success(ConversationMemberRemovedResponse.Unchanged, response)
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

    override suspend fun updateAccess(
        conversationId: ConversationId,
        updateConversationAccessRequest: UpdateConversationAccessRequest
    ): NetworkResponse<UpdateConversationAccessResponse> = try {
        httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_ACCESS") {
            setBody(updateConversationAccessRequest)
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

    override suspend fun updateConversationName(
        conversationId: QualifiedID,
        conversationName: String
    ): NetworkResponse<ConversationRenameResponse> = try {
        httpClient.put(
            "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_NAME"
        ) {
            setBody(ConversationRenameRequest(conversationName))
        }.let { response ->
            when (response.status) {
                HttpStatusCode.OK -> wrapKaliumResponse<EventContentDTO.Conversation.ConversationRenameDTO> { response }
                    .mapSuccess { ConversationRenameResponse.Changed(it) }

                HttpStatusCode.NoContent -> NetworkResponse.Success(ConversationRenameResponse.Unchanged, response)
                else -> wrapKaliumResponse { response }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun fetchGroupInfo(conversationId: QualifiedID): NetworkResponse<ByteArray> =
        NetworkResponse.Error(
            APINotSupported("MLS: fetchGroupInfo api is only available on API V3")
        )

    override suspend fun joinConversation(
        code: String,
        key: String,
        uri: String?
    ): NetworkResponse<ConversationMemberAddedResponse> =
        httpClient.preparePost("$PATH_CONVERSATIONS/$PATH_JOIN") {
            setBody(JoinConversationRequest(code, key, uri))
        }.execute { httpResponse ->
            handleConversationMemberAddedResponse(httpResponse)
        }

    override suspend fun fetchLimitedInformationViaCode(code: String, key: String): NetworkResponse<LimitedConversationInfo> =
        wrapKaliumResponse {
            httpClient.get("$PATH_CONVERSATIONS/$PATH_JOIN") {
                parameter(QUERY_KEY_CODE, code)
                parameter(QUERY_KEY_KEY, key)
            }
        }

    override suspend fun fetchSubconversationDetails(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<SubconversationResponse> =
        NetworkResponse.Error(
            APINotSupported("MLS: fetchSubconversationDetails api is only available on API V3")
        )

    override suspend fun fetchSubconversationGroupInfo(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<ByteArray> =
        NetworkResponse.Error(
            APINotSupported("MLS: fetchSubconversationGroupInfo api is only available on API V3")
        )

    override suspend fun deleteSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId,
        deleteRequest: SubconversationDeleteRequest
    ): NetworkResponse<Unit> =
        NetworkResponse.Error(
            APINotSupported("MLS: deleteSubconversation api is only available on API V3")
        )

    override suspend fun leaveSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<Unit> =
        NetworkResponse.Error(
            APINotSupported("MLS: leaveSubconversation api is only available on API V3")
        )

    protected suspend fun handleConversationMemberAddedResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<ConversationMemberAddedResponse> =
        when (httpResponse.status) {
            HttpStatusCode.OK -> {
                wrapKaliumResponse<EventContentDTO.Conversation.MemberJoinDTO> { httpResponse }
                    .mapSuccess { ConversationMemberAddedResponse.Changed(it) }
            }

            HttpStatusCode.NoContent -> {
                NetworkResponse.Success(ConversationMemberAddedResponse.Unchanged, httpResponse)
            }

            else -> {
                wrapKaliumResponse { httpResponse }
            }
        }

    private suspend fun handleServiceAddedResponse(
        httpResponse: HttpResponse
    ): NetworkResponse<ServiceAddedResponse> =
        when (httpResponse.status) {
            HttpStatusCode.NoContent -> {
                NetworkResponse.Success(ServiceAddedResponse.Unchanged, httpResponse)
            }

            HttpStatusCode.Created -> {
                wrapKaliumResponse<AddServiceResponse> { httpResponse }
                    .mapSuccess { ServiceAddedResponse.Changed(it.event) }
            }

            else -> {
                wrapKaliumResponse { httpResponse }
            }
        }

    override suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: ConversationReceiptModeDTO
    ): NetworkResponse<UpdateConversationReceiptModeResponse> = try {
        httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_RECEIPT_MODE") {
            setBody(receiptMode)
        }.let { httpResponse ->
            when (httpResponse.status) {
                HttpStatusCode.NoContent -> NetworkResponse.Success(
                    UpdateConversationReceiptModeResponse.ReceiptModeUnchanged,
                    httpResponse
                )

                else -> wrapKaliumResponse<EventContentDTO.Conversation.ReceiptModeUpdate> { httpResponse }
                    .mapSuccess {
                        UpdateConversationReceiptModeResponse.ReceiptModeUpdated(it)
                    }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun generateGuestRoomLink(conversationId: ConversationId): NetworkResponse<GenerateGuestRoomLinkResponse> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_CODE")
        }

    override suspend fun revokeGuestRoomLink(conversationId: ConversationId): NetworkResponse<Unit> = wrapKaliumResponse {
        httpClient.delete("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_CODE")
    }

    override suspend fun updateMessageTimer(conversationId: ConversationId, messageTimer: Long?): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.put("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_MESSAGE_TIMER") {
                setBody(ConversationMessageTimerDTO(messageTimer))
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
        const val PATH_NAME = "name"
        const val PATH_JOIN = "join"
        const val PATH_RECEIPT_MODE = "receipt-mode"
        const val PATH_CODE = "code"
        const val PATH_MESSAGE_TIMER = "message-timer"
        const val PATH_BOTS = "bots"
        const val QUERY_KEY_CODE = "code"
        const val QUERY_KEY_KEY = "key"
        const val QUERY_KEY_START = "start"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_KEY_IDS = "qualified_ids"

        const val MAX_CONVERSATION_DETAILS_COUNT = 1000
    }
}
