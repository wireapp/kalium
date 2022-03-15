package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.SessionDTO
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.UserDTO
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.utils.NetworkResponse
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
class RegisterAccountRepositoryTest {
    @Mock
    private val registerApi: RegisterApi = mock(classOf<RegisterApi>())

    private lateinit var registerAccountRepository: RegisterAccountRepository

    @BeforeTest
    fun setup() {
        registerAccountRepository = RegisterAccountDataSource(registerApi)
    }

    @Test
    fun givenApiRequestSuccess_whenRequestingActivationCodeForAnEmail_thenSuccessIsPropagated() = runTest {
        val expected = Unit
        val email = "user@domain.de"
        given(registerApi)
            .coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email), TEST_API_HOST) }
            .then { NetworkResponse.Success(expected, mapOf(), 200) }

        val actual = registerAccountRepository.requestEmailActivationCode(email, TEST_API_HOST)

        assertIs<Either.Right<Unit>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi)
            .coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email), TEST_API_HOST) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestFail_whenRequestingActivationCodeForAnEmail_thenNetworkFailureIsPropagated() = runTest {
        val expected = TestNetworkException.generic
        val email = "user@domain.de"
        given(registerApi)
            .coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email), TEST_API_HOST) }
            .then { NetworkResponse.Error(expected) }

        val actual = registerAccountRepository.requestEmailActivationCode(email, TEST_API_HOST)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)
        verify(registerApi)
            .coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email), TEST_API_HOST) }
            .wasInvoked(exactly = once)
    }


    @Test
    fun givenApiRequestRequestSuccess_whenActivatingAnEmail_thenSuccessIsPropagated() = runTest {
        val expected = Unit
        val email = "user@domain.de"
        val code = "123456"
        given(registerApi)
            .coroutine { activate(RegisterApi.ActivationParam.Email(email, code), TEST_API_HOST) }
            .then { NetworkResponse.Success(expected, mapOf(), 200) }

        val actual = registerAccountRepository.verifyActivationCode(email, code, TEST_API_HOST)

        assertIs<Either.Right<Unit>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi)
            .coroutine { activate(RegisterApi.ActivationParam.Email(email, code), TEST_API_HOST) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestFail_whenActivatingAnEmail_thenNetworkFailureIsPropagated() = runTest {
        val expected = TestNetworkException.generic
        val email = "user@domain.de"
        val code = "123456"
        given(registerApi).coroutine { activate(RegisterApi.ActivationParam.Email(email, code), TEST_API_HOST) }
            .then { NetworkResponse.Error(expected) }

        val actual = registerAccountRepository.verifyActivationCode(email, code, TEST_API_HOST)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verify(registerApi).coroutine { activate(RegisterApi.ActivationParam.Email(email, code), TEST_API_HOST) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestSuccess_whenRegisteringWithEmail_thenSuccessIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val expected = Pair(TEST_USER, SESSION)

        given(registerApi)
            .coroutine {
                register(
                    RegisterApi.RegisterParam.PersonalAccount(email, code, name, password), TEST_API_HOST
                )
            }.then { NetworkResponse.Success(expected, mapOf(), 200) }

        val actual = registerAccountRepository.registerWithEmail(email, code, name, password, TEST_API_HOST)

        assertIs<Either.Right<Pair<UserDTO, SessionDTO>>>(actual)
        assertEquals(expected, actual.value)

        verify(registerApi)
            .coroutine { register(RegisterApi.RegisterParam.PersonalAccount(email, code, name, password), TEST_API_HOST) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestRequestFail_whenRegisteringWithEmail_thenNetworkFailureIsPropagated() = runTest {
        val email = EMAIL
        val code = CODE
        val password = PASSWORD
        val name = NAME
        val expected = TestNetworkException.generic

        given(registerApi)
            .coroutine {
                register(
                    RegisterApi.RegisterParam.PersonalAccount(email, code, name, password), TEST_API_HOST
                )
            }.then { NetworkResponse.Error(expected) }

        val actual = registerAccountRepository.registerWithEmail(email, code, name, password, TEST_API_HOST)

        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verify(registerApi).coroutine { register(RegisterApi.RegisterParam.PersonalAccount(email, code, name, password), TEST_API_HOST) }
            .wasInvoked(exactly = once)
    }

    private companion object {
        const val TEST_API_HOST = """test.wire.com"""
        const val NAME = "user_name"
        const val EMAIL = "user@domain.de"
        const val CODE = "123456"
        const val PASSWORD = "password"
        val SESSION: SessionDTO = SessionDTO("user_id", "tokenType", "access_token", "refresh_token")
        val TEST_USER: UserDTO = UserDTO(
            id = UserId("user_id", "domain.com"),
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
            managedBy = null,
            phone = null,
            ssoID = null
        )
    }
}
