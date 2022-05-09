package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterAccountUseCaseTest {
    @Mock
    private val registerAccountRepository = mock(classOf<RegisterAccountRepository>())

    private lateinit var registerAccountUseCase: RegisterAccountUseCase

    @BeforeTest
    fun setup() {
        registerAccountUseCase = RegisterAccountUseCase(registerAccountRepository)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_whenRegisteringPersonalAccount_thenSuccessIsPropagated() = runTest {
        val serverConfig = TEST_SERVER_CONFIG
        val param = TEST_PRIVATE_ACCOUNT_PARAM
        val user = TEST_SELF_USER
        val session = TEST_AUTH_SESSION
        val expected = Pair(user, session)

        given(registerAccountRepository)
            .coroutine {
                registerPersonalAccountWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig)
            }
            .then { Either.Right(expected) }

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Success>(actual)
        assertEquals(expected, actual.value)

        verify(registerAccountRepository)
            .coroutine {
                registerPersonalAccountWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig)
            }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_whenRegisteringTeamAccount_thenSuccessIsPropagated() = runTest {
        val serverConfig = TEST_SERVER_CONFIG
        val param = TEST_TEAM_ACCOUNT_PARAM
        val user = TEST_SELF_USER
        val session = TEST_AUTH_SESSION
        val expected = Pair(user, session)

        given(registerAccountRepository)
            .coroutine {
                registerTeamWithEmail(
                    param.email, param.emailActivationCode, param.name, param.password, param.teamName, param.teamIcon, serverConfig
                )
            }
            .then { Either.Right(expected) }

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Success>(actual)
        assertEquals(expected, actual.value)

        verify(registerAccountRepository)
            .coroutine {
                registerTeamWithEmail(
                    param.email, param.emailActivationCode, param.name, param.password, param.teamName, param.teamIcon, serverConfig
                )
            }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_shouldStoreSessionIsFalse_whenRegisteringPersonalAccount_thenDoNotStoreSessionAndReturnSuccess() =
        runTest {
            val serverConfig = TEST_SERVER_CONFIG
            val param = TEST_PRIVATE_ACCOUNT_PARAM
            val user = TEST_SELF_USER
            val session = TEST_AUTH_SESSION
            val expected = Pair(user, session)

            given(registerAccountRepository)
                .coroutine {
                    registerPersonalAccountWithEmail(
                        param.email,
                        param.emailActivationCode,
                        param.name,
                        param.password,
                        serverConfig
                    )
                }
                .then { Either.Right(expected) }

            val actual = registerAccountUseCase(param, serverConfig)

            assertIs<RegisterResult.Success>(actual)
            assertEquals(expected, actual.value)

            verify(registerAccountRepository)
                .coroutine {
                    registerPersonalAccountWithEmail(
                        param.email,
                        param.emailActivationCode,
                        param.name,
                        param.password,
                        serverConfig
                    )
                }
                .wasInvoked(exactly = once)
        }

    @Test
    fun givenRepositoryCallFailWithGenericError_whenRegisteringPersonalAccount_thenErrorIsPropagated() = runTest {
        val serverConfig = TEST_SERVER_CONFIG
        val param = TEST_PRIVATE_ACCOUNT_PARAM
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(registerAccountRepository)
            .coroutine {
                registerPersonalAccountWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig)
            }
            .then { Either.Left(expected) }

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Failure.Generic>(actual)
        assertIs<NetworkFailure.ServerMiscommunication>(actual.failure)
        assertEquals(expected.kaliumException, (actual.failure as NetworkFailure.ServerMiscommunication).kaliumException)

        verify(registerAccountRepository)
            .coroutine {
                registerPersonalAccountWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig)
            }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithInvalidEmail_whenRegisteringPersonalAccount_thenErrorIsPropagated() =
        testSpecificError(TestNetworkException.invalidEmail, RegisterResult.Failure.InvalidEmail)

    @Test
    fun givenRepositoryCallFailWithInvalidCode_whenRegisteringPersonalAccount_thenErrorIsPropagated() =
        testSpecificError(TestNetworkException.invalidCode, RegisterResult.Failure.InvalidActivationCode)

    @Test
    fun givenRepositoryCallFailWithKeyExists_whenRegisteringPersonalAccount_thenErrorIsPropagated() =
        testSpecificError(TestNetworkException.blackListedEmail, RegisterResult.Failure.BlackListed)

    @Test
    fun givenRepositoryCallFailWithUserCreationRestricted_whenRegisteringPersonalAccount_thenErrorIsPropagated() =
        testSpecificError(TestNetworkException.userCreationRestricted, RegisterResult.Failure.UserCreationRestricted)

    @Test
    fun givenRepositoryCallFailWithTooMAnyMembers_whenRegisteringPersonalAccount_thenErrorIsPropagated() =
        testSpecificError(TestNetworkException.tooManyTeamMembers, RegisterResult.Failure.TeamMembersLimitReached)

    @Test
    fun givenRepositoryCallFailWithDomainBlockedForRegistration_whenRegisteringPersonalAccount_thenErrorIsPropagated() =
        testSpecificError(TestNetworkException.domainBlockedForRegistration, RegisterResult.Failure.EmailDomainBlocked)


    private fun testSpecificError(kaliumException: KaliumException, error: RegisterResult.Failure) = runTest {
        val serverConfig = TEST_SERVER_CONFIG
        val param = TEST_PRIVATE_ACCOUNT_PARAM
        val expected = NetworkFailure.ServerMiscommunication(kaliumException)

        given(registerAccountRepository)
            .coroutine {
                registerPersonalAccountWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig)
            }
            .then { Either.Left(expected) }

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Failure>(actual)
        assertEquals(error, actual)

        verify(registerAccountRepository)
            .coroutine {
                registerPersonalAccountWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig)
            }
            .wasInvoked(exactly = once)
    }


    private companion object {
        const val TEST_EMAIL = """user@domain.com"""
        const val TEST_CODE = "123456"
        const val TEST_PASSWORD = "password"
        val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)
        val TEST_PRIVATE_ACCOUNT_PARAM = RegisterParam.PrivateAccount(
            firstName = "first", lastName = "last", email = TEST_EMAIL, password = TEST_PASSWORD, emailActivationCode = TEST_CODE
        )
        val TEST_TEAM_ACCOUNT_PARAM = RegisterParam.Team(
            firstName = "first", lastName = "last", email = TEST_EMAIL, password = TEST_PASSWORD, emailActivationCode = TEST_CODE,
            teamName = "teamName", teamIcon = "teamIcon"
        )
        val TEST_SELF_USER = SelfUser(
            id = UserId(value = "user_id", domain = "domain.com"),
            name = TEST_PRIVATE_ACCOUNT_PARAM.name,
            handle = null,
            email = TEST_PRIVATE_ACCOUNT_PARAM.email,
            phone = null,
            accentId = 3,
            team = null,
            connectionStatus = ConnectionState.ACCEPTED,
            previewPicture = null,
            completePicture = null
        )
        val TEST_AUTH_SESSION = AuthSession(TEST_SELF_USER.id, "access_token", "refresh_token", "token_type", TEST_SERVER_CONFIG)
    }
}
