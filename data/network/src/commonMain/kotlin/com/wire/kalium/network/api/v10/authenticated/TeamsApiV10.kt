/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v10.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.teams.TeamCollaboratorDTO
import com.wire.kalium.network.api.model.TeamId
import com.wire.kalium.network.api.v9.authenticated.TeamsApiV9
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.get

internal open class TeamsApiV10 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : TeamsApiV9(authenticatedNetworkClient) {

    override suspend fun getTeamCollaborators(teamId: TeamId): NetworkResponse<List<TeamCollaboratorDTO>> = wrapRequest {
        authenticatedNetworkClient.httpClient.get("teams/$teamId/collaborators")
    }
}
