package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.persistence.dao.TeamDAO

interface TeamRepository {
    suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Unit>
}

internal class TeamDataSource(
    private val teamDAO: TeamDAO,
    private val teamMapper: TeamMapper,
    private val teamsApi: TeamsApi
) : TeamRepository {

    override suspend fun fetchTeamById(teamId: TeamId): Either<CoreFailure, Unit> = suspending {
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
    }
}
