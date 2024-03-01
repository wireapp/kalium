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

import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.NonQualifiedConversationId
import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.model.ServiceDetailResponse
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TeamsApi {

    @Serializable
    data class TeamMemberListPaginated(
        // Please note that this is intentionally cased differently form the has_more in TeamsResponse
        // because the backend response contains a different casing
        @SerialName("hasMore") val hasMore: Boolean,
<<<<<<< HEAD
=======
        val members: List<TeamMemberDTO>,
        @SerialName("pagingState") val pagingState: String? = null
    )

    @Serializable
    data class TeamMemberListNonPaginated(
        // Please note that this is intentionally cased differently form the has_more in TeamsResponse
        // because the backend response contains a different casing
        @SerialName("hasMore") val hasMore: Boolean,
>>>>>>> 11bc5e6c20 (fix: missing json value pagingState when getting team member by ID (#2576))
        val members: List<TeamMemberDTO>
    )

    @Serializable
    data class TeamMemberDTO(
        @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId,
        @SerialName("created_by") val createdBy: NonQualifiedUserId?,
        @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusDTO?,
        @SerialName("created_at") val createdAt: String?,
        val permissions: Permissions?
    )

    @Serializable
    data class Permissions(
        val copy: Int,
        @SerialName("self") val own: Int
    )

    @Serializable
    data class TeamMemberIdList(
        @SerialName("user_ids") val userIds: List<NonQualifiedUserId>
    )
    @Serializable
    data class PasswordRequest(
        @SerialName("password") val password: String?
    )

    sealed interface GetTeamsOptionsInterface

    /**
     *
     * Represents the options that can be passed to [getTeams]
     *
     */

    sealed class GetTeamsOption : GetTeamsOptionsInterface {

        /**
         * @constructor Creates a `StartFrom` option
         * @property[teamId] the id of the team to continue the query from
         */

        data class StartFrom(val teamId: TeamId) : GetTeamsOption()

        /**
         * @constructor Creates a `LimitTo` option
         * @property[teamIds] a list of *max 32* team ids used to limit the query
         */
        data class LimitTo(val teamIds: List<TeamId>) : GetTeamsOption()
    }

    suspend fun deleteConversation(conversationId: NonQualifiedConversationId, teamId: TeamId): NetworkResponse<Unit>

<<<<<<< HEAD
    suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?): NetworkResponse<TeamMemberList>
    suspend fun getTeamMembersByIds(teamId: TeamId, teamMemberIdList: TeamMemberIdList): NetworkResponse<TeamMemberList>
=======
    suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?, pagingState: String? = null): NetworkResponse<TeamMemberListPaginated>
    suspend fun getTeamMembersByIds(teamId: TeamId, teamMemberIdList: TeamMemberIdList): NetworkResponse<TeamMemberListNonPaginated>
>>>>>>> 11bc5e6c20 (fix: missing json value pagingState when getting team member by ID (#2576))
    suspend fun getTeamMember(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<TeamMemberDTO>
    suspend fun getTeamInfo(teamId: TeamId): NetworkResponse<TeamDTO>
    suspend fun whiteListedServices(teamId: TeamId, size: Int = DEFAULT_SERVICES_SIZE): NetworkResponse<ServiceDetailResponse>
    suspend fun approveLegalHoldRequest(teamId: TeamId, userId: NonQualifiedUserId, password: String?): NetworkResponse<Unit>
    suspend fun fetchLegalHoldStatus(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<LegalHoldStatusResponse>

    companion object {
        const val DEFAULT_SERVICES_SIZE = 100 // this number is copied from the web client
    }
}
