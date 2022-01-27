package com.wire.kalium.network.api.teams

import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.NonQualifiedConversationId
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.user.LegalHoldStatus
import com.wire.kalium.network.utils.NetworkResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface TeamsApi {

    @Serializable
    data class TeamsResponse(
        @SerialName("has_more") val hasMore: Boolean,
        val teams: List<Team>
    )

    @Serializable
    data class Team(
        val creator: String,
        val icon: AssetId,
        val name: String,
        val id: TeamId,
        @SerialName("icon_key") val iconKey: String?,
        val binding: Boolean
    )

    @Serializable
    data class TeamMemberList(
        // Please note that this is intentionally cased differently form the has_more in TeamsResponse
        // because the backend response contains a different casing
        @SerialName("hasMore") val hasMore: Boolean,
        val members: List<TeamMember>
    )

    @Serializable
    data class TeamMember(
        @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId,
        @SerialName("created_by") val createdBy: NonQualifiedUserId?,
        @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatus?,
        @SerialName("created_at") val createdAt: String?,
        val permissions: Permissions?
    )

    @Serializable
    data class Permissions(
        val copy: Int,
        @SerialName("self") val own: Int
    )

    sealed interface GetTeamsOptionsInterface
    sealed class GetTeamsOption : GetTeamsOptionsInterface {
        /**
         * StartFrom parameters
         * @param teamId the id of the team to continue the query from
         */
        data class StartFrom(val teamId: TeamId) : GetTeamsOption()

        /**
         * LimitTo parameters
         * @param teamIds a list of max 32 team ids to limit the query to
         */
        data class LimitTo(val teamIds: List<TeamId>) : GetTeamsOption()
    }

    suspend fun deleteConversation(conversationId: NonQualifiedConversationId, teamId: TeamId): NetworkResponse<Unit>
    suspend fun getTeams(size: Int?, option: GetTeamsOption?): NetworkResponse<TeamsResponse>
    suspend fun getTeamMembers(teamId: TeamId, limitTo: Int?): NetworkResponse<TeamMemberList>
}
