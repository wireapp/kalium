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
package com.wire.kalium.logic.feature.personaltoteamaccount

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.TestRequestHandler.Companion.TEST_BACKEND_CONFIG
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanMigrateFromPersonalToTeamUseCaseTest {

    @Test
    fun givenAPIVersionBelowMinimumAndUserNotInATeam_whenInvoking_thenReturnsFalse() = runTest {
        // Given
        val (_, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withTeamId(Either.Right(null))
            .withServerConfig(6)
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
    }

    @Test
    fun givenAPIVersionEqualToMinimumAndUserNotInATeam_whenInvoking_thenReturnsTrue() =
        runTest {
            // Given
            val (_, useCase) = Arrangement()
                .withRepositoryReturningMinimumApiVersion()
                .withServerConfig(7)
                .withTeamId(Either.Right(null))
                .arrange()

            // When
            val result = useCase.invoke()

            // Then
            assertTrue(result)
        }

    @Test
    fun givenAPIVersionAboveMinimumAndUserInATeam_whenInvoking_thenReturnsFalse() = runTest {
        // Given
        val (_, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withTeamId(Either.Right(TeamId("teamId")))
            .withServerConfig(9)
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
    }


    @Test
    fun givenSelfTeamIdProviderFailure_whenInvoking_thenReturnsFalse() = runTest {
        // Given
        val (_, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withTeamId(Either.Left(CoreFailure.MissingClientRegistration))
            .withServerConfig(9)
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
    }

    private class Arrangement {
        
        val serverConfigRepository = mock(ServerConfigRepository::class)
        val sessionManager = mock(SessionManager::class)
        val selfTeamIdProvider = mock(SelfTeamIdProvider::class)

        suspend fun withTeamId(result: Either<CoreFailure, TeamId?>) = apply {
            coEvery {
                selfTeamIdProvider()
            }.returns(result)
        }

        fun withRepositoryReturningMinimumApiVersion() = apply {
            every {
                serverConfigRepository.minimumApiVersionForPersonalToTeamAccountMigration
            }.returns(MIN_API_VERSION)
        }

        fun withServerConfig(apiVersion: Int) = apply {
            val backendConfig = TEST_BACKEND_CONFIG.copy(
                metaData = TEST_BACKEND_CONFIG.metaData.copy(
                    commonApiVersion = ApiVersionDTO.Valid(apiVersion)
                )
            )
            every {
                sessionManager.serverConfig()
            }.returns(backendConfig)
        }

        fun arrange() = this to CanMigrateFromPersonalToTeamUseCaseImpl(
            sessionManager = sessionManager,
            serverConfigRepository = serverConfigRepository,
            selfTeamIdProvider = selfTeamIdProvider
        )
    }

    companion object {
        private const val MIN_API_VERSION = 7
    }
}
