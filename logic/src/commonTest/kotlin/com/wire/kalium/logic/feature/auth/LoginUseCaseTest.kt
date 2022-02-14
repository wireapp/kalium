package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.AuthenticationFailure
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LoginUseCaseTest {

    @Mock
    val loginRepository = mock(classOf<LoginRepository>())

    @OptIn(ConfigurationApi::class)
    @Mock
    val sessionRepository = configure(mock(classOf<SessionRepository>())) { stubsUnitByDefault = true }

    @Mock
    val validateEmailUseCase = mock(classOf<ValidateEmailUseCase>())

    @Mock
    val validateUserHandleUseCase = mock(classOf<ValidateUserHandleUseCase>())

    lateinit var loginUseCase: LoginUseCase


    @BeforeTest
    fun setup() {
        loginUseCase = LoginUseCase(loginRepository, sessionRepository, validateEmailUseCase, validateUserHandleUseCase)
    }

    @Test
    fun givenLoginUserCaseIsInvoked_whenEmailHasLeadingOrTrailingSpaces_thenCleanUserIdentifierIsUsedToAuthenticate() =
        runTest {
            val cleanEmail = "user@email.de"
            val password = "password"
            given(validateEmailUseCase).invocation { invoke(cleanEmail) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(cleanEmail) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(cleanEmail, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }
            given(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }

            val loginUserCaseResult = loginUseCase("   user@email.de  ", password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(cleanEmail) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(cleanEmail) }.wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(cleanEmail, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(loginRepository).suspendFunction(loginRepository::loginWithHandle).with(any(), any(), any(), any()).wasNotInvoked()

            verify(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenLoginUserCaseIsInvoked_whenUserHandleHasLeadingOrTrailingSpaces_thenCleanUserIdentifierIsUsedToAuthenticate() =
        runTest {
            val cleanHandle = "usere"
            val password = "password"
            given(validateEmailUseCase).invocation { invoke(cleanHandle) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(cleanHandle) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(cleanHandle, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }
            given(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }

            val loginUserCaseResult = loginUseCase("   usere  ", password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(cleanHandle) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(cleanHandle) }.wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(cleanHandle, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)

            verify(loginRepository).suspendFunction(loginRepository::loginWithEmail).with(any(), any(), any(), any()).wasNotInvoked()

            verify(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }.wasInvoked(exactly = once)
        }

    @Test
    fun `given LoginUseCase is invoked, When ValidateEmailUseCase, loginWithEmail and storeSession return success, then returns success`() =
        runTest {

            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }
            given(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).function(validateUserHandleUseCase::invoke).with(any()).wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(loginRepository).suspendFunction(loginRepository::loginWithHandle).with(any(), any(), any(), any()).wasNotInvoked()

            verify(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }.wasInvoked(exactly = once)
        }

    @Test
    fun `given LoginUseCase is invoked, When ValidateEmailUseCase return false and validateUserHandleUseCase loginWithEmail and storeSession return success, then returns success`() =
        runTest {
            // given
            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }
            given(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }

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
            verify(sessionRepository)
                .coroutine { storeSession(TEST_AUTH_SESSION) }
                .wasInvoked(exactly = once)
            verify(sessionRepository)
                .coroutine { updateCurrentSession(TEST_AUTH_SESSION.userId) }
                .wasInvoked(exactly = once)
        }

    @Test
    fun `given LoginUseCase is invoked, When ValidateEmailUseCase and validateUserHandleUseCase return false, then returns InvalidUserIdentifier`() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Failure.InvalidUserIdentifier)

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
        }

    @Test
    fun `given LoginUseCase is invoked, When loginRepository return InvalidCredentials, return InvalidCredentials`() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(AuthenticationFailure.InvalidCredentials) }

            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(AuthenticationFailure.InvalidCredentials) }

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
            verify(sessionRepository)
                .suspendFunction(sessionRepository::storeSession)
                .with(any())
                .wasNotInvoked()
            verify(sessionRepository)
                .suspendFunction(sessionRepository::updateCurrentSession)
                .with(any())
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
            verify(sessionRepository)
                .suspendFunction(sessionRepository::storeSession)
                .with(any())
                .wasNotInvoked()
            verify(sessionRepository)
                .suspendFunction(sessionRepository::updateCurrentSession)
                .with(any())
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
            websiteUrl = "websiteUrl.com"
        )
        val TEST_AUTH_SESSION =
            AuthSession(
                userId = "user_id",
                accessToken = "access_token",
                refreshToken = "refresh_token",
                tokenType = "token_type",
                TEST_SERVER_CONFIG
            )
    }
}
