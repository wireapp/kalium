package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.session.SessionMapper
import com.wire.kalium.logic.data.user.SsoId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthTokens
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.utils.NetworkResponse
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
import com.wire.kalium.network.api.UserId as UserIdDTO

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterAccountRepositoryTest {
    @Mock
    private val registerApi: RegisterApi = mock(classOf<RegisterApi>())

    @Mock
    private val idMapper = mock(classOf<IdMapper>())

    @Mock
    private val sessionMapper = mock(classOf<SessionMapper>())

    private lateinit var registerAccountRepository: RegisterAccountRepository

    @BeforeTest
    fun setup() {
        registerAccountRepository = RegisterAccountDataSource(registerApi, idMapper, sessionMapper)
    }

    @Test
    fun givenApiRequestSuccess_whenRequestingActivationCodeForAnEmail_thenSuccessIsPropagated() = runTest {
        val expected = Unit
        val email = "user@domain.de"
        given(registerApi).coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email)) }
            .then { NetworkResponse.Success(expected, mapOf(), 200) }

        val actual = registerAccountRepository.requestEmailActivationCode(email)

        assertIs<Either.Right<Unit>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi).coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email)) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestFail_whenRequestingActivationCodeForAnEmail_thenNetworkFailureIsPropagated() = runTest {
        val expected = TestNetworkException.generic
        val email = "user@domain.de"
        given(registerApi).coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email)) }
            .then { NetworkResponse.Error(expected) }

        val actual = registerAccountRepository.requestEmailActivationCode(email)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)
        verify(registerApi).coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email)) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestSuccess_whenActivatingAnEmail_thenSuccessIsPropagated() = runTest {
        val expected = Unit
        val email = "user@domain.de"
        val code = "123456"
        given(registerApi).coroutine { activate(RegisterApi.ActivationParam.Email(email, code)) }
            .then { NetworkResponse.Success(expected, mapOf(), 200) }

        val actual = registerAccountRepository.verifyActivationCode(email, code)

        assertIs<Either.Right<Unit>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi).coroutine { activate(RegisterApi.ActivationParam.Email(email, code)) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestFail_whenActivatingAnEmail_thenNetworkFailureIsPropagated() = runTest {
        val expected = TestNetworkException.generic
        val email = "user@domain.de"
        val code = "123456"
        given(registerApi).coroutine { activate(RegisterApi.ActivationParam.Email(email, code)) }.then { NetworkResponse.Error(expected) }

        val actual = registerAccountRepository.verifyActivationCode(email, code)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verify(registerApi).coroutine { activate(RegisterApi.ActivationParam.Email(email, code)) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestSuccess_whenRegisteringPersonalAccountWithEmail_thenSuccessIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val ssoId = with(TEST_USER.ssoID) {
            this?.let { SsoId(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }
        }
        val authTokens = with(SESSION) {
            AuthTokens(
                userId = UserId(userId.value, userId.domain),
                accessToken = accessToken,
                refreshToken = refreshToken,
                tokenType = tokenType
            )
        }
        val expected = Pair(ssoId, authTokens)

        given(registerApi).coroutine {
            register(
                RegisterApi.RegisterParam.PersonalAccount(email, code, name, password)
            )
        }.then { NetworkResponse.Success(Pair(TEST_USER, SESSION), mapOf(), 200) }
        given(idMapper).invocation { toSsoId(TEST_USER.ssoID) }.then { ssoId }
        given(sessionMapper).invocation { fromSessionDTO(SESSION) }.then { authTokens }

        val actual = registerAccountRepository.registerPersonalAccountWithEmail(email, code, name, password)

        assertIs<Either.Right<Pair<SsoId?, AuthTokens>>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi).coroutine { register(RegisterApi.RegisterParam.PersonalAccount(email, code, name, password)) }
            .wasInvoked(exactly = once)
        verify(sessionMapper).function(sessionMapper::fromSessionDTO).with(any()).wasInvoked(exactly = once)
        verify(idMapper).invocation { toSsoId(TEST_USER.ssoID) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestSuccess_whenRegisteringTeamAccountWithEmail_thenSuccessIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val teamName = TEAM_NAME
        val teamIcon = TEAM_ICON
        val ssoId = with(TEST_USER.ssoID) {
            this?.let { SsoId(scimExternalId = it.scimExternalId, subject = it.subject, tenant = it.tenant) }
        }
        val authTokens =
            with(SESSION) {
                AuthTokens(
                    userId = UserId(userId.value, userId.domain),
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = tokenType
                )
            }
        val expected = Pair(ssoId, authTokens)

        given(registerApi).coroutine {
            register(RegisterApi.RegisterParam.TeamAccount(email, code, name, password, teamName, teamIcon))
        }.then { NetworkResponse.Success(Pair(TEST_USER, SESSION), mapOf(), 200) }
        given(idMapper).invocation { toSsoId(TEST_USER.ssoID) }.then { ssoId }
        given(sessionMapper)
            .invocation { fromSessionDTO(SESSION) }
            .then { authTokens }

        val actual = registerAccountRepository.registerTeamWithEmail(email, code, name, password, teamName, teamIcon)

        assertIs<Either.Right<Pair<SsoId?,AuthTokens>>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi).coroutine {
            register(RegisterApi.RegisterParam.TeamAccount(email, code, name, password, teamName, teamIcon))
        }.wasInvoked(exactly = once)
        verify(sessionMapper).invocation {
            fromSessionDTO(SESSION)
        }.wasInvoked(exactly = once)
        verify(idMapper).invocation { toSsoId(TEST_USER.ssoID) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestFail_whenRegisteringWithEmail_thenNetworkFailureIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val expected = TestNetworkException.generic

        given(registerApi).coroutine {
            register(
                RegisterApi.RegisterParam.PersonalAccount(email, code, name, password)
            )
        }.then { NetworkResponse.Error(expected) }

        val actual = registerAccountRepository.registerPersonalAccountWithEmail(email, code, name, password)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verify(registerApi).coroutine {
            register(
                RegisterApi.RegisterParam.PersonalAccount(email, code, name, password)
            )
        }.wasInvoked(exactly = once)
        verify(sessionMapper)
            .function(sessionMapper::fromSessionDTO)
            .with(any())
            .wasNotInvoked()
        verify(idMapper).invocation { toSsoId(TEST_USER.ssoID) }.wasNotInvoked()

    }

    private companion object {
        const val NAME = "user_name"
        const val EMAIL = "user@domain.de"
        const val CODE = "123456"
        const val PASSWORD = "password"
        const val TEAM_NAME = "teamName"
        const val TEAM_ICON = "teamIcon"
        val USERID_DTO = UserIdDTO("user_id", "domain.com")
        val SESSION: SessionDTO = SessionDTO(USERID_DTO, "tokenType", "access_token", "refresh_token")
        val TEST_USER: UserDTO = UserDTO(
            id = USERID_DTO,
            name = NAME,
            email = EMAIL,
            accentId = 1,
            assets = listOf(),
            deleted = null,
            handle = null,
            service = null,
            teamId = null,
            expiresAt = "",
            nonQualifiedId = "",
            locale = "",
            managedByDTO = null,
            phone = null,
            ssoID = null
        )
    }
}
