package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TeamRepository {
    suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Team>
    suspend fun fetchMembersByTeamId(teamId: TeamId, userDomain: String): Either<CoreFailure, Unit>
    suspend fun getTeam(teamId: TeamId): Flow<Team?>
}

internal class TeamDataSource(
    private val userDAO: UserDAO,
    private val teamDAO: TeamDAO,
    private val teamsApi: TeamsApi,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper()
) : TeamRepository {

    override suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Team> = wrapApiRequest {
        teamsApi.getTeamInfo(teamId = teamId.value)
    }.map { teamDTO ->
        teamMapper.fromDtoToEntity(teamDTO)
    }.map { teamEntity ->
        teamDAO.insertTeam(team = teamEntity)

        teamMapper.fromDaoModelToTeam(teamEntity)
    }

    override suspend fun fetchMembersByTeamId(teamId: TeamId, userDomain: String): Either<CoreFailure, Unit> = wrapApiRequest {
        teamsApi.getTeamMembers(
            teamId = teamId.value,
            limitTo = null
        )
    }.map { teamMemberList ->
        /**
         * If hasMore is true, then this result should be discarded and not stored locally,
         * otherwise the user will see random team members when opening the search UI.
         * If the result has has_more field set to false, then these users are stored locally to be used in a search later.
         */
        if (teamMemberList.hasMore.not()) {
            teamMemberList.members.map { teamMember ->
                userMapper.fromTeamMemberToDaoModel(
                    teamId = teamId,
                    teamMemberDTO = teamMember,
                    userDomain = userDomain
                )
            }
        } else {
            listOf()
        }
    }.flatMap { teamMembers ->
        wrapStorageRequest {
            userDAO.upsertTeamMembers(teamMembers)
        }
    }

    override suspend fun getTeam(teamId: TeamId): Flow<Team?> =
        teamDAO.getTeamById(teamId.value)
            .map {
                it?.let {
                    teamMapper.fromDaoModelToTeam(it)
                }
            }
}
