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
package com.wire.kalium.network.api.authenticated.teams

import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.NonQualifiedUserId
import com.wire.kalium.network.api.model.TeamId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamMemberListPaginated(
    // Please note that this is intentionally cased differently form the has_more in TeamsResponse
    // because the backend response contains a different casing
    @SerialName("hasMore") val hasMore: Boolean,
    @SerialName("members") val members: List<TeamMemberDTO>,
    @SerialName("pagingState") val pagingState: String? = null
)

@Serializable
data class TeamMemberListNonPaginated(
    // Please note that this is intentionally cased differently form the has_more in TeamsResponse
    // because the backend response contains a different casing
    @SerialName("hasMore") val hasMore: Boolean,
    @SerialName("members") val members: List<TeamMemberDTO>
)

@Serializable
data class TeamMemberDTO(
    @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId,
    @SerialName("created_by") val createdBy: NonQualifiedUserId?,
    @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusDTO?,
    @SerialName("created_at") val createdAt: String?,
    @SerialName("permissions") val permissions: TeamPermissions?
)

@Serializable
data class TeamPermissions(
    @SerialName("copy") val copy: Int,
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
