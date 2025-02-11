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

package com.wire.kalium.logic.feature.user.migration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.user.CreateUserTeam
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.user.SyncContactsUseCase
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.model.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MigrateFromPersonalToTeamUseCaseTest {

    @Test
    fun givenRepositorySucceeds_whenMigratingUserToTeam_thenShouldPropagateSuccess() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withUpdateTeamIdReturning(Either.Right(Unit))
            .withMigrationSuccess()
            .withSyncContactsSuccess()
            .arrange()

        val result = useCase(teamName = "teamName")

        coVerify {
            arrangement.userRepository.updateTeamId(any(), any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.syncContacts() }.wasInvoked(exactly = once)

        assertTrue(arrangement.isCachedTeamIdInvalidated)
        assertIs<MigrateFromPersonalToTeamResult.Success>(result)
    }

    @Test
    fun givenRepositoryFailsWithNoNetworkConnection_whenMigratingUserToTeam_thenShouldPropagateNoNetworkFailure() =
        runTest {
            val (_, useCase) = Arrangement()
                .withMigrationNoNetworkFailure()
                .arrange()

            val result = useCase(teamName = "teamName")

            assertIs<MigrateFromPersonalToTeamResult.Error>(result)
            assertIs<MigrateFromPersonalToTeamFailure.NoNetwork>(result.failure)
        }

    @Test
    fun givenRepositoryFailsWithUserAlreadyInTeam_whenMigratingUserToTeam_thenShouldPropagateUserAlreadyInTeamFailure() =
        runTest {
            val (_, useCase) = Arrangement()
                .withUserAlreadyInTeamFailure()
                .arrange()

            val result = useCase(teamName = "teamName")

            assertIs<MigrateFromPersonalToTeamResult.Error>(result)
            assertIs<MigrateFromPersonalToTeamFailure.UserAlreadyInTeam>(result.failure)
        }

    @Test
    fun givenRepositoryFailsWithUnknownError_whenMigratingUserToTeam_thenShouldPropagateUnknownFailure() =
        runTest {
            val (_, useCase) = Arrangement()
                .withMigrationUserNotFoundFailure()
                .arrange()

            val result = useCase(teamName = "teamName")

            assertIs<MigrateFromPersonalToTeamResult.Error>(result)
            assertIs<MigrateFromPersonalToTeamFailure.UnknownError>(result.failure)
            val coreFailure =
                (result.failure as MigrateFromPersonalToTeamFailure.UnknownError).coreFailure
            val serverMiscommunication = coreFailure as NetworkFailure.ServerMiscommunication
            val invalidRequestError =
                serverMiscommunication.kaliumException as KaliumException.InvalidRequestError
            val errorLabel = invalidRequestError.errorResponse.label

            assertEquals("not-found", errorLabel)
        }


    private class Arrangement {

        @Mock
        val userRepository: UserRepository = mock(UserRepository::class)

        @Mock
        val syncContacts: SyncContactsUseCase = mock(SyncContactsUseCase::class)

        var isCachedTeamIdInvalidated = false

        suspend fun withMigrationSuccess() = apply {
            coEvery { userRepository.migrateUserToTeam(any()) }.returns(
                Either.Right(
                    CreateUserTeam("teamId", "teamName")
                )
            )
        }

        suspend fun withUserAlreadyInTeamFailure() = withMigrationReturning(
            Either.Left(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            HttpStatusCode.Forbidden.value,
                            message = "Switching teams is not allowed",
                            label = "user-already-in-a-team",
                        )
                    )
                )
            )
        )

        suspend fun withMigrationUserNotFoundFailure() = withMigrationReturning(
            Either.Left(
                NetworkFailure.ServerMiscommunication(
                    KaliumException.InvalidRequestError(
                        ErrorResponse(
                            HttpStatusCode.NotFound.value,
                            message = "User not found",
                            label = "not-found",
                        )
                    )
                )
            )
        )

        suspend fun withSyncContactsSuccess() = apply {
            coEvery { syncContacts.invoke() }.returns(Either.Right(Unit))
        }

        suspend fun withMigrationNoNetworkFailure() = withMigrationReturning(
            Either.Left(NetworkFailure.NoNetworkConnection(null))
        )

        suspend fun withMigrationReturning(result: Either<CoreFailure, CreateUserTeam>) = apply {
            coEvery { userRepository.migrateUserToTeam(any()) }.returns(result)
        }

        suspend fun withUpdateTeamIdReturning(result: Either<StorageFailure, Unit>) = apply {
            coEvery { userRepository.updateTeamId(any(), any()) }.returns(result)
        }

        fun arrange() = this to MigrateFromPersonalToTeamUseCaseImpl(selfUserId = TestUser.SELF.id,
            userRepository = userRepository,
            syncContacts = syncContacts,
            invalidateTeamId = {
                isCachedTeamIdInvalidated = true
            })
    }
}
