package com.wire.kalium.network.api.teams

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.KaliumHttpResult
import com.wire.kalium.network.api.NonQualifiedConversationId
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.wrapKaliumResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse

class TeamsApiImp(private val httpClient: HttpClient) : TeamsApi {

    override suspend fun deleteConversation(conversationId: NonQualifiedConversationId, teamId: TeamId): KaliumHttpResult<Unit> =
        wrapKaliumResponse {
            httpClient.delete<HttpResponse>(
                path = "/$PATH_TEAMS/$teamId/$PATH_CONVERSATIONS/$conversationId"
            ).receive()
        }

    override suspend fun getTeams(size: Int?, option: TeamsApi.GetTeamsOption?): KaliumHttpResult<TeamsApi.TeamsResponse> =
        wrapKaliumResponse {
            when (option) {
                is TeamsApi.GetTeamsOption.StartFrom ->
                    httpClient.get<HttpResponse>(path = "/$PATH_TEAMS") {
                        size?.let { parameter(QUERY_KEY_SIZE, it) }
                        option?.let { parameter(QUERY_KEY_START, it.teamId) }
                    }.receive()
                is TeamsApi.GetTeamsOption.LimitTo ->
                    httpClient.get<HttpResponse>(path = "/$PATH_TEAMS") {
                        size?.let { parameter(QUERY_KEY_SIZE, it) }
                        option?.let { parameter(QUERY_KEY_IDS, it.teamIds.joinToString(",")) }
                    }.receive()
                null ->
                    httpClient.get<HttpResponse>(path = "/$PATH_TEAMS").receive()
            }
        }

    override suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?): KaliumHttpResult<TeamsApi.TeamMemberList> =
        wrapKaliumResponse {
            httpClient.get<HttpResponse>(path = "/$PATH_TEAMS/$teamId/$PATH_MEMBERS") {
                limitTo?.let { parameter("maxResults", it) }
            }.receive()
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
