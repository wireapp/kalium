package com.wire.kalium.logic.data.register

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkExiption
import com.wire.kalium.network.api.user.register.RegisterApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        val expected = TestNetworkExiption.generic
        val email = "user@domain.de"
        given(registerApi)
            .coroutine { requestActivationCode(RegisterApi.RequestActivationCodeParam.Email(email), TEST_API_HOST) }
            .then { NetworkResponse.Error<KaliumException>(expected) as NetworkResponse<Unit> }

        val actual = registerAccountRepository.requestEmailActivationCode(email, TEST_API_HOST)

        assertIs<Either.Left<NetworkFailure>>(actual)
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
        val expected = TestNetworkExiption.generic
        val email = "user@domain.de"
        val code = "123456"
        given(registerApi)
            .coroutine { activate(RegisterApi.ActivationParam.Email(email, code), TEST_API_HOST) }
            .then { NetworkResponse.Error<KaliumException>(expected) as NetworkResponse<Unit> }

        val actual = registerAccountRepository.verifyActivationCode(email, code, TEST_API_HOST)

        assertIs<Either.Left<NetworkFailure>>(actual)
        assertEquals(expected, actual.value.kaliumException)

        verify(registerApi)
            .coroutine { activate(RegisterApi.ActivationParam.Email(email, code), TEST_API_HOST) }
            .wasInvoked(exactly = once)
    }


    private companion object {
        const val TEST_API_HOST = """test.wire.com"""
    }
}
