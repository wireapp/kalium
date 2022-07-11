package com.wire.kalium.logic.data.team

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
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
    fun givenSelfUserExists_whenFetchingTeamInfo_thenTeamInfoShouldBeSuccessful() = runTest {
        val teamDto = TestTeam.dto(
            id = "teamId",
            name = "teamName"
        )

        val team = Team(
            id = "teamId",
            name = "teamName"
        )

        given(teamsApi)
            .suspendFunction(teamsApi::getTeamInfo)
            .whenInvokedWith(oneOf("teamId"))
            .then { NetworkResponse.Success(value = teamDto, headers = mapOf(), httpCode = 200) }

        val teamEntity = TeamEntity(id = "teamId", name = "teamName")

        given(teamMapper)
            .function(teamMapper::fromDtoToEntity)
            .whenInvokedWith(oneOf(teamDto))
            .then { teamEntity }

        given(teamMapper)
            .function(teamMapper::fromDaoModelToTeam)
            .whenInvokedWith(oneOf(teamEntity))
            .then { team }

        teamMapper.fromDaoModelToTeam(teamEntity)

        val result = teamRepository.fetchTeamById(teamId = TeamId("teamId"))

        // Verifies that teamDAO insertTeam was called with the correct mapped values
        verify(teamDAO)
            .suspendFunction(teamDAO::insertTeam)
            .with(oneOf(teamEntity))
            .wasInvoked(exactly = once)

        // Verifies that when fetching team by id, it succeeded
        result.shouldSucceed { returnTeam ->
            assertEquals(team, returnTeam)
        }
    }

    @Test
    fun givenTeamApiFails_whenFetchingTeamInfo_thenTheFailureIsPropagated() = runTest {
        given(teamsApi)
            .suspendFunction(teamsApi::getTeamInfo)
            .whenInvokedWith(any())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val result = teamRepository.fetchTeamById(teamId = TeamId("teamId"))

        result.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }
    }

    @Test
    fun givenTeamIdAndUserDomain_whenFetchingTeamMembers_thenTeamMembersShouldBeSuccessful() = runTest {
        val teamMember = TestTeam.memberDTO(
            nonQualifiedUserId = "teamMember1"
        )

        val teamMembersList = TeamsApi.TeamMemberList(
            hasMore = false,
            members = listOf(
                teamMember
            )
        )

        val mappedTeamMember = UserEntity(
            id = QualifiedIDEntity(
                value = "teamMember1",
                domain = "userDomain"
            ),
            name = null,
            handle = null,
            email = null,
            phone = null,
            accentId = 1,
            team = "teamId",
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.NONE,
            userTypEntity = UserTypeEntity.EXTERNAL
        )

        given(teamsApi)
            .suspendFunction(teamsApi::getTeamMembers)
            .whenInvokedWith(oneOf("teamId"), oneOf(null))
            .thenReturn(NetworkResponse.Success(value = teamMembersList, headers = mapOf(), httpCode = 200))

        given(userMapper)
            .invocation { userMapper.fromTeamMemberToDaoModel(teamId = TeamId("teamId"), teamMember, "userDomain") }
            .then { mappedTeamMember }

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain")

        // Verifies that userDAO insertUsers was called with the correct mapped values
        verify(userDAO)
            .suspendFunction(userDAO::upsertTeamMembers)
            .with(oneOf(listOf(mappedTeamMember)))
            .wasInvoked(exactly = once)

        // Verifies that when fetching members by team id, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenTeamApiFails_whenFetchingTeamMembers_thenTheFailureIsPropagated() = runTest {
        given(teamsApi)
            .suspendFunction(teamsApi::getTeamMembers)
            .whenInvokedWith(any(), anything())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain")

        result.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }
    }

    @Test
    fun givenSelfUserExists_whenGettingTeamById_thenTeamDataShouldBePassed() = runTest {
        val teamEntity = TeamEntity(id = "teamId", name = "teamName")
        val team = Team(id = "teamId", name = "teamName")

        given(teamDAO)
            .suspendFunction(teamDAO::getTeamById)
            .whenInvokedWith(oneOf("teamId"))
            .then { flowOf(teamEntity) }
        given(teamMapper)
            .function(teamMapper::fromDaoModelToTeam)
            .whenInvokedWith(oneOf(teamEntity))
            .then { team }

        teamRepository.getTeam(teamId = TeamId("teamId")).test {
            assertEquals(team, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSelfUserDoesNotExist_whenGettingTeamById_thenNullShouldBePassed() = runTest {
        given(teamDAO)
            .suspendFunction(teamDAO::getTeamById)
            .whenInvokedWith(oneOf("teamId"))
            .then { flowOf(null) }

        teamRepository.getTeam(teamId = TeamId("teamId")).test {
            assertEquals(null, awaitItem())
            awaitComplete()
        }
    }
}
