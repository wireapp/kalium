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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.LegalHoldStatus
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldRequestHandler
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.client.ClientIdDTO
import com.wire.kalium.network.api.authenticated.keypackage.LastPreKeyDTO
import com.wire.kalium.network.api.authenticated.teams.TeamMemberListPaginated
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.model.ServiceDetailDTO
import com.wire.kalium.network.api.model.ServiceDetailResponse
import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.ServiceDAO
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.UserConfigDAO
import com.wire.kalium.persistence.dao.UserDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.mockative.oneOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class TeamRepositoryTest {
    @Test
    fun givenSelfUserExists_whenFetchingTeamInfo_thenTeamInfoShouldBeSuccessful() = runTest {
        val (arrangement, teamRepository) = Arrangement().withApiGetTeamInfoSuccess(TestTeam.TEAM_DTO).arrange()

        arrangement.teamMapper.fromDaoModelToTeam(TestTeam.TEAM_ENTITY)

        val result = teamRepository.fetchTeamById(teamId = TeamId(TestTeam.TEAM_ID.value))

        // Verifies that teamDAO insertTeam was called with the correct mapped values
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamDAO.insertTeam(oneOf(TestTeam.TEAM_ENTITY))
        }

        // Verifies that when fetching team by id, it succeeded
        result.shouldSucceed { returnTeam ->
            assertEquals(TestTeam.TEAM, returnTeam)
        }
    }

    @Test
    fun givenTeamApiFails_whenFetchingTeamInfo_thenTheFailureIsPropagated() = runTest {
        val (arrangement, teamRepository) = Arrangement().arrange()

        everySuspend {
            arrangement.teamsApi.getTeamInfo(any())
        }.returns(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

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

        val teamMembersList = TeamMemberListPaginated(
            hasMore = false,
            members = listOf(
                teamMember
            ),
            pagingState = "A=="
        )

        val (arrangement, teamRepository) = Arrangement().withGetTeamMembers(NetworkResponse.Success(teamMembersList, mapOf(), 200))
            .arrange()

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", null)

        // Verifies that userDAO insertUsers was called with the correct mapped values
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.upsertTeamMemberUserTypes(any())
        }

        // Verifies that when fetching members by team id, it succeeded
        result.shouldSucceed()
    }

    @Test
    fun givenTeamApiFails_whenFetchingTeamMembers_thenTheFailureIsPropagated() = runTest {
        val (arrangement, teamRepository) = Arrangement().arrange()

        everySuspend {
            arrangement.teamsApi.getTeamMembers(any(), any(), any())
        }.returns(NetworkResponse.Error(KaliumException.ServerError(ErrorResponse(500, "error_message", "error_label"))))

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", null)

        result.shouldFail {
            assertEquals(it::class, NetworkFailure.ServerMiscommunication::class)
        }
    }

    @Test
    fun givenLimitIs0_whenFetchingTeamMembers_thenReturnSuccessWithoutFetchingAny() = runTest {
        val (arrangement, teamRepository) = Arrangement().arrange()

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", 0)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.teamsApi.getTeamMembers(any(), any(), any())
        }
    }

    @Test
    fun givenLimitAndHasMoreIsTrue_whenFetchingTeamMembers_thenDoNotFetchTheNextPage() = runTest {
        val teamMember = TestTeam.memberDTO(
            nonQualifiedUserId = "teamMember1"
        )

        val teamMembersList = TeamMemberListPaginated(
            hasMore = true,
            members = listOf(
                teamMember
            ),
            "A=="
        )

        val pageSize = 100
        val limit = 200

        val (arrangement, teamRepository) = Arrangement().withGetTeamMembers(NetworkResponse.Success(teamMembersList, mapOf(), 200))
            .arrange()

        val result = teamRepository.fetchMembersByTeamId(teamId = TeamId("teamId"), userDomain = "userDomain", limit, pageSize = pageSize)

        result.shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.teamsApi.getTeamMembers(any(), any(), any())
        }
    }

    @Test
    fun givenSelfUserExists_whenGettingTeamById_thenTeamDataShouldBePassed() = runTest {
        val teamEntity = TeamEntity(id = "teamId", name = "teamName", icon = "icon")
        val team = Team(id = "teamId", name = "teamName", icon = "icon")

        val (arrangement, teamRepository) = Arrangement().arrange()

        everySuspend {
            arrangement.teamDAO.getTeamById(oneOf("teamId"))
        }.returns(flowOf(teamEntity))

        teamRepository.getTeam(teamId = TeamId("teamId")).test {
            assertEquals(team, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenSelfUserDoesNotExist_whenGettingTeamById_thenNullShouldBePassed() = runTest {
        val (arrangement, teamRepository) = Arrangement().arrange()

        everySuspend {
            arrangement.teamDAO.getTeamById(oneOf("teamId"))
        }.returns(flowOf(null))

        teamRepository.getTeam(teamId = TeamId("teamId")).test {
            assertEquals(null, awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenAConversationId_whenDeletingATeamConversation_thenShouldCallToApiLayerSucceed() = runTest {
        val (arrangement, teamRepository) = Arrangement().arrange()

        everySuspend {
            arrangement.teamsApi.deleteConversation(any(), any())
        }.returns(NetworkResponse.Success(Unit, mapOf(), 200))

        val result = teamRepository.deleteConversation(TestConversation.ID, TeamId("aTeamId"))

        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamsApi.deleteConversation(eq("valueConvo"), eq("aTeamId"))
        }
    }

    @Test
    fun givenAConversationId_whenDeletingATeamConversationAndErrorFromApi_thenShouldFail() = runTest {
        val (arrangement, teamRepository) = Arrangement().arrange()

        everySuspend {
            arrangement.teamsApi.deleteConversation(any(), any())
        }.returns(NetworkResponse.Error(KaliumException.GenericError(RuntimeException("Some error happened"))))

        val result = teamRepository.deleteConversation(TestConversation.ID, TeamId("aTeamId"))

        result.shouldFail()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamsApi.deleteConversation(eq("valueConvo"), eq("aTeamId"))
        }
    }

    @Test
    fun givenTeamId_whenSyncingWhitelistedServices_thenInsertIntoDatabase() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement().withFetchWhiteListedServicesSuccess().arrange()

        // when
        val result = teamRepository.syncServices(teamId = TeamId(value = "teamId"))

        result.shouldSucceed()

        // then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.serviceDAO.insertMultiple(any())
        }
    }

    @Test
    fun givenTeamIdAndUserIdAndPassword_whenApprovingLegalHoldRequest_thenItShouldSucceedAndClearRequestLocallyAndCreateEvent() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement().withApiApproveLegalHoldSuccess().withHandleLegalHoldSuccesses().arrange()
        // when
        val result = teamRepository.approveLegalHoldRequest(teamId = TeamId(value = "teamId"), password = "password")
        // then
        result.shouldSucceed()
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userConfigDAO.clearLegalHoldRequest()
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.legalHoldHandler.handleEnable(matches { it.userId == TestUser.USER_ID })
        }
    }

    private inline fun testFetchingLegalHoldStatus(
        response: LegalHoldStatusResponse,
        expected: LegalHoldStatus,
        crossinline verification: suspend (Arrangement) -> Unit,
    ) = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement().withApiFetchLegalHoldStatusSuccess(response).withHandleLegalHoldSuccesses()
            .arrange()
        // when
        val result = teamRepository.fetchLegalHoldStatus(teamId = TeamId(value = "teamId"))
        // then
        result.shouldSucceed { assertEquals(expected, it) }
        verification(arrangement)
    }

    @Test
    fun givenTeamIdAndUserIdAndStatusNoConsent_whenFetchingLegalHoldStatus_thenItShouldSucceed() = testFetchingLegalHoldStatus(
        response = LegalHoldStatusResponse(LegalHoldStatusDTO.NO_CONSENT, null, null),
        expected = LegalHoldStatus.NO_CONSENT,
    ) { arrangement ->
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.legalHoldHandler.handleEnable(any())
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.legalHoldHandler.handleDisable(any())
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.legalHoldRequestHandler.handle(any())
        }
    }

    @Test
    fun givenTeamIdAndUserIdAndStatusPending_whenFetchingLegalHoldStatus_thenItShouldSucceedAndHandlePendingLegalHold() {
        val clientId = ClientIdDTO("clientId")
        val lastPreKey = LastPreKeyDTO(1, "key")
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.PENDING, ClientIdDTO("clientId"), LastPreKeyDTO(1, "key")),
            expected = LegalHoldStatus.PENDING,
        ) { arrangement ->
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.legalHoldHandler.handleEnable(any())
            }
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.legalHoldHandler.handleDisable(any())
            }
            verifySuspend(VerifyMode.atLeast(1)) {
                arrangement.legalHoldRequestHandler.handle(
                    matches {
                        it.clientId.value == clientId.clientId && it.lastPreKey.id == lastPreKey.id && it.lastPreKey.key == lastPreKey.key
                    }
                )
            }
        }
    }

    @Test
    fun givenTeamIdAndUserIdAndStatusEnabled_whenFetchingLegalHoldStatus_thenItShouldSucceedAndHandleEnabledLegalHold() =
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.ENABLED, null, null),
            expected = LegalHoldStatus.ENABLED,
        ) { arrangement ->
            verifySuspend(VerifyMode.atLeast(1)) {
                arrangement.legalHoldHandler.handleEnable(any())
            }
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.legalHoldHandler.handleDisable(any())
            }
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.legalHoldRequestHandler.handle(any())
            }
        }

    @Test
    fun givenTeamIdAndUserIdAndStatusDisabled_whenFetchingLegalHoldStatus_thenItShouldSucceedAndHandleDisabledLegalHold() =
        testFetchingLegalHoldStatus(
            response = LegalHoldStatusResponse(LegalHoldStatusDTO.DISABLED, null, null),
            expected = LegalHoldStatus.DISABLED,
        ) { arrangement ->
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.legalHoldHandler.handleEnable(any())
            }
            verifySuspend(VerifyMode.atLeast(1)) {
                arrangement.legalHoldHandler.handleDisable(any())
            }
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.legalHoldRequestHandler.handle(any())
            }
        }

    @Test
    fun givenSelfUserExists_whenSyncingTeam_thenTeamInfoShouldBeUpdatedSuccessful() = runTest {
        // given
        val (arrangement, teamRepository) = Arrangement().withApiGetTeamInfoSuccess(TestTeam.TEAM_DTO).arrange()

        // when
        val result = teamRepository.syncTeam(teamId = TeamId(TestTeam.TEAM_ID.value))

        // then
        result.shouldSucceed { returnTeam -> assertEquals(TestTeam.TEAM, returnTeam) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamsApi.getTeamInfo(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.teamDAO.updateTeam(eq(TestTeam.TEAM_ENTITY))
        }
    }

    private class Arrangement {
                val teamDAO = mock<TeamDAO>(mode = MockMode.autoUnit)
        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)

        val teamMapper = MapperProvider.teamMapper()
        val userConfigDAO = mock<UserConfigDAO>(mode = MockMode.autoUnit)
        val teamsApi = mock<TeamsApi>(mode = MockMode.autoUnit)
        val serviceDAO = mock<ServiceDAO>(mode = MockMode.autoUnit)
        val legalHoldHandler = mock<LegalHoldHandler>(mode = MockMode.autoUnit)
        val legalHoldRequestHandler = mock<LegalHoldRequestHandler>(mode = MockMode.autoUnit)

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

        suspend fun withApiGetTeamInfoSuccess(teamDTO: TeamDTO) = apply {
            everySuspend {
                teamsApi.getTeamInfo(oneOf(teamDTO.id))
            }.returns(NetworkResponse.Success(value = teamDTO, headers = mapOf(), httpCode = 200))
        }

        suspend fun withFetchWhiteListedServicesSuccess() = apply {
            everySuspend {
                teamsApi.whiteListedServices(any(), any())
            }.returns(NetworkResponse.Success(value = SERVICE_DETAILS_RESPONSE, headers = mapOf(), httpCode = 200))
        }

        suspend fun withApiApproveLegalHoldSuccess() = apply {
            everySuspend {
                teamsApi.approveLegalHoldRequest(any(), any(), any())
            }.returns(NetworkResponse.Success(value = Unit, headers = mapOf(), httpCode = 200))
        }

        suspend fun withApiFetchLegalHoldStatusSuccess(result: LegalHoldStatusResponse) = apply {
            everySuspend {
                teamsApi.fetchLegalHoldStatus(any(), any())
            }.returns(NetworkResponse.Success(value = result, headers = mapOf(), httpCode = 200))
        }

        suspend fun withHandleLegalHoldSuccesses() = apply {
            everySuspend {
                legalHoldHandler.handleEnable(any())
            }.returns(Either.Right(Unit))
            everySuspend {
                legalHoldHandler.handleDisable(any())
            }.returns(Either.Right(Unit))
            everySuspend {
                legalHoldRequestHandler.handle(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withGetTeamMembers(result: NetworkResponse<TeamMemberListPaginated>) = apply {
            everySuspend {
                teamsApi.getTeamMembers(any(), any(), any())
            }.returns(result)
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
