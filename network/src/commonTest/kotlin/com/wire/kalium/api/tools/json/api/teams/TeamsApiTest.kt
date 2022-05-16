package com.wire.kalium.api.tools.json.api.teams

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.teams.TeamsApiImpl
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class TeamsApiTest: ApiTest {

    @Test
    fun givenAValidListOfTeamsIds_whenCallingGetTeamsLimitedTo_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val commaSeparatedListOfIds = LIST_OF_TEAM_IDS.joinToString(",")
            val networkClient = mockAuthenticatedNetworkClient(
                GET_LIMIT_TO_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertQueryExist("size")
                    assertQueryExist("ids")
                    assertQueryParameter("ids", hasValue = commaSeparatedListOfIds)
                    assertPathEqual("/$PATH_TEAMS")
                }
            )
            val teamsApi: TeamsApi = TeamsApiImpl(networkClient)
            teamsApi.getTeams(size = 10, option = TeamsApi.GetTeamsOption.LimitTo(LIST_OF_TEAM_IDS))
        }

    @Test
    fun givenAValidGetTeamsRequest_whenCallingGetTeamsStartFrom_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                GET_START_FROM_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertQueryExist("size")
                    assertQueryDoesNotExist("ids")
                    assertQueryParameter("start", hasValue = DUMMY_TEAM_ID)
                    assertPathEqual("/$PATH_TEAMS")
                }
            )
            val teamsApi: TeamsApi = TeamsApiImpl(networkClient)
            teamsApi.getTeams(size = 10, option = TeamsApi.GetTeamsOption.StartFrom(DUMMY_TEAM_ID))
        }

    @Test
    fun givenAValidGetTeamsFirstPageRequest_whenGettingTeamsMembers_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                GET_TEAM_MEMBER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertGet()
                    assertQueryExist("maxResults")
                    assertQueryParameter("maxResults", hasValue = "10")
                    assertPathEqual("/$PATH_TEAMS/$DUMMY_TEAM_ID/$PATH_MEMBERS")
                }
            )
            val teamsApi: TeamsApi = TeamsApiImpl(networkClient)
            teamsApi.getTeamMembers(DUMMY_TEAM_ID, limitTo = 10)
        }

    @Test
    fun givenADeleteConversationForTeamRequest_whenDeletingATeamConversationWithSuccess_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val conversationId = "96a6e8e4-6420-49db-aa83-2711edf7580d"
            val networkClient = mockAuthenticatedNetworkClient(
                "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertDelete()
                    assertPathEqual("/$PATH_TEAMS/$DUMMY_TEAM_ID/$PATH_CONVERSATIONS/$conversationId")
                }
            )
            val teamsApi: TeamsApi = TeamsApiImpl(networkClient)
            teamsApi.deleteConversation(conversationId, teamId = DUMMY_TEAM_ID)
        }

    private companion object {
        const val PATH_TEAMS = "teams"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_MEMBERS = "members"
        const val DUMMY_TEAM_ID = "770b0623-ffd5-4e08-8092-7a6b9b9ca3b4"
        val LIST_OF_TEAM_IDS = listOf(DUMMY_TEAM_ID)
        val GET_LIMIT_TO_CLIENT_RESPONSE = TeamsResponsesJson.GetTeams.validGetTeamsLimitTo(LIST_OF_TEAM_IDS)
        val GET_START_FROM_CLIENT_RESPONSE = TeamsResponsesJson.GetTeams.validGetTeamsStartFrom(DUMMY_TEAM_ID)
        val GET_TEAM_MEMBER_CLIENT_RESPONSE = TeamsResponsesJson.GetTeamsMembers.validGetTeamsMembers
    }
}
