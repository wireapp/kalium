/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.authenticated.teams.PasswordRequest
import com.wire.kalium.network.api.authenticated.teams.TeamMemberDTO
import com.wire.kalium.network.api.authenticated.teams.TeamMemberIdList
import com.wire.kalium.network.api.authenticated.teams.TeamMemberListNonPaginated
import com.wire.kalium.network.api.authenticated.teams.TeamMemberListPaginated
import com.wire.kalium.network.api.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.model.NonQualifiedConversationId
import com.wire.kalium.network.api.model.NonQualifiedUserId
import com.wire.kalium.network.api.model.ServiceDetailResponse
import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.network.api.model.TeamId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

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

    override suspend fun whiteListedServices(teamId: TeamId, size: Int): NetworkResponse<ServiceDetailResponse> = wrapKaliumResponse {
        httpClient.get("$PATH_TEAMS/$teamId/$PATH_SERVICES/$PATH_WHITELISTED") {
            parameter("size", size)
        }
    }

    override suspend fun getTeamMembers(
        teamId: TeamId,
        limitTo: Int?,
        pagingState: String?
    ): NetworkResponse<TeamMemberListPaginated> =
        wrapKaliumResponse<TeamMemberListPaginated> {
            httpClient.get("$PATH_TEAMS/$teamId/$PATH_MEMBERS") {
                limitTo?.let { parameter("maxResults", it) }
                pagingState?.let { parameter("pagingState", it) }
            }
        }

    override suspend fun getTeamMembersByIds(
        teamId: TeamId,
        teamMemberIdList: TeamMemberIdList
    ): NetworkResponse<TeamMemberListNonPaginated> = wrapKaliumResponse {
        httpClient.post("$PATH_TEAMS/$teamId/$PATH_MEMBERS_BY_IDS") {
            setBody(teamMemberIdList)
        }
    }

    override suspend fun getTeamMember(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<TeamMemberDTO> =
        wrapKaliumResponse {
            httpClient.get("$PATH_TEAMS/$teamId/$PATH_MEMBERS/$userId")
        }

    override suspend fun approveLegalHoldRequest(teamId: TeamId, userId: NonQualifiedUserId, password: String?): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.put("$PATH_TEAMS/$teamId/$PATH_LEGAL_HOLD/$userId/$PATH_APPROVE") {
                setBody(PasswordRequest(password))
            }
        }

    override suspend fun fetchLegalHoldStatus(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<LegalHoldStatusResponse> =
        wrapKaliumResponse {
            httpClient.get("$PATH_TEAMS/$teamId/$PATH_LEGAL_HOLD/$userId")
        }

    private companion object {
        const val PATH_TEAMS = "teams"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_MEMBERS = "members"
        const val PATH_MEMBERS_BY_IDS = "get-members-by-ids-using-post"
        const val PATH_SERVICES = "services"
        const val PATH_WHITELISTED = "whitelisted"
        const val PATH_LEGAL_HOLD = "legalhold"
        const val PATH_APPROVE = "approve"
    }
}
