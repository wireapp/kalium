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
package com.wire.kalium.api.v10.teams

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.authenticated.teams.TeamCollaboratorDTO
import com.wire.kalium.network.api.v10.authenticated.TeamsApiV10
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

internal class TeamsApiV10Test : ApiTest() {

    @Test
    fun givenTeam_whenGettingCollaborators_thenRequestAndResponseAreCorrect() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            COLLABORATORS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertPathEqual("/teams/$TEAM_ID/collaborators")
            }
        )

        val response = TeamsApiV10(networkClient).getTeamCollaborators(TEAM_ID)

        val success = assertIs<NetworkResponse.Success<List<TeamCollaboratorDTO>>>(response)
        assertEquals(listOf("collaborator-id"), success.value.map { it.nonQualifiedUserId })
    }

    @Test
    fun givenApiBeforeV15_whenGettingTeamApps_thenReturnApiNotSupported() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.OK,
            assertion = { fail("Unsupported endpoint must not make a network request") }
        )

        val response = TeamsApiV10(networkClient).getTeamApps(TEAM_ID)

        assertIs<APINotSupported>(assertIs<NetworkResponse.Error>(response).kException)
    }

    private companion object {
        const val TEAM_ID = "team-id"
        const val COLLABORATORS_RESPONSE = """
            [
              {
                "user": "collaborator-id",
                "team": "team-id",
                "permissions": { "copy": 0, "self": 0 },
                "unused_future_field": true
              }
            ]
        """
    }
}
