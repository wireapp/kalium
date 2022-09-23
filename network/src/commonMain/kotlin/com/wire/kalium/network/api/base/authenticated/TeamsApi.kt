package com.wire.kalium.network.api.base.authenticated

import com.wire.kalium.network.api.base.model.NonQualifiedConversationId
import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TeamsApi {

    @Serializable
    data class TeamsResponse(
        @SerialName("has_more") val hasMore: Boolean,
        val convTeamInfos: List<TeamDTO>
    )


    @Serializable
    data class TeamMemberList(
        // Please note that this is intentionally cased differently form the has_more in TeamsResponse
        // because the backend response contains a different casing
        @SerialName("hasMore") val hasMore: Boolean,
        val members: List<TeamMemberDTO>
    )

    @Serializable
    data class TeamMemberDTO(
        @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId,
        @SerialName("created_by") val createdBy: NonQualifiedUserId?,
        @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusResponse?,
        @SerialName("created_at") val createdAt: String?,
        val permissions: Permissions?
    )

    @Serializable
    data class Permissions(
        val copy: Int, @SerialName("self") val own: Int
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

    /**
     * Gets a list of teams
     *
     * @return a list of teams, represented by [TeamsApi.TeamsResponse] wrapped in a [NetworkResponse]
     * @param[size] limits the number of teams returned
     * @param[option] one of [GetTeamsOption.LimitTo] or [GetTeamsOption.StartFrom]
     */
    suspend fun getTeams(size: Int?, option: GetTeamsOption?): NetworkResponse<TeamsResponse>
    suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?): NetworkResponse<TeamMemberList>
    suspend fun getTeamMember(teamId: TeamId, userId: NonQualifiedUserId): NetworkResponse<TeamMemberDTO>
    suspend fun getTeamInfo(teamId: TeamId): NetworkResponse<TeamDTO>
}
