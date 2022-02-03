package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.failure.InvalidCredentials
import com.wire.kalium.logic.functional.Either
import io.mockative.ConfigurationApi
import io.mockative.Mock
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
            verify(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }.wasInvoked(exactly = once)
        }

    @Test
    fun `given LoginUseCase is invoked, When ValidateEmailUseCase return false and validateUserHandleUseCase loginWithEmail and storeSession return success, then returns success`() =
        runTest {
            // given
            given(validateEmailUseCase).invocation { invoke(TEST_HANDEL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDEL) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDEL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(TEST_AUTH_SESSION)
            }
            given(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }

            // when
            val loginUserCaseResult = loginUseCase(TEST_HANDEL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            // then
            assertEquals(loginUserCaseResult, AuthenticationResult.Success(TEST_AUTH_SESSION))

            verify(validateEmailUseCase).invocation { invoke(TEST_HANDEL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_HANDEL) }.wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(TEST_HANDEL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
            verify(sessionRepository).coroutine { storeSession(TEST_AUTH_SESSION) }.wasInvoked(exactly = once)
        }

    @Test
    fun `given LoginUseCase is invoked, When ValidateEmailUseCase and validateUserHandleUseCase return false, then returns InvalidUserIdentifier`() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, AuthenticationResult.Failure.InvalidUserIdentifier)

            verify(validateEmailUseCase).invocation { invoke(TEST_HANDEL) }.wasNotInvoked()
            verify(validateUserHandleUseCase).invocation { invoke(TEST_HANDEL) }.wasNotInvoked()
        }

    @Test
    fun `given LoginUseCase is invoked, When loginRepository return InvalidCredentials, return InvalidCredentials`() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(InvalidCredentials) }

            given(validateEmailUseCase).invocation { invoke(TEST_HANDEL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDEL) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDEL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(InvalidCredentials) }

            // email
            val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            assertEquals(loginEmailResult, AuthenticationResult.Failure.InvalidCredentials)
            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.wasNotInvoked()
            verify(loginRepository).coroutine {
                loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)

            // user handle
            val loginHandelResult = loginUseCase(TEST_HANDEL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            assertEquals(loginHandelResult, AuthenticationResult.Failure.InvalidCredentials)
            verify(validateEmailUseCase).invocation { invoke(TEST_HANDEL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_HANDEL) }.wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(TEST_HANDEL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)
        }

    private companion object {
        const val TEST_EMAIL = "email@fu-berlin.de"
        const val TEST_HANDEL = "cool_user"
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
