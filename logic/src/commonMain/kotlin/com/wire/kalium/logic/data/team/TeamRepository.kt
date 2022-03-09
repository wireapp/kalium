package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.persistence.dao.TeamDAO
import kotlinx.coroutines.flow.firstOrNull

interface TeamRepository {
    suspend fun getTeam(): Either<CoreFailure, Unit>
}

class TeamDataSource(
    private val teamDAO: TeamDAO,
    private val teamMapper: TeamMapper,
    private val teamsApi: TeamsApi,
    private val userRepository: UserRepository
) : TeamRepository {

    override suspend fun getTeam(): Either<CoreFailure, Unit> = suspending {
        userRepository.getSelfUser().firstOrNull()?.team?.let { teamId ->
            wrapApiRequest {
                teamsApi.getTeams(
                    size = 1,
                    option = TeamsApi.GetTeamsOption.StartFrom(teamId = teamId)
                ).mapSuccess { teamsResponse ->
                    teamMapper.fromApiModelToDaoModel(
                        team = teamsResponse.teams.first()
                    )
                }
            }.coFold({
                Either.Left(it)
            }, { team ->
                teamDAO.insertTeam(team = team)
                kaliumLogger.d("--- Team Info ---")
                kaliumLogger.d("Team ID: ${team.id}")
                kaliumLogger.d("Team Name: ${team.name}")
                Either.Right(Unit)
            })
        } ?: Either.Left(CoreFailure.MissingSelfUser)
    }
}
