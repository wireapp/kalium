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
package com.wire.kalium.api.v15.teams

import com.wire.kalium.api.ApiTest
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.network.api.v15.authenticated.TeamsApiV15
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

internal class TeamsApiV15Test : ApiTest() {

    @Test
    fun givenTeam_whenGettingApps_thenRequestAndFullProfilesAreCorrect() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            APPS_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertNoQueryParams()
                assertPathEqual("/teams/$TEAM_ID/apps")
            }
        )

        val response = TeamsApiV15(networkClient).getTeamApps(TEAM_ID)

        val success = assertIs<NetworkResponse.Success<List<UserProfileDTO>>>(response)
        assertEquals(1, success.value.size)
        assertEquals("app-id", success.value.single().id.value)
        assertEquals("wire.example", success.value.single().id.domain)
        assertEquals(UserTypeDTO.APP, success.value.single().type)
        assertEquals("Productivity", success.value.single().app?.category)
    }

    private companion object {
        const val TEAM_ID = "team-id"
        const val APPS_RESPONSE = """
            [
              {
                "qualified_id": { "id": "app-id", "domain": "wire.example" },
                "id": "app-id",
                "name": "Calendar App",
                "handle": "calendar-app",
                "team": "team-id",
                "accent_id": 1,
                "assets": [],
                "deleted": false,
                "email": null,
                "expires_at": null,
                "service": null,
                "supported_protocols": ["mls"],
                "legalhold_status": "disabled",
                "type": "app",
                "app": {
                  "description": "Schedules meetings",
                  "category": "Productivity"
                }
              }
            ]
        """
    }
}
