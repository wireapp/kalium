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
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SSOInitiateLoginUseCaseTest {

    @Mock
    private val ssoLoginRepository = mock(SSOLoginRepository::class)

    @Mock
    private val validateUUIDUseCase = mock(ValidateSSOCodeUseCase::class)

    private val serverConfig = newServerConfig(1)

    private lateinit var ssoInitiateLoginUseCase: SSOInitiateLoginUseCase

    @BeforeTest
    fun setup() {
        ssoInitiateLoginUseCase =
            SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateUUIDUseCase, serverConfig)
    }

    @Test
    fun givenCodeFormatIsInvalid_whenInitiating_thenReturnInvalidCodeFormat() =
        runTest {
            every {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.returns(ValidateSSOCodeResult.Invalid)
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidCodeFormat)
            verify {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.wasInvoked(exactly = once)
        }

    @Test
    fun givenApiReturnsInvalidCode_whenInitiating_thenReturnInvalidCode() =
        runTest {
            every {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.returns(ValidateSSOCodeResult.Valid(TEST_UUID))
            coEvery {
                ssoLoginRepository.initiate(TEST_UUID)
            }.returns(Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.NotFound.value)))
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidCode)
        }

    @Test
    fun givenApiReturnsInvalidRedirect_whenInitiating_thenReturnInvalidRedirect() =
        runTest {
            every {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.returns(ValidateSSOCodeResult.Valid(TEST_UUID))
            coEvery {
                ssoLoginRepository.initiate(TEST_UUID)
            }.returns(Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.BadRequest.value)))
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidRedirect)
        }

    @Test
    fun givenApiReturnsOtherError_whenInitiating_thenReturnGenericFailure() =
        runTest {
            val expected = serverMiscommunicationFailure(code = HttpStatusCode.Forbidden.value)
            every {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.returns(ValidateSSOCodeResult.Valid(TEST_UUID))
            coEvery {
                ssoLoginRepository.initiate(TEST_UUID)
            }.returns(Either.Left(expected))
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertIs<SSOInitiateLoginResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenInitiatingWithoutRedirect_thenReturnSuccess() =
        runTest {
            every {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.returns(ValidateSSOCodeResult.Valid(TEST_UUID))
            coEvery {
                ssoLoginRepository.initiate(TEST_UUID)
            }.returns(Either.Right(TEST_RESPONSE))
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Success(TEST_RESPONSE))
        }

    @Test
    fun givenApiReturnsSuccess_whenInitiatingWitRedirect_thenReturnSuccess() =
        runTest {
            val expectedRedirects = SSORedirects(serverConfig.id)
            every {
                validateUUIDUseCase.invoke(TEST_CODE)
            }.returns(ValidateSSOCodeResult.Valid(TEST_UUID))
            coEvery {
                ssoLoginRepository.initiate(TEST_UUID, expectedRedirects.success, expectedRedirects.error)
            }.returns(Either.Right(TEST_RESPONSE))

            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithRedirect(TEST_CODE))

            assertEquals(result, SSOInitiateLoginResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_UUID = "fd994b20-b9af-11ec-ae36-00163e9b33ca"
        const val TEST_CODE = "wire-$TEST_UUID"
        const val TEST_RESPONSE = "wire/response"
    }
}
