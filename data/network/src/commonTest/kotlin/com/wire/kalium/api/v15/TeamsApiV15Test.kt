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
package com.wire.kalium.api.v15

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.v15.authenticated.TeamsApiV15
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class TeamsApiV15Test : ApiTest() {

    @Test
    fun givenApiV15_whenGettingTeamApps_theRequestShouldBeConfiguredCorrectlyAndParsed() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            APPS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/$PATH_TEAMS/$DUMMY_TEAM_ID/$PATH_APPS")
            }
        )
        val teamsApi: TeamsApi = TeamsApiV15(networkClient)
        val response = teamsApi.getTeamApps(DUMMY_TEAM_ID)

        assertIs<NetworkResponse.Success<List<UserProfileDTO>>>(response)
        assertEquals(1, response.value.size)
        assertEquals(DUMMY_USER_ID, response.value.first().id.value)
    }

    private companion object {
        const val PATH_TEAMS = "teams"
        const val PATH_APPS = "apps"
        const val DUMMY_TEAM_ID = "770b0623-ffd5-4e08-8092-7a6b9b9ca3b4"
        const val DUMMY_USER_ID = "96a6e8e4-6420-49db-aa83-2711edf7580d"
        val APPS_RESPONSE = """
            [
                {
                    "qualified_id": {"id": "$DUMMY_USER_ID", "domain": "domain.com"},
                    "name": "An App",
                    "handle": null,
                    "team": "$DUMMY_TEAM_ID",
                    "accent_id": 0,
                    "assets": [],
                    "deleted": false,
                    "email": null,
                    "expires_at": null,
                    "id": "$DUMMY_USER_ID",
                    "service": null,
                    "supported_protocols": null,
                    "legalhold_status": "disabled",
                    "type": "app",
                    "app": {"description": "an app", "category": "productivity"}
                }
            ]
        """.trimIndent()
    }
}
