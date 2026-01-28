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

package com.wire.kalium.network.api.base.authenticated

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
import io.mockative.Mockable

@Mockable
interface TeamsApi {

    suspend fun deleteConversation(conversationId: NonQualifiedConversationId, teamId: TeamId): NetworkResponse<Unit>
    suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?, pagingState: String? = null): NetworkResponse<TeamMemberListPaginated>
    suspend fun getTeamMembersByIds(teamId: TeamId, teamMemberIdList: TeamMemberIdList): NetworkResponse<TeamMemberListNonPaginated>
    suspend fun getTeamMember(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<TeamMemberDTO>
    suspend fun getTeamInfo(teamId: TeamId): NetworkResponse<TeamDTO>
    suspend fun whiteListedServices(teamId: TeamId, size: Int = DEFAULT_SERVICES_SIZE): NetworkResponse<ServiceDetailResponse>
    suspend fun approveLegalHoldRequest(teamId: TeamId, userId: NonQualifiedUserId, password: String?): NetworkResponse<Unit>
    suspend fun fetchLegalHoldStatus(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<LegalHoldStatusResponse>

    companion object {
        const val DEFAULT_SERVICES_SIZE = 100 // this number is copied from the web client
    }
}
