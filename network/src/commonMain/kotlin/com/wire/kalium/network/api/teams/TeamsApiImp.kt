package com.wire.kalium.network.api.teams

import com.wire.kalium.network.api.NonQualifiedConversationId
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class TeamsApiImp(private val httpClient: HttpClient) : TeamsApi {

    override suspend fun deleteConversation(conversationId: NonQualifiedConversationId, teamId: TeamId): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.delete(
                path = "/$PATH_TEAMS/$teamId/$PATH_CONVERSATIONS/$conversationId"
            )
        }

    override suspend fun getTeams(size: Int?, option: TeamsApi.GetTeamsOption?): NetworkResponse<TeamsApi.TeamsResponse> =
        wrapKaliumResponse {
            when (option) {
                is TeamsApi.GetTeamsOption.StartFrom ->
                    httpClient.get(path = "/$PATH_TEAMS") {
                        size?.let { parameter(QUERY_KEY_SIZE, it) }
                        option.let { parameter(QUERY_KEY_START, it.teamId) }
                    }
                is TeamsApi.GetTeamsOption.LimitTo ->
                    httpClient.get(path = "/$PATH_TEAMS") {
                        size?.let { parameter(QUERY_KEY_SIZE, it) }
                        option.let { parameter(QUERY_KEY_IDS, it.teamIds.joinToString(",")) }
                    }
                null ->
                    httpClient.get(path = "/$PATH_TEAMS")
            }
        }

    override suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?): NetworkResponse<TeamsApi.TeamMemberList> =
        wrapKaliumResponse {
            httpClient.get(path = "/$PATH_TEAMS/$teamId/$PATH_MEMBERS") {
                limitTo?.let { parameter("maxResults", it) }
            }
        }

    private companion object {
        const val PATH_TEAMS = "teams"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_MEMBERS = "members"

        const val QUERY_KEY_START = "start"
        const val QUERY_KEY_SIZE = "size"
        const val QUERY_KEY_IDS = "ids"
    }
}
