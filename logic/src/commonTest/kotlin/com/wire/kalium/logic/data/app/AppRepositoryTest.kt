/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.app

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.PersistenceQualifiedId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.teams.TeamCollaboratorDTO
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.authenticated.userDetails.QualifiedUserIdListRequest
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.AppDTO
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedID
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.AppEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.TeamDAO
import com.wire.kalium.persistence.dao.TeamEntity
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class AppRepositoryTest {

    @Test
    fun givenSuccess_whenReadingAppDetailsById_thenSuccessIsPropagated() = runTest {
        // given
        val expected = Arrangement.appDetails.copy(
            creator = Arrangement.TEAM_NAME
        )

        val (arrangement, appRepository) = Arrangement()
            .withAppByIdSuccess(
                Arrangement.APP_ID_ENTITY,
                Arrangement.appDetailsEntity
            )
            .arrange()

        // when
        // then
        appRepository.getAppById(Arrangement.APP_ID).also {
            it.shouldSucceed { actual ->
                assertEquals(expected, actual)
            }
        }

        verifySuspend {
            arrangement.appDAO.byId(Arrangement.APP_ID_ENTITY)
        }
    }

    @Test
    fun givenError_whenReadingAppDetailsById_thenErrorIsPropagated() = runTest {
        // given
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, appRepository) = Arrangement()
            .withAppByIdFailure(
                Arrangement.APP_ID_ENTITY,
                expected.rootCause
            )
            .arrange()

        // when
        // then
        appRepository.getAppById(Arrangement.APP_ID).also {
            it.shouldFail {
                assertEquals(expected, it)
            }
        }

        verifySuspend {
            arrangement.appDAO.byId(Arrangement.APP_ID_ENTITY)
        }
    }

    @Test
    fun givenSuccess_whenObservingIfAppIsMember_thenSuccessIsPropagated() = runTest {
        val expected: List<QualifiedIDEntity?> = listOf(null, QualifiedIDEntity("id", "domain"), null)

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveIsMemberSuccess(expected.asFlow())
            .arrange()

        serviceRepository.observeIsAppMember(
            Arrangement.APP_ID,
            Arrangement.CONVERSATION_ID
        ).test {
            expected.forEach { expectedEmit ->
                val currentValue = awaitItem()
                currentValue.shouldSucceed {
                    assertEquals(expectedEmit?.toModel(), it)
                }
            }
            awaitComplete()
        }

        verifySuspend {
            arrangement.appDAO.observeIsAppMember(any(), any())
        }
    }

    @Test
    fun givenError_whenObservingIfAppIsMember_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveIsMemberFailure(expected.rootCause)
            .arrange()

        serviceRepository.observeIsAppMember(
            Arrangement.APP_ID,
            Arrangement.CONVERSATION_ID
        ).first().also {
            it.shouldFail {
                assertEquals(expected, it)
            }
        }

        verifySuspend {
            arrangement.appDAO.observeIsAppMember(any(), any())
        }
    }

    @Test
    fun givenSuccess_whenObservingAllApps_thenSuccessIsPropagated() = runTest {
        val expectedEntity = listOf(Arrangement.appDetailsEntity)

        val expected = listOf(Arrangement.appDetails)

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveAllAppsSuccess(flowOf(expectedEntity))
            .arrange()

        serviceRepository.observeAllApps().test {
            val currentValue = awaitItem()
            assertIs<Either.Right<List<AppDetails>>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend {
            arrangement.appDAO.observeAllApps()
        }
    }

    @Test
    fun givenError_whenObservingAllApps_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withObserveAllAppsFailure(expected.rootCause)
            .arrange()

        serviceRepository.observeAllApps().test {
            val currentValue = awaitItem()
            assertIs<Either.Left<StorageFailure>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend {
            arrangement.appDAO.observeAllApps()
        }
    }

    @Test
    fun givenSuccess_whenSearchingAppsByName_thenSearchResultIsPropagated() = runTest {
        val expectedEntity = listOf(Arrangement.appDetailsEntity)

        val expected = listOf(Arrangement.appDetails)

        val (arrangement, serviceRepository) = Arrangement()
            .withSearchAppsByNameSuccess(flowOf(expectedEntity))
            .arrange()

        serviceRepository.searchAppsByName("name").test {
            val currentValue = awaitItem()
            assertIs<Either.Right<List<AppDetails>>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend {
            arrangement.appDAO.searchAppsByName(any())
        }
    }

    @Test
    fun givenError_whenSearchingAppsByName_thenErrorIsPropagated() = runTest {
        val expected = StorageFailure.Generic(IllegalStateException())

        val (arrangement, serviceRepository) = Arrangement()
            .withSearchAppsByNameFailure(expected.rootCause)
            .arrange()

        serviceRepository.searchAppsByName("name").test {
            val currentValue = awaitItem()
            assertIs<Either.Left<StorageFailure>>(currentValue)
            assertEquals(expected, currentValue.value)

            awaitComplete()
        }

        verifySuspend {
            arrangement.appDAO.searchAppsByName(any())
        }
    }

    @Test
    fun givenTeamAndCollaboratorApps_whenSyncing_thenAppsAreMergedFilteredAndPersisted() = runTest {
        val teamApp = Arrangement.appProfile("team-app", UserTypeDTO.APP)
        val collaboratorApp = Arrangement.appProfile("collaborator-app", UserTypeDTO.APP)
        val regularCollaborator = Arrangement.appProfile("regular-user", UserTypeDTO.REGULAR)
        val failedId = NetworkQualifiedID("failed-user", Arrangement.DOMAIN)
        val (arrangement, appRepository) = Arrangement()
            .withTeamApps(listOf(teamApp))
            .withCollaborators(listOf("team-app", "collaborator-app", "regular-user", "failed-user"))
            .withUsers(ListUsersDTO(listOf(failedId), listOf(collaboratorApp, regularCollaborator)))
            .withUpsertAppsSuccess()
            .arrange()

        appRepository.syncApps(TeamId(Arrangement.TEAM_ID), includeTeamApps = true).shouldSucceed()

        verifySuspend {
            arrangement.userDetailsApi.getMultipleUsers(matches { request ->
                (request as QualifiedUserIdListRequest).qualifiedIds.map { it.value }.toSet() ==
                        setOf("collaborator-app", "regular-user", "failed-user")
            })
            arrangement.appDAO.upsertApps(matches { apps ->
                apps.map { it.id.value }.toSet() == setOf("team-app", "collaborator-app")
            })
        }
    }

    @Test
    fun givenCollaboratorOnlySync_whenSyncing_thenTeamAppsEndpointIsNotCalled() = runTest {
        val collaboratorApp = Arrangement.appProfile("collaborator-app", UserTypeDTO.APP)
        val (arrangement, appRepository) = Arrangement()
            .withCollaborators(listOf("collaborator-app"))
            .withUsers(ListUsersDTO(emptyList(), listOf(collaboratorApp)))
            .withUpsertAppsSuccess()
            .arrange()

        appRepository.syncApps(TeamId(Arrangement.TEAM_ID), includeTeamApps = false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(0)) { arrangement.teamsApi.getTeamApps(any()) }
        verifySuspend { arrangement.appDAO.upsertApps(matches { it.single().id.value == "collaborator-app" }) }
    }

    @Test
    fun givenMoreThanFiveHundredCollaborators_whenSyncing_thenUsersAreFetchedInBatches() = runTest {
        val collaboratorIds = (1..501).map { "collaborator-$it" }
        val (arrangement, appRepository) = Arrangement()
            .withCollaborators(collaboratorIds)
            .withUsers(ListUsersDTO(emptyList(), emptyList()))
            .withUpsertAppsSuccess()
            .arrange()

        appRepository.syncApps(TeamId(Arrangement.TEAM_ID), includeTeamApps = false).shouldSucceed()

        verifySuspend(VerifyMode.exactly(2)) { arrangement.userDetailsApi.getMultipleUsers(any()) }
    }

    @Test
    fun givenInsufficientCollaboratorPermissions_whenSyncing_thenTeamAppsAreStillPersisted() = runTest {
        val teamApp = Arrangement.appProfile("team-app", UserTypeDTO.APP)
        val (arrangement, appRepository) = Arrangement()
            .withTeamApps(listOf(teamApp))
            .withCollaboratorFailure(
                KaliumException.InvalidRequestError(
                    GenericAPIErrorResponse(403, "Forbidden", "insufficient-permissions")
                )
            )
            .withUpsertAppsSuccess()
            .arrange()

        appRepository.syncApps(TeamId(Arrangement.TEAM_ID), includeTeamApps = true).shouldSucceed()

        verifySuspend { arrangement.appDAO.upsertApps(matches { it.single().id.value == "team-app" }) }
        verifySuspend(VerifyMode.exactly(0)) { arrangement.userDetailsApi.getMultipleUsers(any()) }
    }

    @Test
    fun givenOtherCollaboratorFailure_whenSyncing_thenExistingCacheIsNotChanged() = runTest {
        val teamApp = Arrangement.appProfile("team-app", UserTypeDTO.APP)
        val (arrangement, appRepository) = Arrangement()
            .withTeamApps(listOf(teamApp))
            .withCollaboratorFailure(KaliumException.GenericError(IllegalStateException("network failure")))
            .arrange()

        appRepository.syncApps(TeamId(Arrangement.TEAM_ID), includeTeamApps = true).shouldFail()

        verifySuspend(VerifyMode.exactly(0)) { arrangement.appDAO.upsertApps(any()) }
    }

    private class Arrangement {
        val appDAO = mock<AppDAO>()
        val teamDAO = mock<TeamDAO>()
        val teamsApi = mock<TeamsApi>()
        val userDetailsApi = mock<UserDetailsApi>()

        private val appRepository = AppDataSource(
            appDAO = appDAO,
            teamDAO = teamDAO,
            teamsApi = teamsApi,
            userDetailsApi = userDetailsApi,
            selfUserId = UserId("self-user", "wire.com")
        )

        init {
            everySuspend { teamDAO.getTeamById(any()) } returns flowOf(null)
        }

        suspend fun withAppByIdSuccess(appId: PersistenceQualifiedId, result: AppEntity) = apply {
            everySuspend {
                appDAO.byId(appId)
            } returns result
            everySuspend {
                teamDAO.getTeamById(TEAM_ID)
            } returns flowOf(
                TeamEntity(
                    id = TEAM_ID,
                    name = TEAM_NAME,
                    icon = ""
                )
            )
        }

        suspend fun withAppByIdFailure(appId: PersistenceQualifiedId, error: Throwable) = apply {
            everySuspend {
                appDAO.byId(appId)
            } throws error
        }

        suspend fun withObserveIsMemberSuccess(result: Flow<QualifiedIDEntity?>) = apply {
            everySuspend {
                appDAO.observeIsAppMember(any(), any())
            } returns result
        }

        suspend fun withObserveIsMemberFailure(error: Throwable) = apply {
            everySuspend {
                appDAO.observeIsAppMember(any(), any())
            } throws error
        }

        suspend fun withObserveAllAppsSuccess(result: Flow<List<AppEntity>>) = apply {
            everySuspend {
                appDAO.observeAllApps()
            } returns result
        }

        suspend fun withObserveAllAppsFailure(error: Throwable) = apply {
            everySuspend {
                appDAO.observeAllApps()
            } throws error
        }

        suspend fun withSearchAppsByNameSuccess(result: Flow<List<AppEntity>>) = apply {
            everySuspend {
                appDAO.searchAppsByName(any())
            } returns result
        }

        suspend fun withSearchAppsByNameFailure(error: Throwable) = apply {
            everySuspend {
                appDAO.searchAppsByName(any())
            } throws error
        }

        fun withTeamApps(apps: List<UserProfileDTO>) = apply {
            everySuspend { teamsApi.getTeamApps(TEAM_ID) } returns NetworkResponse.Success(apps, emptyMap(), 200)
        }

        fun withCollaborators(ids: List<String>) = apply {
            everySuspend { teamsApi.getTeamCollaborators(TEAM_ID) } returns NetworkResponse.Success(
                ids.map(::TeamCollaboratorDTO),
                emptyMap(),
                200
            )
        }

        fun withCollaboratorFailure(error: KaliumException) = apply {
            everySuspend { teamsApi.getTeamCollaborators(TEAM_ID) } returns NetworkResponse.Error(error)
        }

        fun withUsers(users: ListUsersDTO) = apply {
            everySuspend { userDetailsApi.getMultipleUsers(any()) } returns NetworkResponse.Success(users, emptyMap(), 200)
        }

        fun withUpsertAppsSuccess() = apply {
            everySuspend { appDAO.upsertApps(any()) } returns Unit
        }

        fun arrange() = this to appRepository

        companion object {
            val TEAM_ID = Uuid.random().toString()
            val TEAM_NAME = "Wire Team"

            val APP_ID = QualifiedID(
                value = Uuid.random().toString(),
                domain = "wire.com"
            )
            val APP_ID_ENTITY = PersistenceQualifiedId(
                value = APP_ID.value,
                domain = APP_ID.domain
            )
            const val APP_NAME = "App Name"
            const val APP_DESCRIPTION = "App Description"
            const val APP_CATEGORY = "DEVELOPER"
            const val DOMAIN = "wire.com"

            val CONVERSATION_ID = QualifiedID(
                value = Uuid.random().toString(),
                domain = "wire.com"
            )

            val appDetails = AppDetails(
                id = APP_ID,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY,
                creator = null,
                previewAssetId = null,
                completeAssetId = null
            )

            val appDetailsEntity = AppEntity(
                id = APP_ID_ENTITY,
                name = APP_NAME,
                description = APP_DESCRIPTION,
                category = APP_CATEGORY,
                teamId = TEAM_ID,
                previewAssetId = null,
                completeAssetId = null
            )

            fun appProfile(id: String, type: UserTypeDTO): UserProfileDTO = TestUser.USER_PROFILE_DTO.copy(
                id = NetworkQualifiedID(id, DOMAIN),
                nonQualifiedId = id,
                name = id,
                type = type,
                app = if (type == UserTypeDTO.APP) AppDTO(APP_DESCRIPTION, APP_CATEGORY) else null
            )
        }
    }
}
