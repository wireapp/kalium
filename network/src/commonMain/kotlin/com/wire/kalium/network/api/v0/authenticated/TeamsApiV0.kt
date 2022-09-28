package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.model.NonQualifiedConversationId
import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal open class TeamsApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : TeamsApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun deleteConversation(conversationId: NonQualifiedConversationId, teamId: TeamId): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete("$PATH_TEAMS/$teamId/$PATH_CONVERSATIONS/$conversationId")
        }

    override suspend fun getTeamInfo(teamId: TeamId): NetworkResponse<TeamDTO> =
        wrapKaliumResponse {
            httpClient.get("$PATH_TEAMS/$teamId")
        }

    override suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?): NetworkResponse<TeamsApi.TeamMemberList> =
        wrapKaliumResponse {
            httpClient.get("$PATH_TEAMS/$teamId/$PATH_MEMBERS") {
                limitTo?.let { parameter("maxResults", it) }
            }
        }

    override suspend fun getTeamMember(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<TeamsApi.TeamMemberDTO> =
        wrapKaliumResponse {
            httpClient.get("$PATH_TEAMS/$teamId/$PATH_MEMBERS/$userId")
        }

    private companion object {
        const val PATH_TEAMS = "teams"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_MEMBERS = "members"
    }
}
