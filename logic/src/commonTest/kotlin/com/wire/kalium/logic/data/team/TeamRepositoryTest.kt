package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.oneOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamRepositoryTest {

    @Mock
    private val teamDAO = mock(classOf<TeamDAO>())

    @Mock
    private val teamMapper = mock(classOf<TeamMapper>())

    @Mock
    private val teamsApi = mock(classOf<TeamsApi>())

    private lateinit var teamRepository: TeamRepository

    @BeforeTest
    fun setUp() {
        teamRepository = TeamDataSource(
            teamDAO = teamDAO,
            teamMapper = teamMapper,
            teamsApi = teamsApi
        )
    }

    @Test
    fun givenSelfUserExists_whenGettingTeamInfo_thenTeamInfoShouldBeReturned() = runTest {
        val team = TeamsApi.Team(
            creator = "creator",
            icon = AssetId(),
            name = "teamName",
            id = "teamId",
            iconKey = null,
            binding = false
        )

        given(teamsApi)
            .suspendFunction(teamsApi::getTeamInfo)
            .whenInvokedWith(oneOf("teamId"))
            .then {
                NetworkResponse.Success(
                    value = team,
                    headers = mapOf(),
                    httpCode = 200
                )
            }

        val teamEntity = TeamEntity(
            id = "teamId",
            name = "teamName"
        )

        given(teamMapper)
            .function(teamMapper::fromApiModelToDaoModel)
            .whenInvokedWith(oneOf(team))
            .then { teamEntity }

        given(teamDAO)
            .coroutine { insertTeam(team = teamEntity) }
            .then { } // returns Unit

        val result = teamRepository.fetchTeamById(teamId = "teamId")
        assertEquals(Either.Right(Unit), result)
    }
}
