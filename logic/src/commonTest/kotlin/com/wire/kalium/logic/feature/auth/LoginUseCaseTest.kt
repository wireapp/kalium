package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
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
    val loginRepository = mock(classOf<LoginRepository>())

    @Mock
    val validateEmailUseCase = mock(classOf<ValidateEmailUseCase>())

    @Mock
    val validateUserHandleUseCase = mock(classOf<ValidateUserHandleUseCase>())

    lateinit var loginUseCase: LoginUseCase


    @BeforeTest
    fun setup() {
        loginUseCase = LoginUseCaseImpl(loginRepository, validateEmailUseCase, validateUserHandleUseCase)
    }

    @Test
    fun givenEmailHasLeadingOrTrailingSpaces_thenCleanEmailIsUsedToAuthenticate() =
        runTest {
            val cleanEmail = TEST_EMAIL
            given(validateEmailUseCase).invocation { invoke(cleanEmail) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(cleanEmail) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("") }
            given(loginRepository).coroutine { loginWithEmail(cleanEmail, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }

            val loginUserCaseResult = loginUseCase("   $cleanEmail  ", TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(cleanEmail) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(cleanEmail) }.wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(cleanEmail, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(loginRepository).suspendFunction(loginRepository::loginWithHandle).with(any(), any(), any(), any()).wasNotInvoked()
        }

    @Test
    fun givenUserHandleHasLeadingOrTrailingSpaces_thenCleanUserIdentifierIsUsedToAuthenticate() =
        runTest {
            val cleanHandle = TEST_HANDLE
            given(validateEmailUseCase).invocation { invoke(cleanHandle) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(cleanHandle) }
                .then { ValidateUserHandleResult.Valid(cleanHandle) }
            given(loginRepository).coroutine { loginWithHandle(cleanHandle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }

            val loginUserCaseResult = loginUseCase("   $cleanHandle  ", TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(cleanHandle) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(cleanHandle) }.wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(cleanHandle, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)

            verify(loginRepository).suspendFunction(loginRepository::loginWithEmail).with(any(), any(), any(), any()).wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingEmail_thenStoreTheSessionAndReturnSuccess() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("") }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).function(validateUserHandleUseCase::invoke).with(any()).wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(loginRepository).suspendFunction(loginRepository::loginWithHandle).with(any(), any(), any(), any()).wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingUserHandle_thenStoreTheSessionAndReturnSuccess() =
        runTest {
            // given
            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }
                .then { ValidateUserHandleResult.Valid(TEST_HANDLE) }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }

            // when
            val loginUserCaseResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            // then
            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(loginRepository)
                .coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail).with(any(), any(), any(), any())
                .wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsFalse_andEverythingElseSucceeds_whenLoggingIn_thenDoNotStoreTheSessionAndReturnSuccess() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("") }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Right(TEST_AUTH_SESSION) }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))
        }

    @Test
    fun givenEmailIsInvalid_whenLoggingInUsingEmail_thenReturnInvalidUserIdentifier() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("") }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Failure.InvalidUserIdentifier)

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenWrongPassword_whenLoggingIn_thenReturnInvalidCredentials() =
        runTest {
            val invalidCredentialsFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }
                .then { ValidateUserHandleResult.Invalid.InvalidCharacters("") }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(invalidCredentialsFailure) }

            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }
                .then { ValidateUserHandleResult.Valid(TEST_HANDLE) }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(invalidCredentialsFailure) }

            // email
            val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            assertEquals(loginEmailResult, AuthenticationResult.Failure.InvalidCredentials)

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_EMAIL) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .function(validateUserHandleUseCase::invoke)
                .with(any()).wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithHandle)
                .with(any(), any(), any(), any())
                .wasNotInvoked()

            // user handle
            val loginHandleResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            assertEquals(loginHandleResult, AuthenticationResult.Failure.InvalidCredentials)

            verify(validateEmailUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(validateUserHandleUseCase)
                .invocation { invoke(TEST_HANDLE) }
                .wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(loginRepository)
                .suspendFunction(loginRepository::loginWithEmail)
                .with(any(), any(), any(), any())
                .wasNotInvoked()
        }

    private companion object {
        const val TEST_EMAIL = "email@fu-berlin.de"
        const val TEST_HANDLE = "cool_user"
        const val TEST_PASSWORD = "123456"
        val TEST_PERSIST_CLIENT = Random.nextBoolean()
        val TEST_SERVER_CONFIG: ServerConfig = ServerConfig(
            apiBaseUrl = "apiBaseUrl.com",
            accountsBaseUrl = "accountsUrl.com",
            webSocketBaseUrl = "webSocketUrl.com",
            blackListUrl = "blackListUrl.com",
            teamsUrl = "teamsUrl.com",
            websiteUrl = "websiteUrl.com",
            title = "Test Title"
        )
        val TEST_AUTH_SESSION =
            AuthSession(
                userId = UserId("user_id", "domain.de"),
                accessToken = "access_token",
                refreshToken = "refresh_token",
                tokenType = "token_type",
                TEST_SERVER_CONFIG
            )
    }
}
