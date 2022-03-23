package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.auth.login.LoginRepository
import com.wire.kalium.logic.data.user.SelfUser
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
    fun givenEmailHasLeadingOrTrailingSpaces_thenCleanEmailIsUsedToAuthenticate() = runTest {
        val cleanEmail = "user@email.de"
        val password = "password"
        given(validateEmailUseCase).invocation { invoke(cleanEmail) }.then { true }
        given(validateUserHandleUseCase).invocation { invoke(cleanEmail) }.then { false }
        given(loginRepository).coroutine { loginWithEmail(cleanEmail, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
            Either.Right(Pair(TEST_USER, TEST_AUTH_SESSION))
        }

        val loginUserCaseResult = loginUseCase("   user@email.de  ", password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

        assertEquals(loginUserCaseResult, LoginResult.Success(TEST_AUTH_SESSION, TEST_USER))

        verify(validateEmailUseCase).invocation { invoke(cleanEmail) }.wasInvoked(exactly = once)
        verify(validateUserHandleUseCase).invocation { invoke(cleanEmail) }.wasNotInvoked()
        verify(loginRepository).coroutine {
            loginWithEmail(cleanEmail, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
        }.wasInvoked(exactly = once)
        verify(loginRepository).suspendFunction(loginRepository::loginWithHandle).with(any(), any(), any(), any()).wasNotInvoked()
    }

    @Test
    fun givenUserHandleHasLeadingOrTrailingSpaces_thenCleanUserIdentifierIsUsedToAuthenticate() =
        runTest {
            val cleanHandle = "usere"
            val password = "password"
            given(validateEmailUseCase).invocation { invoke(cleanHandle) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(cleanHandle) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(cleanHandle, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(Pair(TEST_USER, TEST_AUTH_SESSION))
            }

            val loginUserCaseResult = loginUseCase("   usere  ", password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, LoginResult.Success(TEST_AUTH_SESSION, TEST_USER))

            verify(validateEmailUseCase).invocation { invoke(cleanHandle) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(cleanHandle) }.wasInvoked(exactly = once)
            verify(loginRepository).coroutine {
                loginWithHandle(cleanHandle, password, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            }.wasInvoked(exactly = once)

            verify(loginRepository).suspendFunction(loginRepository::loginWithEmail).with(any(), any(), any(), any()).wasNotInvoked()
        }

    @Test
    fun givenStoreSessionIsTrue_andEverythingElseSucceeds_whenLoggingInUsingEmail_thenStoreTheSessionAndReturnSuccess() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(Pair(TEST_USER, TEST_AUTH_SESSION))
            }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, LoginResult.Success(TEST_AUTH_SESSION, TEST_USER))

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
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }.then {
                Either.Right(Pair(TEST_USER, TEST_AUTH_SESSION))
            }

            // when
            val loginUserCaseResult = loginUseCase(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            // then
            assertEquals(loginUserCaseResult, LoginResult.Success(TEST_AUTH_SESSION, TEST_USER))

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
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Right(Pair(TEST_USER, TEST_AUTH_SESSION)) }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, LoginResult.Success(TEST_AUTH_SESSION, TEST_USER))
        }

    @Test
    fun givenEmailIsInvalid_whenLoggingInUsingEmail_thenReturnInvalidUserIdentifier() =
        runTest {
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }

            val loginUserCaseResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)

            assertEquals(loginUserCaseResult, LoginResult.Failure.InvalidUserIdentifier)

            verify(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
            verify(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenWrongPassword_whenLoggingIn_thenReturnInvalidCredentials() =
        runTest {
            val invalidCredentialsFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)
            given(validateEmailUseCase).invocation { invoke(TEST_EMAIL) }.then { true }
            given(validateUserHandleUseCase).invocation { invoke(TEST_EMAIL) }.then { false }
            given(loginRepository).coroutine { loginWithEmail(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(invalidCredentialsFailure) }

            given(validateEmailUseCase).invocation { invoke(TEST_HANDLE) }.then { false }
            given(validateUserHandleUseCase).invocation { invoke(TEST_HANDLE) }.then { true }
            given(loginRepository).coroutine { loginWithHandle(TEST_HANDLE, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG) }
                .then { Either.Left(invalidCredentialsFailure) }

            // email
            val loginEmailResult = loginUseCase(TEST_EMAIL, TEST_PASSWORD, TEST_PERSIST_CLIENT, TEST_SERVER_CONFIG)
            assertEquals(loginEmailResult, LoginResult.Failure.InvalidCredentials)

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
            assertEquals(loginHandleResult, LoginResult.Failure.InvalidCredentials)

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
                userId = "user_id",
                accessToken = "access_token",
                refreshToken = "refresh_token",
                tokenType = "token_type",
                TEST_SERVER_CONFIG
            )
        val TEST_USER: SelfUser = SelfUser(
            id = UserId("user_id", "domain.com"),
            name = "user name",
            email = "user@domain.com",
            accentId = 1,
            handle = null,
            phone = null,
            team = null,
            previewPicture = null,
            completePicture = null,
        )
    }
}
