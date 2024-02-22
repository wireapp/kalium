/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.client.ClientIdDTO
import com.wire.kalium.network.api.base.authenticated.keypackage.LastPreKeyDTO
import com.wire.kalium.network.api.base.model.ErrorResponse
import com.wire.kalium.network.api.base.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
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
import io.mockative.anything
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.oneOf
import io.mockative.twice
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

        val (arrangement, teamRepository) = Arrangement()
            .withGetTeamMembers(NetworkResponse.Success(teamMembersList, mapOf(), 200))
            .arrange()

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", null)

        // Verifies that userDAO insertUsers was called with the correct mapped values
        verify(arrangement.userDAO)
            .suspendFunction(arrangement.userDAO::upsertTeamMemberUserTypes)
            .with(any())
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

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", null)

        result.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }
    }

    @Test
    fun givenLimitIs0_whenFetchingTeamMembers_thenReturnSuccessWithoutFetchingAnything() = runTest {
        val (arrangement, teamRepository) = Arrangement()
            .arrange()

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", 0)

        result.shouldSucceed()

        verify(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamMembers)
            .with(any(), any())
            .wasNotInvoked()
    }

    @Test
    fun givenLimitAndHasMoreIsTrue_whenFetchingTeamMembers_thenDoNotFetchTheNextyPage() = runTest {
        val teamMember = TestTeam.memberDTO(
            nonQualifiedUserId = "teamMember1"
        )

        val teamMembersList = TeamsApi.TeamMemberList(
            hasMore = true,
            members = listOf(
                teamMember
            )
        )


        val pageSize = 100
        val limit = 200

        val (arrangement, teamRepository) = Arrangement()
            .withGetTeamMembers(NetworkResponse.Success(teamMembersList, mapOf(), 200))
            .arrange()

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", limit, pageSize = pageSize)

        result.shouldSucceed()

        verify(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamMembers)
            .with(any(), any())
            .wasInvoked(exactly = twice)
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
    fun givenTeamIdAndUserIdAndPassword_whenApprovingLegalHoldRequest_thenItShouldSucceedAndClearRequestLocallyAndCreateEvent() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement()
            .withApiApproveLegalHoldSuccess()
            .withHandleLegalHoldSuccesses()
            .arrange()
        // when
        val result = teamRepository.approveLegalHoldRequest(teamId = TeamId(value = "teamId"), password = "password")
        // then
        result.shouldSucceed()
        verify(arrangement.userConfigDAO)
            .suspendFunction(arrangement.userConfigDAO::clearLegalHoldRequest)
            .wasInvoked(once)
        verify(arrangement.legalHoldHandler)
            .suspendFunction(arrangement.legalHoldHandler::handleEnable)
            .with(matching { it.userId == TestUser.USER_ID })
            .wasInvoked(once)
    }

    private fun testFetchingLegalHoldStatus(
        response: LegalHoldStatusResponse,
        expected: LegalHoldStatus,
        verification: (Arrangement) -> Unit,
    ) = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement()
            .withApiFetchLegalHoldStatusSuccess(response)
            .withHandleLegalHoldSuccesses()
            .arrange()
        // when
        val result = teamRepository.fetchLegalHoldStatus(teamId = TeamId(value = "teamId"))
        // then
        result.shouldSucceed() { assertEquals(expected, it) }
        verification(arrangement)
    }

    @Test
    fun givenTeamIdAndUserIdAndStatusNoConsent_whenFetchingLegalHoldStatus_thenItShouldSucceed() =
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.NO_CONSENT, null, null),
            expected = LegalHoldStatus.NO_CONSENT,
        ) { arrangement ->
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleEnable)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleDisable)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.legalHoldRequestHandler)
                .suspendFunction(arrangement.legalHoldRequestHandler::handle)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenTeamIdAndUserIdAndStatusPending_whenFetchingLegalHoldStatus_thenItShouldSucceedAndHandlePendingLegalHold() {
        val clientId = ClientIdDTO("clientId")
        val lastPreKey = LastPreKeyDTO(1, "key")
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.PENDING, ClientIdDTO("clientId"), LastPreKeyDTO(1, "key")),
            expected = LegalHoldStatus.PENDING,
        ) { arrangement ->
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleEnable)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleDisable)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.legalHoldRequestHandler)
                .suspendFunction(arrangement.legalHoldRequestHandler::handle)
                .with(matching {
                    it.clientId.value == clientId.clientId && it.lastPreKey.id == lastPreKey.id && it.lastPreKey.key == lastPreKey.key
                })
                .wasInvoked()
        }
    }

    @Test
    fun givenTeamIdAndUserIdAndStatusEnabled_whenFetchingLegalHoldStatus_thenItShouldSucceedAndHandleEnabledLegalHold() =
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.ENABLED, null, null),
            expected = LegalHoldStatus.ENABLED,
        ) { arrangement ->
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleEnable)
                .with(any())
                .wasInvoked()
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleDisable)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.legalHoldRequestHandler)
                .suspendFunction(arrangement.legalHoldRequestHandler::handle)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenTeamIdAndUserIdAndStatusDisabled_whenFetchingLegalHoldStatus_thenItShouldSucceedAndHandleDisabledLegalHold() =
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.DISABLED, null, null),
            expected = LegalHoldStatus.DISABLED,
        ) { arrangement ->
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleEnable)
                .with(any())
                .wasNotInvoked()
            verify(arrangement.legalHoldHandler)
                .suspendFunction(arrangement.legalHoldHandler::handleDisable)
                .with(any())
                .wasInvoked()
            verify(arrangement.legalHoldRequestHandler)
                .suspendFunction(arrangement.legalHoldRequestHandler::handle)
                .with(any())
                .wasNotInvoked()
        }

    @Test
    fun givenSelfUserExists_whenSyncingTeam_thenTeamInfoShouldBeUpdatedSuccessful() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement()
            .withApiGetTeamInfoSuccess(TestTeam.TEAM_DTO)
            .arrange()

        // when
        val result = teamRepository.syncTeam(teamId = TeamId(TestTeam.TEAM_ID.value))

        // then
        result.shouldSucceed { returnTeam -> assertEquals(TestTeam.TEAM, returnTeam) }
        verify(arrangement.teamsApi)
            .suspendFunction(arrangement.teamsApi::getTeamInfo)
            .with(any())
            .wasInvoked(exactly = once)
        verify(arrangement.teamDAO)
            .suspendFunction(arrangement.teamDAO::updateTeam)
            .with(eq(TestTeam.TEAM_ENTITY))
            .wasInvoked(exactly = once)
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
        val userConfigDAO = configure(mock(classOf<UserConfigDAO>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val teamsApi = mock(classOf<TeamsApi>())

        @Mock
        val serviceDAO = configure(mock(classOf<ServiceDAO>())) {
            stubsUnitByDefault = true
        }

        @Mock
        val legalHoldHandler = mock(classOf<LegalHoldHandler>())

        @Mock
        val legalHoldRequestHandler = mock(classOf<LegalHoldRequestHandler>())

        val teamRepository: TeamRepository = TeamDataSource(
            teamDAO = teamDAO,
            teamMapper = teamMapper,
            teamsApi = teamsApi,
            userDAO = userDAO,
            selfUserId = TestUser.USER_ID,
            serviceDAO = serviceDAO,
            legalHoldHandler = legalHoldHandler,
            legalHoldRequestHandler = legalHoldRequestHandler,
            userConfigDAO = userConfigDAO

        )

        fun withApiGetTeamInfoSuccess(teamDTO: TeamDTO) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::getTeamInfo)
                .whenInvokedWith(oneOf(teamDTO.id))
                .then { NetworkResponse.Success(value = teamDTO, headers = mapOf(), httpCode = 200) }
        }

        fun withFetchWhiteListedServicesSuccess() = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::whiteListedServices)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(value = SERVICE_DETAILS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        fun withApiApproveLegalHoldSuccess() = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::approveLegalHoldRequest)
                .whenInvokedWith(any(), any())
                .thenReturn(NetworkResponse.Success(value = Unit, headers = mapOf(), httpCode = 200))
        }

        fun withApiFetchLegalHoldStatusSuccess(result: LegalHoldStatusResponse) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::fetchLegalHoldStatus)
                .whenInvokedWith(any())
                .thenReturn(NetworkResponse.Success(value = result, headers = mapOf(), httpCode = 200))
        }

        fun withHandleLegalHoldSuccesses() = apply {
            given(legalHoldHandler)
                .suspendFunction(legalHoldHandler::handleEnable)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(legalHoldHandler)
                .suspendFunction(legalHoldHandler::handleDisable)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(legalHoldRequestHandler)
                .suspendFunction(legalHoldRequestHandler::handle)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withGetTeamMembers(result: NetworkResponse<TeamsApi.TeamMemberList>) = apply {
            given(teamsApi)
                .suspendFunction(teamsApi::getTeamMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(result)

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
