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
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsPersonalToTeamAccountSupportedByBackendUseCaseTest {

    @Test
    fun givenAPIVersionBelowMinimum_whenInvoking_thenReturnsFalse() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withRepositoryReturningCommonApiVersion(Either.Right(6))
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
    fun givenAPIVersionEqualToMinimum_whenInvoking_thenReturnsTrue() = runBlocking {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withRepositoryReturningCommonApiVersion(Either.Right(7))
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
    fun givenAPIVersionAboveMinimum_whenInvoking_thenReturnsTrue() = runBlocking {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withRepositoryReturningCommonApiVersion(Either.Right(8))
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
    fun givenErrorFetchingAPIVersion_whenInvoking_thenReturnsFalse() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withRepositoryReturningMinimumApiVersion()
            .withRepositoryReturningCommonApiVersion(Either.Left(CoreFailure.SyncEventOrClientNotFound))
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

        suspend fun withRepositoryReturningCommonApiVersion(value: Either<CoreFailure, Int>) = apply {
            coEvery {
                serverConfigRepository.commonApiVersion(TestUser.USER_ID.domain)
            }.returns(value)
        }

        fun withRepositoryReturningMinimumApiVersion() = apply {
            every {
                serverConfigRepository.minimumApiVersionForPersonalToTeamAccountMigration
            }.returns(MIN_API_VERSION)
        }

        fun arrange() = this to IsPersonalToTeamAccountSupportedByBackendUseCaseImpl(
            serverConfigRepository = serverConfigRepository,
            userId = TestUser.USER_ID
        )
    }

    companion object {
        private const val MIN_API_VERSION = 7
    }
}
