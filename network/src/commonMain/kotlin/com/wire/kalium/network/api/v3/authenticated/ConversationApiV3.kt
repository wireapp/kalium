package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequestV3
import com.wire.kalium.network.api.base.authenticated.conversation.GlobalTeamConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTOV3
import com.wire.kalium.network.api.base.authenticated.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.RequestMapper
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.mapSuccess
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.Clock
import okio.IOException

internal open class ConversationApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val requestMapper: RequestMapper
) : ConversationApiV2(authenticatedNetworkClient) {

    /**
     * returns 201 when a new conversation is created or 200 if the conversation already existed
     */
    override suspend fun createNewConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse {
        httpClient.post(ConversationApiV0.PATH_CONVERSATIONS) {
            setBody(requestMapper.toApiV3(createConversationRequest))
        }
    }

    override suspend fun createOne2OneConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse {
        httpClient.post("${ConversationApiV0.PATH_CONVERSATIONS}/$PATH_ONE_2_ONE") {
            setBody(requestMapper.toApiV3(createConversationRequest))
        }
    }

    override suspend fun fetchGroupInfo(conversationId: QualifiedID): NetworkResponse<ByteArray> =
        wrapKaliumResponse {
            httpClient.get(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_GROUP_INFO"
            )
        }

    override suspend fun fetchGlobalTeamConversationDetails(selfUserId: UserId, teamId: TeamId): NetworkResponse<ConversationResponse> {
        return wrapKaliumResponse<GlobalTeamConversationResponse> {
            httpClient.get("$PATH_TEAM/$teamId/$PATH_CONVERSATIONS/$PATH_GLOBAL")
        }.mapSuccess { response ->
            ConversationResponse(
                response.creator ?: "",
                ConversationMembersResponse(
                    ConversationMemberDTO.Self(
                        selfUserId,
                        "wire_default",
                    ),
                    emptyList()
                ),
                response.name,
                response.id,
                response.groupId,
                response.epoch,
                ConversationResponse.Type.GLOBAL_TEAM,
                0,
                response.teamId,
                ConvProtocol.MLS,
                Clock.System.now().toString(),
                response.mlsCipherSuiteTag,
                response.access,
                emptySet()
            )
        }
    }

    override suspend fun updateAccessRole(
        conversationId: ConversationId,
        conversationAccessInfoDTO: ConversationAccessInfoDTO
    ): NetworkResponse<UpdateConversationAccessResponse> = try {
        httpClient.put("${ConversationApiV0.PATH_CONVERSATIONS}/${conversationId.domain}/${conversationId.value}/$PATH_ACCESS") {
            setBody(requestMapper.toApiV3(conversationAccessInfoDTO))
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

    companion object {
        const val PATH_TEAM = "teams"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_GLOBAL = "global"
        const val PATH_GROUP_INFO = "groupinfo"
    }
}
