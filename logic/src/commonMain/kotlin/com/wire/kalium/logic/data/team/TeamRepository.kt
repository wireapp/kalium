package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.persistence.dao.TeamDAO
import kotlinx.coroutines.flow.firstOrNull

interface TeamRepository {
    suspend fun syncSelfTeam(): Either<CoreFailure, Unit>
}

class TeamDataSource(
    private val teamDAO: TeamDAO,
    private val teamMapper: TeamMapper,
    private val teamsApi: TeamsApi,
    private val userRepository: UserRepository
) : TeamRepository {

    override suspend fun syncSelfTeam(): Either<CoreFailure, Unit> = suspending {
        userRepository.getSelfUser().firstOrNull()?.team?.let { teamId ->
            wrapApiRequest {
                teamsApi.getTeamInfo(
                    teamId = teamId
                )
            }.map { team ->
                teamMapper.fromApiModelToDaoModel(
                    team = team
                )
            }.coFold({
                Either.Left(it)
            }, { team ->
                teamDAO.insertTeam(team = team)
                Either.Right(Unit)
            })
        } ?: Either.Left(CoreFailure.MissingSelfUser)
    }
}
