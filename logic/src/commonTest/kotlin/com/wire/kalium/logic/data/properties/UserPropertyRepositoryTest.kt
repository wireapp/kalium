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

package com.wire.kalium.logic.data.properties

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class UserPropertyRepositoryTest {

    @Test
    fun whenUserEnablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withUpdateReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.setReadReceiptsEnabled()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.setProperty(any(), any())
        }.wasInvoked(once)

        verify {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(true))
        }.wasInvoked(once)
    }

    @Test
    fun whenUserDisablingReadReceipts_thenShouldCallApiAndLocalStorageWithCorrectArgs() = runTest {
        val (arrangement, repository) = Arrangement()
            .withDeleteReadReceiptsSuccess()
            .withUpdateReadReceiptsLocallySuccess()
            .arrange()

        val result = repository.deleteReadReceiptsProperty()

        result.shouldSucceed()
        coVerify {
            arrangement.propertiesApi.deleteProperty(any())
        }.wasInvoked(once)

        verify {
            arrangement.userConfigRepository.setReadReceiptsStatus(eq(false))
        }.wasInvoked(once)
    }

    @Test
    fun whenUserReadReceiptsNotPresent_thenShouldReturnsReceiptsAsDefaultFalse() = runTest {
        val (arrangement, repository) = Arrangement()
            .withNullReadReceiptsStatus()
            .arrange()

        val result = repository.getReadReceiptsStatus()

        assertFalse(result)
        verify {
            arrangement.userConfigRepository.isReadReceiptsEnabled()
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val propertiesApi = mock(PropertiesApi::class)

        @Mock
        val userConfigRepository = mock(UserConfigRepository::class)

        private val userPropertyRepository = UserPropertyDataSource(propertiesApi, userConfigRepository)

        suspend fun withUpdateReadReceiptsSuccess() = apply {
            coEvery {
                propertiesApi.setProperty(eq(PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE), eq(1))
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        suspend fun withDeleteReadReceiptsSuccess() = apply {
            coEvery {
                propertiesApi.deleteProperty(eq(PropertiesApi.PropertyKey.WIRE_RECEIPT_MODE))
            }.returns(NetworkResponse.Success(Unit, mapOf(), 200))
        }

        fun withUpdateReadReceiptsLocallySuccess() = apply {
            every {
                userConfigRepository.setReadReceiptsStatus(any())
            }.returns(Either.Right(Unit))
        }

        fun withNullReadReceiptsStatus() = apply {
            every {
                userConfigRepository.isReadReceiptsEnabled()
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun arrange() = this to userPropertyRepository

    }
}
