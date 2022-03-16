package com.wire.kalium.logic.data.team

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamRepositoryTest {

    @Mock
    private val teamDAO = configure(mock(classOf<TeamDAO>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val userDAO = configure(mock(classOf<UserDAO>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val teamMapper = mock(classOf<TeamMapper>())

    @Mock
    private val userMapper = mock(classOf<UserMapper>())

    @Mock
    private val teamsApi = mock(classOf<TeamsApi>())

    private lateinit var teamRepository: TeamRepository

    @BeforeTest
    fun setUp() {
        teamRepository = TeamDataSource(
            teamDAO = teamDAO, teamMapper = teamMapper, teamsApi = teamsApi, userDAO = userDAO, userMapper = userMapper
        )
    }

    @Test
    fun givenSelfUserExists_whenGettingTeamInfo_thenTeamInfoShouldBeSuccessful() = runTest {
        val team = TeamDTO(
            creator = "creator", icon = AssetId(), name = "teamName", id = "teamId", iconKey = null, binding = false
        )

        given(teamsApi)
            .suspendFunction(teamsApi::getTeamInfo)
            .whenInvokedWith(oneOf("teamId"))
            .then { NetworkResponse.Success(value = team, headers = mapOf(), httpCode = 200) }

        val teamEntity = TeamEntity(
            id = "teamId", name = "teamName"
        )

        given(teamMapper)
            .function(teamMapper::fromDtoToEntity)
            .whenInvokedWith(oneOf(team))
            .then { teamEntity }

        val result = teamRepository.fetchTeamById(teamId = "teamId")

        // Verifies that teamDAO insertTeam was called with the correct mapped values
        verify(teamDAO)
            .suspendFunction(teamDAO::insertTeam)
            .with(oneOf(teamEntity))
            .wasInvoked(exactly = once)


        // Verifies that when fetching team by id, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenTeamApiFails_whenGettingTeamInfo_thenTheFailureIsPropagated() = runTest {
        given(teamsApi)
            .suspendFunction(teamsApi::getTeamInfo)
            .whenInvokedWith(any())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val result = teamRepository.fetchTeamById(teamId = "teamId")

        result.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }
    }
}
