/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.api.v10

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.authenticated.teams.CollaboratorPermissionDTO
import com.wire.kalium.network.api.authenticated.teams.TeamCollaboratorDTO
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.v10.authenticated.TeamsApiV10
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

internal class TeamsApiV10Test : ApiTest() {

    @Test
    fun givenApiV10_whenGettingTeamCollaborators_theRequestShouldBeConfiguredCorrectlyAndParsed() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            COLLABORATORS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/$PATH_TEAMS/$DUMMY_TEAM_ID/$PATH_COLLABORATORS")
            }
        )
        val teamsApi: TeamsApi = TeamsApiV10(networkClient)
        val response = teamsApi.getTeamCollaborators(DUMMY_TEAM_ID)

        assertIs<NetworkResponse.Success<List<TeamCollaboratorDTO>>>(response)
        assertEquals(
            listOf(
                TeamCollaboratorDTO(
                    nonQualifiedUserId = DUMMY_USER_ID,
                    teamId = DUMMY_TEAM_ID,
                    permissions = listOf(CollaboratorPermissionDTO.CREATE_TEAM_CONVERSATION)
                )
            ),
            response.value
        )
    }

    @Test
    fun givenApiV10_whenGettingTeamApps_thenRequestShouldStillNotBeSupported() = runTest {
        val networkClient = mockAuthenticatedNetworkClient("", statusCode = HttpStatusCode.OK)
        val teamsApi: TeamsApi = TeamsApiV10(networkClient)
        val response = teamsApi.getTeamApps(DUMMY_TEAM_ID)
        assertFalse(response.isSuccessful())
    }

    private companion object {
        const val PATH_TEAMS = "teams"
        const val PATH_COLLABORATORS = "collaborators"
        const val DUMMY_TEAM_ID = "770b0623-ffd5-4e08-8092-7a6b9b9ca3b4"
        const val DUMMY_USER_ID = "96a6e8e4-6420-49db-aa83-2711edf7580d"
        val COLLABORATORS_RESPONSE = """
            [
                {
                    "user": "$DUMMY_USER_ID",
                    "team": "$DUMMY_TEAM_ID",
                    "permissions": ["create_team_conversation"]
                }
            ]
        """.trimIndent()
    }
}
