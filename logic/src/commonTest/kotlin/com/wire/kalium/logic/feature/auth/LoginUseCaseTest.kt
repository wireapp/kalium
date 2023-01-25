/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LoginUseCaseTest {

    @Mock
    private val loginRepository = mock(classOf<LoginRepository>())

    @Mock
    private val validateEmailUseCase = mock(classOf<ValidateEmailUseCase>())

    @Mock
    private val validateUserHandleUseCase = mock(classOf<ValidateUserHandleUseCase>())

    lateinit var loginUseCase: LoginUseCase

    private val serverConfig = TEST_SERVER_CONFIG

    val proxyCredentials = PROXY_CREDENTIALS

    @BeforeTest
    fun setup() {
        loginUseCase =
            LoginUseCaseImpl(
                loginRepository,
                validateEmailUseCase,
                validateUserHandleUseCase,
                serverConfig,
                proxyCredentials
            )
    }

    @Test
    fun givenEmailHasLeadingOrTrailingSpaces_thenCleanEmailIsUsedToAuthenticate() =
        runTest {
            val cleanEmail = TEST_EMAIL
            given(validateEmailUseCase).invocation { invoke(cleanEmail) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(cleanEmail) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }
            given(loginRepository)
                .coroutine { loginWithEmail(cleanEmail, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID) }

            val loginUserCaseResult = loginUseCase("   $cleanEmail  ", TEST_PASSWORD, TEST_PERSIST_CLIENT)

            assertEquals(
                loginUserCaseResult,
                AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, proxyCredentials)
            )

            verify(validateEmailUseCase)
                .invocation { invoke(cleanEmail) }
                .wasInvoked(exactly = once)

            verify(validateUserHandleUseCase)
                .invocation { invoke(cleanEmail) }
                .wasNotInvoked()

            verify(loginRepository)
                .coroutine { loginWithEmail(cleanEmail, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .wasInvoked(exactly = once)

            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithHandle)
                .with(any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenUserHandleHasLeadingOrTrailingSpaces_thenCleanUserIdentifierIsUsedToAuthenticate() =
        runTest {
            val cleanHandle = TEST_HANDLE
            given(validateEmailUseCase).invocation { invoke(cleanHandle) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(cleanHandle) }
                .then { ValidateUserHandleResult.Valid(cleanHandle) }
            given(loginRepository)
                .coroutine { loginWithHandle(cleanHandle, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID) }

            val loginUserCaseResult = loginUseCase("   $cleanHandle  ", TEST_PASSWORD, TEST_PERSIST_CLIENT)

            assertEquals(
                loginUserCaseResult,
                AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, proxyCredentials)
            )

            verify(validateEmailUseCase)
                .invocation { invoke(cleanHandle) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(cleanHandle) }
                .wasInvoked(exactly = once)
            verify(loginRepository)
                .coroutine { loginWithHandle(cleanHandle, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .wasInvoked(exactly = once)

            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail)
                .with(any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingEmail_thenStoreTheSessionAndReturnSuccess() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }
            given(loginRepository)
                .coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID) }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)

            assertEquals(
                loginUserCaseResult,
                AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, proxyCredentials)
            )

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).function(validateUserHandleUseCase::invoke).with(any()).wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            }.wasInvoked(exactly = once)
            verify(loginRepository).suspendFunction(loginRepository::loginWithHandle).with(any(), any(), any()).wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingUserHandle_thenStoreTheSessionAndReturnSuccess() =
        runTest {
            // given
            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }
                .then { ValidateUserHandleResult.Valid(TEST_HANDLE) }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT) }.then {
                Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID)
            }

            // when
            val loginUserCaseResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT)

            // then
            assertEquals(
                loginUserCaseResult,
                AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, proxyCredentials)
            )

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(loginRepository)
                .coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail).with(any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsFalse_andEverythingElseSucceeds_whenLoggingIn_thenDoNotStoreTheSessionAndReturnSuccess() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID) }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)

            assertEquals(
                loginUserCaseResult,
                AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, proxyCredentials)
            )
        }

    @Test
    fun givenEmailIsInvalid_whenLoggingInUsingEmail_thenReturnInvalidUserIdentifier() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)

            assertEquals(loginUserCaseResult, AuthenticationResult.Failure.InvalidUserIdentifier)

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenWrongPassword_whenLoggingIn_thenReturnInvalidCredentials() =
        runTest {
            val invalidCredentialsFailure =
                NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)

            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Left(invalidCredentialsFailure) }

            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }
                .then { ValidateUserHandleResult.Valid(TEST_HANDLE) }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Left(invalidCredentialsFailure) }

            // email
            val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            assertEquals(loginEmailResult, AuthenticationResult.Failure.InvalidCredentials)

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_EMAIL) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .function(validateUserHandleUseCase::invoke)
                .with(any()).wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            }.wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithHandle)
                .with(any(), any(), any())
                .wasNotInvoked()

            // user handle
            val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            assertEquals(loginHandleResult, AuthenticationResult.Failure.InvalidCredentials)

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            }.wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail)
                .with(any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenBadRequest_whenLoggingIn_thenReturnInvalidCredentials() =
        runTest {
            val badRequestFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()) }
            given(loginRepository)
                .coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Left(badRequestFailure) }

            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .then { ValidateUserHandleResult.Valid(TEST_HANDLE) }
            given(loginRepository)
                .coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Left(badRequestFailure) }

            // email
            val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            assertEquals(loginEmailResult, AuthenticationResult.Failure.InvalidCredentials)

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_EMAIL) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .function(validateUserHandleUseCase::invoke)
                .with(any()).wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            }.wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithHandle)
                .with(any(), any(), any())
                .wasNotInvoked()

            // user handle
            val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            assertEquals(loginHandleResult, AuthenticationResult.Failure.InvalidCredentials)

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT)
            }.wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail)
                .with(any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenUserHandleWithDots_whenLoggingInUsingUserHandle_thenStoreTheSessionAndReturnSuccess() =
        runTest {
            val handle = "cool.user"
            given(validateEmailUseCase).invocation { invoke(handle) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(handle) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("cooluser", listOf('.')) }
            given(loginRepository)
                .coroutine { loginWithHandle(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .then { Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID) }

            val loginUserCaseResult = loginUseCase(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT)

            assertEquals(
                loginUserCaseResult,
                AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, proxyCredentials)
            )

            verify(validateEmailUseCase)
                .invocation { invoke(handle) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(handle) }
                .wasInvoked(exactly = once)
            verify(loginRepository)
                .coroutine { loginWithHandle(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT) }
                .wasInvoked(exactly = once)

            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail)
                .with(any(), any(), any())
                .wasNotInvoked()
        }

    private companion object {
        const val TEST_EMAIL = "email@fu-berlin.de"
        const val TEST_HANDLE = "cool_user"
        const val TEST_PASSWORD = "123456"
        val TEST_PERSIST_CLIENT = Random.nextBoolean()
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)
        val TEST_AUTH_TOKENS = AuthTokens(
            userId = UserId("user_id", "domain.de"),
            accessToken = "access_token",
            refreshToken = "refresh_token",
            tokenType = "token_type"
        )
        val PROXY_CREDENTIALS = ProxyCredentials("user_name", "password")

        val TEST_SSO_ID = SsoId("scim_external", "subject", null)
    }
}
