package com.wire.kalium.logic.data.team

import app.cash.turbine.test
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class TeamRepositoryTest {
    @Test
    fun givenSelfUserExists_whenFetchingTeamInfo_thenTeamInfoShouldBeSuccessful() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .withApiGetTeamInfoSuccess(TestTeam.TEAM_DTO)
            .arrange()

        arrangement.teamMapper.fromDaoModelToTeam(TestTeam.TEAM_ENTITY)

        val result = teamRepository.fetchTeamById(teamId = TeamId(TestTeam.TEAM_ID.value))

        // Verifies that teamDAO insertTeam was called with the correct mapped values
        verify(arrangement.teamDAO)
            .suspendFunction(arrangement.teamDAO::insertTeam)
            .with(oneOf(TestTeam.TEAM_ENTITY))
            .wasInvoked(exactly = once)

        // Verifies that when fetching team by id, it succeeded
        result.shouldSucceed { returnTeam ->
            assertEquals(TestTeam.TEAM, returnTeam)
        }
    }

    @Test
    fun givenTeamApiFails_whenFetchingTeamInfo_thenTheFailureIsPropagated() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamInfo)
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
            userType = UserTypeEntity.EXTERNAL,
            botService = null,
            deleted = false
        )

        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamMembers)
            .whenInvokedWith(oneOf("teamId"), oneOf(null))
            .thenReturn(NetworkResponse.Success(value = teamMembersList, headers = mapOf(), httpCode = 200))

        given(arrangement.userMapper)
            .invocation {
                arrangement.userMapper.fromTeamMemberToDaoModel(
                    teamId = TeamId("teamId"),
                    teamMember.nonQualifiedUserId,
                    null,
                    "userDomain",
                )
            }
            .then { mappedTeamMember }

        val result = arrangement.teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain")

        // Verifies that userDAO insertUsers was called with the correct mapped values
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertTeamMembersTypes)
            .with(oneOf(listOf(mappedTeamMember)))
            .wasInvoked(exactly = once)

        // Verifies that when fetching members by team id, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenTeamApiFails_whenFetchingTeamMembers_thenTheFailureIsPropagated() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamMembers)
            .whenInvokedWith(any(), anything())
            .thenReturn(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain")

        result.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }
    }

    @Test
    fun givenSelfUserExists_whenGettingTeamById_thenTeamDataShouldBePassed() = runTest {
        val teamEntity = TeamEntity(id = "teamId", name = "teamName", icon = "icon")
        val team = Team(id = "teamId", name = "teamName", icon = "icon")

        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamDAO)
            .suspendFunction(arrangement.teamDAO::getTeamById)
            .whenInvokedWith(oneOf("teamId"))
            .then { flowOf(teamEntity) }
        given(arrangement.teamMapper)
            .function(arrangement.teamMapper::fromDaoModelToTeam)
            .whenInvokedWith(oneOf(teamEntity))
            .then { team }

        teamRepository.getTeam(teamId = TeamId("teamId")).test {
            assertEquals(team, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSelfUserDoesNotExist_whenGettingTeamById_thenNullShouldBePassed() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamDAO)
            .suspendFunction(arrangement.teamDAO::getTeamById)
            .whenInvokedWith(oneOf("teamId"))
            .then { flowOf(null) }

        teamRepository.getTeam(teamId = TeamId("teamId")).test {
            assertEquals(null, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenAConversationId_whenDeletingATeamConversation_thenShouldCallToApiLayerSucceed() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::deleteConversation)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Success(Unit, mapOf(), 200))

        val result = teamRepository.deleteConversation(TestConversation.ID, "aTeamId")

        result.shouldSucceed()
        verify(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::deleteConversation)
            .with(eq("valueConvo"), eq("aTeamId"))
            .wasInvoked(once)
    }

    @Test
    fun givenAConversationId_whenDeletingATeamConversationAndErrorFromApi_thenShouldFail() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::deleteConversation)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Error(KaliumException.GenericError(RuntimeException("Some error happened"))))

        val result = teamRepository.deleteConversation(TestConversation.ID, "aTeamId")

        result.shouldFail()
        verify(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::deleteConversation)
            .with(eq("valueConvo"), eq("aTeamId"))
            .wasInvoked(once)
    }

    @Test
    fun givenTeamIdAndUserId_whenFetchingTeamMember_thenTeamMemberShouldBeSuccessful() = runTest {
        val teamMemberDTO = TestTeam.memberDTO(
            nonQualifiedUserId = "teamMember1"
        )

        val (arrangement, teamRepository) = Arrangement()
            .withApiGetTeamMemberSuccess(teamMemberDTO)
            .withGetUsersInfoSuccess()
            .arrange()

        val result = teamRepository.fetchTeamMember("teamId", "userId")

        result.shouldSucceed()

        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::insertUser)
            .with(any())
            .wasInvoked(once)

    }

    private class Arrangement {
        @Mock
        val teamDAO = configure(mock(classOf<TeamDAO>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val userDAO = configure(mock(classOf<UserDAO>())) {
            stubsUnitByDefault = true
        }

        val teamMapper = MapperProvider.teamMapper()

        @Mock
        val userMapper = MapperProvider.userMapper()

        @Mock
        val idMapper = MapperProvider.idMapper()

        @Mock
        val teamsApi = mock(classOf<TeamsApi>())

        @Mock
        val userDetailsApi = mock(classOf<UserDetailsApi>())

        val teamRepository: TeamRepository = TeamDataSource(
            teamDAO = teamDAO,
            teamMapper = teamMapper,
            teamsApi = teamsApi,
            userDetailsApi = userDetailsApi,
            userDAO = userDAO,
            userMapper = userMapper,
            idMapper = idMapper,
            selfUserId = TestUser.USER_ID
        )

        fun withApiGetTeamInfoSuccess(teamDTO: TeamDTO) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::getTeamInfo)
                .whenInvokedWith(oneOf(teamDTO.id))
                .then { NetworkResponse.Success(value = teamDTO, headers = mapOf(), httpCode = 200) }
        }

        fun withGetUsersInfoSuccess() = apply {
            given(userDetailsApi)
                .suspendFunction(userDetailsApi::getUserInfo)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(TestUser.USER_PROFILE_DTO, mapOf(), 200))
        }

        fun withApiGetTeamMemberSuccess(teamMemberDTO: TeamsApi.TeamMemberDTO) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::getTeamMember)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(value = teamMemberDTO, headers = mapOf(), httpCode = 200))
        }

        fun arrange() = this to teamRepository
    }

}
