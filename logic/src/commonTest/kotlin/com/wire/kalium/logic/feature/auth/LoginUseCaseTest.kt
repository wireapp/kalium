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
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newTestServer
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.verify.VerifyMode
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.verifySuspend
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
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
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, null, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(cleanEmail)
        }

        verify(VerifyMode.not) {
            arrangement.validateUserHandleUseCase.invoke(cleanEmail)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(cleanEmail, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT, TEST_2FA_CODE)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }
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
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, null, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(cleanHandle)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(cleanHandle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(cleanHandle, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingEmail_thenStoreTheSessionAndReturnSuccess() = runTest {
        val (arrangement, loginUseCase) = Arrangement().arrange()

        val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL, TEST_2FA_CODE)

        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, null, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }
        verify(VerifyMode.not) {
            arrangement.validateUserHandleUseCase.invoke(
                any()
            )
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT, TEST_2FA_CODE)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }
    }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingUserHandle_thenStoreTheSessionAndReturnSuccess() = runTest {
        val (arrangement, loginUseCase) = Arrangement().arrange()

        // when
        val loginUserCaseResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, true, TEST_LABEL, TEST_2FA_CODE)

        // then
        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, null, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_HANDLE)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(TEST_HANDLE)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, true)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenStoreSessionIsFalse_andEverythingElseSucceeds_whenLoggingIn_thenReturnSuccess() = runTest {
        val (_, loginUseCase) = Arrangement().arrange()

        val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, false, TEST_LABEL)

        assertEquals(
            loginUserCaseResult,
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, null, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS)
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

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(TEST_EMAIL)
        }
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

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }
        verify(VerifyMode.not) {
            arrangement.validateUserHandleUseCase.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination,
            loginHandleResult
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_HANDLE)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(TEST_HANDLE)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
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

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_EMAIL)
        }
        verify(VerifyMode.not) {
            arrangement.validateUserHandleUseCase.invoke(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            loginHandleResult,
            AuthenticationResult.Failure.InvalidCredentials.InvalidPasswordIdentityCombination
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(TEST_HANDLE)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(TEST_HANDLE)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.Missing2FA,
            loginHandleResult
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(
            AuthenticationResult.Failure.InvalidCredentials.Invalid2FA,
            loginHandleResult
        )

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
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
            .withLoginUsingHandleResulting(
                com.wire.kalium.logic.data.auth.AuthenticationResult(
                    accountTokens = TEST_AUTH_TOKENS,
                    ssoId = TEST_SSO_ID,
                    managedBy = null,
                ).right()
            )
            .arrange()

        val loginUserCaseResult = loginUseCase(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)

        assertEquals(
            AuthenticationResult.Success(TEST_AUTH_TOKENS, TEST_SSO_ID, null, TEST_SERVER_CONFIG.id, PROXY_CREDENTIALS),
            loginUserCaseResult
        )

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(handle)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(handle, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }

        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
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
            .withLoginUsingHandleResulting(
                com.wire.kalium.logic.data.auth.AuthenticationResult(
                    accountTokens = TEST_AUTH_TOKENS,
                    ssoId = TEST_SSO_ID,
                    managedBy = null,
                ).right()
            )
            .arrange()

        val loginUserCaseResult = loginUseCase(handle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)

        assertEquals(AuthenticationResult.Failure.InvalidUserIdentifier, loginUserCaseResult)

        verify(VerifyMode.exactly(1)) {
            arrangement.validateEmailUseCase.invoke(handle)
        }
        verify(VerifyMode.exactly(1)) {
            arrangement.validateUserHandleUseCase.invoke(handle)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenAccountSuspended_whenLoggingIn_thenReturnAccountSuspendedFailure() = runTest {
        val invalidAuthCodeFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.accountSuspended)

        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(invalidAuthCodeFailure))
            .withLoginUsingHandleResulting(Either.Left(invalidAuthCodeFailure))
            .arrange()

        // email
        val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(AuthenticationResult.Failure.AccountSuspended, loginEmailResult)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(AuthenticationResult.Failure.AccountSuspended, loginHandleResult)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun givenAccountPendingActivation_whenLoggingIn_thenReturnAccountPendingActivationFailure() = runTest {
        val invalidAuthCodeFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.accountPendingActivation)

        val (arrangement, loginUseCase) = Arrangement()
            .withLoginUsingEmailResulting(Either.Left(invalidAuthCodeFailure))
            .withLoginUsingHandleResulting(Either.Left(invalidAuthCodeFailure))
            .arrange()

        // email
        val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(AuthenticationResult.Failure.AccountPendingActivation, loginEmailResult)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithHandle(any(), any(), any(), any())
        }

        // user handle
        val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_LABEL)
        assertEquals(AuthenticationResult.Failure.AccountPendingActivation, loginHandleResult)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.loginRepository.loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_LABEL, TEST_PERSIST_CLIENT)
        }
        verifySuspend(VerifyMode.not) {
            arrangement.loginRepository.loginWithEmail(any(), any(), any(), any(), any())
        }
    }

    private class Arrangement {
        val loginRepository = mock<LoginRepository>(mode = MockMode.autoUnit)
        val validateEmailUseCase = mock<ValidateEmailUseCase>(mode = MockMode.autoUnit)
        val validateUserHandleUseCase = mock<ValidateUserHandleUseCase>(mode = MockMode.autoUnit)

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
                withLoginUsingEmailResulting(com.wire.kalium.logic.data.auth.AuthenticationResult(
                    accountTokens = TEST_AUTH_TOKENS,
                    ssoId = TEST_SSO_ID,
                    managedBy = null,
                ).right())
                withLoginUsingHandleResulting(com.wire.kalium.logic.data.auth.AuthenticationResult(
                    accountTokens = TEST_AUTH_TOKENS,
                    ssoId = TEST_SSO_ID,
                    managedBy = null,
                ).right())
            }
        }

        fun withEmailValidationSucceeding(
            isSucceeding: Boolean,
            email: String = TEST_EMAIL,
        ) = apply {
            every {
                validateEmailUseCase.invoke(email)
            } returns (isSucceeding)
        }

        fun withHandleValidationReturning(
            handleValidationResult: ValidateUserHandleResult,
            handle: String = TEST_HANDLE,
        ) = apply {
            every {
                validateUserHandleUseCase.invoke(handle)
            } returns (handleValidationResult)
        }

        suspend fun withLoginUsingEmailResulting(result: Either<NetworkFailure, com.wire.kalium.logic.data.auth.AuthenticationResult>) = apply {
            everySuspend {
                loginRepository.loginWithEmail(any(), any(), any(), any(), any())
            } returns (result)
        }

        suspend fun withLoginUsingHandleResulting(result: Either<NetworkFailure, com.wire.kalium.logic.data.auth.AuthenticationResult>) = apply {
            everySuspend {
                loginRepository.loginWithHandle(any(), any(), any(), any())
            } returns (result)
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
