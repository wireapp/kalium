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

import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import io.ktor.http.HttpStatusCode
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
class SSOFinalizeLoginUseCaseTest {

    @Mock
    val ssoLoginRepository = mock(SSOLoginRepository::class)
    lateinit var ssoFinalizeLoginUseCase: SSOFinalizeLoginUseCase

    @BeforeTest
    fun setup() {
        ssoFinalizeLoginUseCase = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    }

    @Test
    fun givenApiReturnsInvalidCookie_whenFinalizing_thenReturnInvalidCookie() =
        runTest {
            coEvery {
                ssoLoginRepository.finalize(TEST_COOKIE) 
            }.returns(Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.BadRequest.value)))
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE)
            assertEquals(result, SSOFinalizeLoginResult.Failure.InvalidCookie)
        }

    @Test
    fun givenApiReturnsGenericError_whenFinalizing_thenReturnGenericFailure() =
        runTest {
            val expected = serverMiscommunicationFailure(code = HttpStatusCode.Forbidden.value)
            coEvery {
                ssoLoginRepository.finalize(TEST_COOKIE) 
            }.returns(Either.Left(expected))
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE)
            assertIs<SSOFinalizeLoginResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenFinalizing_thenReturnSuccess() =
        runTest {
            coEvery {
                ssoLoginRepository.finalize(TEST_COOKIE) 
            }.returns(Either.Right(TEST_RESPONSE))
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE)
            assertEquals(result, SSOFinalizeLoginResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_COOKIE = "cookie"
        const val TEST_RESPONSE = "wire/response"
    }
}
