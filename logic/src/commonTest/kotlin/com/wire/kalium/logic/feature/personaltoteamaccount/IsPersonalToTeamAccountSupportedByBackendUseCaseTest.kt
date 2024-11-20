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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsPersonalToTeamAccountSupportedByBackendUseCaseTest {

    @Test
    fun `given API version below minimum when invoking then returns false`() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Either.Right(6))
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
        coVerify {
            arrangement.serverConfigRepository.commonApiVersion(TestUser.USER_ID.domain)
        }.wasInvoked(once)
    }

    @Test
    fun `given API version equal to minimum when invoking then returns true`() = runBlocking {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Either.Right(7))
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertTrue(result)
        coVerify {
            arrangement.serverConfigRepository.commonApiVersion(TestUser.USER_ID.domain)
        }.wasInvoked(once)
    }

    @Test
    fun `given API version above minimum when invoking then returns true`() = runBlocking {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Either.Right(8))
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertTrue(result)
        coVerify {
            arrangement.serverConfigRepository.commonApiVersion(TestUser.USER_ID.domain)
        }.wasInvoked(once)
    }

    @Test
    fun `given error fetching API version when invoking then returns false`() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturning(Either.Left(CoreFailure.SyncEventOrClientNotFound))
            .arrange()

        // When
        val result = useCase.invoke()

        // Then
        assertFalse(result)
        coVerify {
            arrangement.serverConfigRepository.commonApiVersion(TestUser.USER_ID.domain)
        }.wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val serverConfigRepository = mock(ServerConfigRepository::class)

        suspend fun withRepositoryReturning(value: Either<CoreFailure, Int>) = apply {
            coEvery {
                serverConfigRepository.commonApiVersion(TestUser.USER_ID.domain)
            }.returns(value)
        }

        fun arrange() = this to IsPersonalToTeamAccountSupportedByBackendUseCaseImpl(
            serverConfigRepository = serverConfigRepository,
            userId = TestUser.USER_ID
        )
    }
}
