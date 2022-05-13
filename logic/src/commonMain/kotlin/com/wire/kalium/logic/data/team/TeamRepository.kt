package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.UserDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TeamRepository {
    suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Unit>
    suspend fun fetchMembersByTeamId(teamId: TeamId, userDomain: String): Either<CoreFailure, Unit>
    suspend fun getTeam(teamId: TeamId): Flow<Team?>
}

internal class TeamDataSource(
    private val userDAO: UserDAO,
    private val teamDAO: TeamDAO,
    private val teamsApi: TeamsApi,
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val teamMapper: TeamMapper = MapperProvider.teamMapper(),
) : TeamRepository {

    override suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Unit> = wrapApiRequest {
        teamsApi.getTeamInfo(teamId = teamId)
    }.map { teamDTO ->
        teamMapper.fromDtoToEntity(teamDTO)
    }.map { teamEntity ->
        teamDAO.insertTeam(team = teamEntity)
    }

    override suspend fun fetchMembersByTeamId(teamId: TeamId, userDomain: String): Either<CoreFailure, Unit> = wrapApiRequest {
        teamsApi.getTeamMembers(
            teamId = teamId,
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
                    teamMember = teamMember,
                    userDomain = userDomain
                )
            }
        } else {
            listOf()
        }
    }.flatMap { teamMembers ->
        // TODO: catch storage exceptions: https://github.com/wireapp/kalium/pull/275
        userDAO.insertUsers(teamMembers)
        Either.Right(Unit)
    }

    override suspend fun getTeam(teamId: TeamId): Flow<Team?> =
        teamDAO.getTeamById(teamId)
            .map {
                it?.let {
                    teamMapper.fromDaoModelToTeam(it)
                }
            }
}
