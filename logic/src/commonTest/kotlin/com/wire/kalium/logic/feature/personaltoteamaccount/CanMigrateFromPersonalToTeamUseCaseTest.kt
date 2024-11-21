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

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.session.SessionManager
import com.wire.kalium.network.utils.TestRequestHandler.Companion.TEST_BACKEND_CONFIG
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CanMigrateFromPersonalToTeamUseCaseTest {

    @Test
    fun givenAPIVersionBelowMinimumAndUserNotInATeam_whenInvoking_thenReturnsFalse() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withTeamId(null)
            .withServerConfig(6)
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
    }

    @Test
    fun givenAPIVersionEqualToMinimumAndUserNotInATeam_whenInvoking_thenReturnsTrue() =
        runBlocking {
            // Given
            val (arrangement, useCase) = Arrangement()
                .withRepositoryReturningMinimumApiVersion()
                .withServerConfig(7)
                .withTeamId(null)
                .arrange()

            // When
            val result = useCase.invoke()

            // Then
            assertTrue(result)
        }

    @Test
    fun givenAPIVersionAboveMinimumAndUserInATeam_whenInvoking_thenReturnsFalse() = runBlocking {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withTeamId(TeamId("teamId"))
            .withServerConfig(9)
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
    }

    private class Arrangement {

        @Mock
        val serverConfigRepository = mock(ServerConfigRepository::class)

        @Mock
        val sessionManager = mock(SessionManager::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        suspend fun withTeamId(result: TeamId?) = apply {
            coEvery {
                userRepository.getSelfUser()
            }.returns(TestUser.SELF.copy(teamId = result))
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
            userRepository = userRepository
        )
    }

    companion object {
        private const val MIN_API_VERSION = 7
    }
}
