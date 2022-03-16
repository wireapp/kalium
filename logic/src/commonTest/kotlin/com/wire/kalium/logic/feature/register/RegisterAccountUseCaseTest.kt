package com.wire.kalium.logic.feature.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ServerConfig
import com.wire.kalium.logic.data.register.RegisterAccountRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
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

    @Mock
    private val sessionRepository = mock(classOf<SessionRepository>())

    private lateinit var registerAccountUseCase: RegisterAccountUseCase

    @BeforeTest
    fun setup() {
        registerAccountUseCase = RegisterAccountUseCase(registerAccountRepository, sessionRepository)
    }

    @Test
    fun givenRepositoryCallIsSuccessful_whenRegisteringPersonalAccount_thenSaucesIsPropagated() = runTest {
        val serverConfig = TEST_SERVER_CONFIG
        val param = TEST_PRIVATE_ACCOUNT_PARAM
        val user = TEST_SELF_USER
        val session = TEST_AUTH_SESSION
        val expected = Pair(user, session)

        given(registerAccountRepository)
            .coroutine { registerWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig) }
            .then { Either.Right(expected) }
        given(sessionRepository)
            .invocation { storeSession(expected.second) }
            .then { Unit } // Unit here is intentional since this will change after using wrapStorageRequest
        given(sessionRepository)
            .invocation { updateCurrentSession(expected.second.userId) }
            .then { Unit } // Unit here is intentional since this will change after using wrapStorageRequest

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Success>(actual)
        assertEquals(expected, actual.value)

        verify(registerAccountRepository)
            .coroutine { registerWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig) }
            .wasInvoked(exactly = once)
        verify(sessionRepository)
            .invocation { storeSession(session) }
            .wasInvoked(exactly = once)
        verify(sessionRepository)
            .invocation { updateCurrentSession(session.userId) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRepositoryCallFailWithGenericError_whenRegisteringPersonalAccount_thenErrorIsPropagated() = runTest {
        val serverConfig = TEST_SERVER_CONFIG
        val param = TEST_PRIVATE_ACCOUNT_PARAM
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(registerAccountRepository)
            .coroutine { registerWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig) }
            .then { Either.Left(expected) }

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Failure.Generic>(actual)
        assertEquals(expected, actual.failure)

        verify(registerAccountRepository)
            .coroutine { registerWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig) }
            .wasInvoked(exactly = once)
        verify(sessionRepository)
            .function(sessionRepository::storeSession)
            .with(any())
            .wasNotInvoked()
        verify(sessionRepository)
            .function(sessionRepository::updateCurrentSession)
            .with(any())
            .wasNotInvoked()
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
            .coroutine { registerWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig) }
            .then { Either.Left(expected) }

        val actual = registerAccountUseCase(param, serverConfig)

        assertIs<RegisterResult.Failure>(actual)
        assertEquals(error, actual)

        verify(registerAccountRepository)
            .coroutine { registerWithEmail(param.email, param.emailActivationCode, param.name, param.password, serverConfig) }
            .wasInvoked(exactly = once)
        verify(sessionRepository)
            .function(sessionRepository::storeSession)
            .with(any())
            .wasNotInvoked()
        verify(sessionRepository)
            .function(sessionRepository::updateCurrentSession)
            .with(any())
            .wasNotInvoked()
    }


    private companion object {
        const val TEST_EMAIL = """user@domain.com"""
        const val TEST_CODE = "123456"
        const val TEST_PASSWORD = "password"
        val TEST_SERVER_CONFIG: ServerConfig = ServerConfig(
            apiBaseUrl = "apiBaseUrl.com",
            accountsBaseUrl = "accountsUrl.com",
            webSocketBaseUrl = "webSocketUrl.com",
            blackListUrl = "blackListUrl.com",
            teamsUrl = "teamsUrl.com",
            websiteUrl = "websiteUrl.com",
            title = "Test Title"
        )
        val TEST_PRIVATE_ACCOUNT_PARAM = RegisterParam.PrivateAccount(
            firstName = "first", lastName = "last", email = TEST_EMAIL, password = TEST_PASSWORD, emailActivationCode = TEST_CODE
        )
        val TEST_SELF_USER = SelfUser(
            id = UserId(value = "user_id", domain = "domain.com"),
            name = TEST_PRIVATE_ACCOUNT_PARAM.name,
            handle = null,
            email = TEST_PRIVATE_ACCOUNT_PARAM.email,
            phone = null,
            accentId = 3,
            team = null,
            previewPicture = null,
            completePicture = null
        )
        val TEST_AUTH_SESSION = AuthSession(TEST_SELF_USER.id.value, "access_token", "refresh_token", "token_type", TEST_SERVER_CONFIG)
    }
}
