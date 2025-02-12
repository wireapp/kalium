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

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.auth.AccountTokens
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.auth.login.ProxyCredentials
import com.wire.kalium.logic.data.auth.verification.FakeSecondFactorVerificationRepository
import com.wire.kalium.logic.data.auth.verification.SecondFactorVerificationRepository
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newTestServer
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.every
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LoginUseCaseTest {

    @Test
    fun givenEmailHasLeadingOrTrailingSpaces_thenCleanEmailIsUsedToAuthenticate() = runTest {
        val cleanEmail = TEST_EMAIL
        val dirtyEmail = "   $cleanEmail  "

        val (arrangement, loginUseCase) = Arrangement()
            .withEmailValidationSucceeding(true, cleanEmail)
            .arrange()

        val loginUserCaseResult = loginUseCase(dirtyEmail, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL, TEST_2FA_CODE)

        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify {
            arrangement.validateEmailUseCase.invoke(cleanEmail)
        }.wasInvoked(exactly = once)

        verify {
            arrangement.validateUserHandleUseCase.invoke(cleanEmail)
        }.wasNotInvoked()

        coVerify {
            arrangement.loginRepository.loginWithEmail(cleanEmail, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT, TEST_2FA_CODE)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUserHandleHasLeadingOrTrailingSpaces_thenCleanUserIdentifierIsUsedToAuthenticate() = runTest {
        val cleanHandle = TEST_HANDLE
        val dirtyHandle = "   $cleanHandle  "

        val (arrangement, loginUseCase) = Arrangement()
            .withHandleValidationReturning(ValidateUserHandleResult.Valid(cleanHandle), dirtyHandle)
            .arrange()

        val loginUserCaseResult = loginUseCase(dirtyHandle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL, TEST_2FA_CODE)

        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify {
            arrangement.validateEmailUseCase.invoke(cleanHandle)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(cleanHandle)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(cleanHandle, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingEmail_thenStoreTheSessionAndReturnSuccess() = runTest {
        val (arrangement, loginUseCase) = Arrangement().arrange()

        val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL, TEST_2FA_CODE)

        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(
                any()
            )
        }.wasNotInvoked()
        coVerify {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT, TEST_2FA_CODE)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingUserHandle_thenStoreTheSessionAndReturnSuccess() = runTest {
        val (arrangement, loginUseCase) = Arrangement().arrange()

        // when
        val loginUserCaseResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, true, TEST_LABEL, TEST_2FA_CODE)

        // then
        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_HANDLE)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(TEST_HANDLE)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, true)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenStoreSessionIsFalse_andEverythingElseSucceeds_whenLoggingIn_thenReturnSuccess() = runTest {
        val (_, loginUseCase) = Arrangement().arrange()

        val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, false, TEST_LABEL)

        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )
    }

    @Test
    fun givenEverythingSucceeds_whenLoggingInUsingEmail_thenShouldStoreTheUsed2FACode() = runTest {
        val (arrangement, loginUseCase) = Arrangement().arrange()

        loginUseCase(TEST_EMAIL, TEST_PASSWORD, false, TEST_LABEL, TEST_2FA_CODE)

        val storedCode = arrangement.secondFactorVerificationRepository.getStoredVerificationCode(TEST_EMAIL)
        assertEquals(TEST_2FA_CODE, storedCode)
    }

    @Test
    fun givenLoginFails_whenLoggingInUsingEmail_thenShouldNotStoreTheUsed2FACode() = runTest {
        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(NetworkFailure.NoNetworkConnection(null)))
            .arrange()

        loginUseCase(TEST_EMAIL, TEST_PASSWORD, false, TEST_LABEL, TEST_2FA_CODE)

        val storedCode = arrangement.secondFactorVerificationRepository.getStoredVerificationCode(TEST_EMAIL)
        assertNull(storedCode)
    }

    @Test
    fun givenEverythingSucceeds_whenLoggingInUsingUsername_thenShouldNotStoreTheUsed2FACode() = runTest {
        val (arrangement, loginUseCase) = Arrangement().arrange()

        loginUseCase(TEST_HANDLE, TEST_PASSWORD, false, TEST_LABEL, TEST_2FA_CODE)

        val storedCode = arrangement.secondFactorVerificationRepository.getStoredVerificationCode(TEST_EMAIL)
        assertNull(storedCode)
    }

    @Test
    fun givenEmailIsInvalid_whenLoggingInUsingEmail_thenReturnInvalidUserIdentifier() = runTest {
        val (arrangement, loginUseCase) = Arrangement()
            .withEmailValidationSucceeding(false, TEST_EMAIL)
            .withHandleValidationReturning(
                handleValidationResult = ValidateUserHandleResult.Invalid.InvalidCharacters("", listOf()),
                handle = TEST_EMAIL
            )
            .arrange()

        val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)

        assertEquals(AuthenticationResult.Failure.InvalidUserIdentifier, loginUserCaseResult)

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(TEST_EMAIL)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenWrongPassword_whenLoggingIn_thenReturnInvalidCredentials() = runTest {
        val invalidCredentialsFailure =
            NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)

        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(invalidCredentialsFailure))
            .withLoginUsingHandleResulting(Either.Left(invalidCredentialsFailure))
            .arrange()

        // email
        val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination,
            loginEmailResult
        )

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination,
            loginHandleResult
        )

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_HANDLE)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(TEST_HANDLE)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenBadRequest_whenLoggingIn_thenReturnInvalidCredentials() = runTest {
        val badRequestFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)

        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(badRequestFailure))
            .withLoginUsingHandleResulting(Either.Left(badRequestFailure))
            .arrange()

        // email
        val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination,
            loginEmailResult
        )

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            loginHandleResult,
            AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination
        )

        verify {
            arrangement.validateEmailUseCase.invoke(TEST_HANDLE)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(TEST_HANDLE)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenMissingAuthenticationCode_whenLoggingIn_thenReturnMissing2FA() = runTest {
        val missingAuthCodeFailure = NetworkFailure.ServerMiscommunication(
            TestNetworkException.missingAuthenticationCode
        )

        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(missingAuthCodeFailure))
            .withLoginUsingHandleResulting(Either.Left(missingAuthCodeFailure))
            .arrange()

        // email
        val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.Missing2FA,
            loginEmailResult
        )

        coVerify {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.Missing2FA,
            loginHandleResult
        )

        coVerify {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenInvalidAuthenticationCode_whenLoggingIn_thenReturnInvalid2FA() = runTest {
        val invalidAuthCodeFailure = NetworkFailure.ServerMiscommunication(
            TestNetworkException.invalidAuthenticationCode
        )

        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(invalidAuthCodeFailure))
            .withLoginUsingHandleResulting(Either.Left(invalidAuthCodeFailure))
            .arrange()

        // email
        val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.Invalid2FA,
            loginEmailResult
        )

        coVerify {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.Invalid2FA,
            loginHandleResult
        )

        coVerify {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUserHandleWithValidCharacters_whenLoggingInUsingUserHandle_thenReturnSuccess() = runTest {
        val handle = "-cool.user_"

        val (arrangement, loginUseCase) = Arrangement()
            .withEmailValidationSucceeding(
                isSucceeding = false,
                email = handle
            )
            .withHandleValidationReturning(
                handleValidationResult = ValidateUserHandleResult.Valid(handle),
                handle = handle
            )
            .withLoginUsingHandleResulting(Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID))
            .arrange()

        val loginUserCaseResult = loginUseCase(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)

        assertEquals(
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS),
            loginUserCaseResult
        )

        verify {
            arrangement.validateEmailUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(handle, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUserHandleWithInvalidCharacters_whenLoggingInUsingUserHandle_thenReturnInvalidUserIdentifier() = runTest {
        val handle = "!cool:user?"

        val (arrangement, loginUseCase) = Arrangement()
            .withEmailValidationSucceeding(
                isSucceeding = false,
                email = handle
            )
            .withHandleValidationReturning(
                handleValidationResult = ValidateUserHandleResult.Invalid.InvalidCharacters("cooluser", listOf('!', ':', '?')),
                handle = handle
            )
            .withLoginUsingHandleResulting(Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID))
            .arrange()

        val loginUserCaseResult = loginUseCase(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)

        assertEquals(AuthenticationResult.Failure.InvalidUserIdentifier, loginUserCaseResult)

        verify {
            arrangement.validateEmailUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        verify {
            arrangement.validateUserHandleUseCase.invoke(handle)
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }.wasNotInvoked()
        coVerify {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }.wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val loginRepository = mock(LoginRepository::class)

        @Mock
        val validateEmailUseCase = mock(ValidateEmailUseCase::class)

        @Mock
        val validateUserHandleUseCase = mock(ValidateUserHandleUseCase::class)

        val secondFactorVerificationRepository: SecondFactorVerificationRepository = FakeSecondFactorVerificationRepository()

        init {
            runBlocking {
                withEmailValidationSucceeding(true, TEST_EMAIL)
                withEmailValidationSucceeding(false, TEST_HANDLE)
                withHandleValidationReturning(ValidateUserHandleResult.Valid(TEST_HANDLE), TEST_HANDLE)
                withHandleValidationReturning(
                    handleValidationResult = ValidateUserHandleResult.Invalid.InvalidCharacters(
                        "userexampleorg",
                        listOf('@', '.')
                    ),
                    handle = TEST_EMAIL
                )
                withLoginUsingEmailResulting(Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID))
                withLoginUsingHandleResulting(Either.Right(TEST_AUTH_TOKENS to TEST_SSO_ID))
            }
        }

        fun withEmailValidationSucceeding(
            isSucceeding: Boolean,
            email: String = TEST_EMAIL,
        ) = apply {
            every {
                validateEmailUseCase.invoke(email)
            }.returns(isSucceeding)
        }

        fun withHandleValidationReturning(
            handleValidationResult: ValidateUserHandleResult,
            handle: String = TEST_HANDLE,
        ) = apply {
            every {
                validateUserHandleUseCase.invoke(handle)
            }.returns(handleValidationResult)
        }

        suspend fun withLoginUsingEmailResulting(result: Either<NetworkFailure, Pair<AccountTokens, SsoId?>>) = apply {
            coEvery {
                loginRepository.loginWithEmail(any(), any(), any(), any(), any())
            }.returns(result)
        }

        suspend fun withLoginUsingHandleResulting(result: Either<NetworkFailure, Pair<AccountTokens, SsoId?>>) = apply {
            coEvery {
                loginRepository.loginWithHandle(any(), any(), any(), any())
            }.returns(result)
        }

        inline fun arrange(): Pair<Arrangement, LoginUseCase> = this to LoginUseCaseImpl(
            loginRepository,
            validateEmailUseCase,
            validateUserHandleUseCase,
            TEST_SERVER_CONFIG,
            PROXY_CREDENTIALS,
            secondFactorVerificationRepository
        )

    }

    private companion object {
        const val TEST_EMAIL = "user@example.org"
        const val TEST_HANDLE = "cool_user"
        const val TEST_PASSWORD = "123456"
        const val TEST_LABEL = "cookie_label"
        const val TEST_2FA_CODE = "someCool2FA-Code"

        // TODO: Remove random value from tests
        val TEST_PERSIST_CLIENT = Random.nextBoolean()
        val TEST_SERVER_CONFIG: ServerConfig = newTestServer(1)
        val TEST_AUTH_TOKENS = AccountTokens(
            userId = UserId("user_id", "domain.de"),
            accessToken = "access_token",
            refreshToken = "refresh_token",
            tokenType = "token_type",
            cookieLabel = TEST_LABEL,
        )
        val PROXY_CREDENTIALS = ProxyCredentials("user_name", "password")

        val TEST_SSO_ID = SsoId("scim_external", "subject", null)
    }
}
