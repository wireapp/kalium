/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
import com.wire.kalium.network.api.base.model.ServiceDetailDTO
import com.wire.kalium.network.api.base.model.ServiceDetailResponse
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ServiceDAO
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.unread.UserConfigDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun givenSelfUserExists_whenGettingTeamById_thenTeamDataShouldBePassed() = runTest {
        val teamEntity = TeamEntity(id = "teamId", name = "teamName", icon = "icon")
        val team = Team(id = "teamId", name = "teamName", icon = "icon")

        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        given(arrangement.teamDAO)
            .suspendFunction(arrangement.teamDAO::getTeamById)
            .whenInvokedWith(oneOf("teamId"))
            .then { flowOf(teamEntity) }

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

        val result = teamRepository.deleteConversation(TestConversation.ID, TeamId("aTeamId"))

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

        val result = teamRepository.deleteConversation(TestConversation.ID, TeamId("aTeamId"))

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
            .suspendFunction(arrangement.userDAO::upsertUser)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenTeamId_whenSyncingWhitelistedServices_thenInsertIntoDatabase() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement()
            .withFetchWhiteListedServicesSuccess()
            .arrange()

        // when
        val result = teamRepository.syncServices(teamId = TeamId(value = "teamId"))

        result.shouldSucceed()

        // then
        verify(arrangement.serviceDAO)
            .suspendFunction(arrangement.serviceDAO::insertMultiple)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenTeamIdAndUserIdAndPassword_whenFetchingTeamMember_thenTeamMemberShouldBeSuccessful() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement()
            .withApiApproveLegalHoldSuccess()
            .withGetUsersInfoSuccess()
            .arrange()
        // when
        val result = teamRepository.approveLegalHold(teamId = TeamId(value = "teamId"), password = "password")
        // then
        result.shouldSucceed()
        verify(arrangement.userConfigDAO)
            .suspendFunction(arrangement.userConfigDAO::clearLegalHoldRequest)
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

        @Mock
        val userConfigDAO = configure(mock(classOf<UserConfigDAO>())) {
            stubsUnitByDefault = true
        }

        val teamMapper = MapperProvider.teamMapper()

        @Mock
        val userMapper = MapperProvider.userMapper()

        @Mock
        val teamsApi = mock(classOf<TeamsApi>())

        @Mock
        val userDetailsApi = mock(classOf<UserDetailsApi>())

        @Mock
        val serviceDAO = configure(mock(classOf<ServiceDAO>())) {
            stubsUnitByDefault = true
        }

        val teamRepository: TeamRepository = TeamDataSource(
            teamDAO = teamDAO,
            teamMapper = teamMapper,
            teamsApi = teamsApi,
            userDetailsApi = userDetailsApi,
            userDAO = userDAO,
            userConfigDAO = userConfigDAO,
            userMapper = userMapper,
            selfUserId = TestUser.USER_ID,
            serviceDAO = serviceDAO
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

        fun withFetchWhiteListedServicesSuccess() = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::whiteListedServices)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(value = SERVICE_DETAILS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        fun withApiApproveLegalHoldSuccess() = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::approveLegalHold)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(value = Unit, headers = mapOf(), httpCode = 200))
        }

        fun arrange() = this to teamRepository

        companion object {
            val SERVICE_DTO = ServiceDetailDTO(
                enabled = true,
                assets = null,
                id = "serviceId",
                provider = "providerId",
                name = "Service Name",
                description = "Service Description",
                summary = "Service Summary",
                tags = listOf()
            )
            val SERVICE_DETAILS_RESPONSE = ServiceDetailResponse(
                hasMore = false,
                services = listOf(SERVICE_DTO)
            )
        }
    }

}
