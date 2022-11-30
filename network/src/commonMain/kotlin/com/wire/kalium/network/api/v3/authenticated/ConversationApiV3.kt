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
import com.wire.kalium.network.api.base.authenticated.conversation.GlobalTeamConversationResponse
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.mapSuccess
import kotlinx.datetime.Clock

internal open class ConversationApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV2(authenticatedNetworkClient) {

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

    companion object {
        const val PATH_TEAM = "teams"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_GLOBAL = "global"
        const val PATH_GROUP_INFO = "groupinfo"
    }
}
