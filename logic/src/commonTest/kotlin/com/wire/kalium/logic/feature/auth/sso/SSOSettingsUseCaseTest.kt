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

package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.base.unauthenticated.sso.SSOSettingsResponse
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SSOSettingsUseCaseTest {

    @Mock
    val ssoLoginRepository = mock(SSOLoginRepository::class)
    lateinit var ssoSettingsUseCase: SSOSettingsUseCase

    @BeforeTest
    fun setup() {
        ssoSettingsUseCase = SSOSettingsUseCaseImpl(ssoLoginRepository)
    }

    @Test
    fun givenApiReturnsGenericError_whenRequestingMetaData_thenReturnGenericFailure() =
        runTest {
            val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
            coEvery {
                ssoLoginRepository.settings() 
            }.returns(Either.Left(expected))
            val result = ssoSettingsUseCase()
            assertIs<SSOSettingsResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenRequestingMetaData_thenReturnSuccess() =
        runTest {
            coEvery {
                ssoLoginRepository.settings() 
            }.returns(Either.Right(TEST_RESPONSE))
            val result = ssoSettingsUseCase()
            assertEquals(result, SSOSettingsResult.Success(TEST_RESPONSE))
        }

    private companion object {
        val TEST_RESPONSE = SSOSettingsResponse("default_code")
    }
}
