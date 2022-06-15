package com.wire.kalium.logic.data.auth.login

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.stubs.newServerConfig
import com.wire.kalium.network.api.user.login.SSOLoginApi
import com.wire.kalium.network.exceptions.KaliumException
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
class SSOLoginRepositoryTest {

    @Mock
    val ssoLoginApi = mock(classOf<SSOLoginApi>())
    private lateinit var ssoLoginRepository: SSOLoginRepository

    @BeforeTest
    fun setup() {
        ssoLoginRepository = SSOLoginRepositoryImpl(ssoLoginApi)
    }

    @Test
    fun givenApiRequestSuccess_whenInitiatingWithoutRedirects_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            { initiate(SSOLoginApi.InitiateParam.WithoutRedirect(TEST_CODE)) },
            "wire/response",
            { ssoLoginRepository.initiate(TEST_CODE) }
        )

    @Test
    fun givenApiRequestSuccess_whenInitiatingWithRedirects_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            { initiate(SSOLoginApi.InitiateParam.WithRedirect(TEST_SUCCESS, TEST_ERROR, TEST_CODE)) },
            "wire/response",
            { ssoLoginRepository.initiate(TEST_CODE, TEST_SUCCESS, TEST_ERROR) }
        )

    @Test
    fun givenApiRequestFail_whenInitiating_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            { initiate(SSOLoginApi.InitiateParam.WithoutRedirect(TEST_CODE)) },
            expected = TestNetworkException.generic,
            { ssoLoginRepository.initiate(TEST_CODE) }
        )

    @Test
    fun givenApiRequestSuccess_whenFinalizing_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            apiCoroutineBlock = { finalize(TEST_COOKIE) },
            expected = "wire/response",
            repositoryCoroutineBlock = { finalize(TEST_COOKIE) }
        )

    @Test
    fun givenApiRequestFail_whenFinalizing_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            apiCoroutineBlock = { finalize(TEST_COOKIE) },
            expected = TestNetworkException.generic,
            repositoryCoroutineBlock = { finalize(TEST_COOKIE) }
        )

    @Test
    fun givenApiRequestSuccess_whenRequestingMetaData_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            apiCoroutineBlock = { metaData() },
            expected = "wire/response",
            repositoryCoroutineBlock = { metaData() }
        )

    @Test
    fun givenApiRequestFail_whenRequestingMetaData_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            apiCoroutineBlock = { metaData() },
            expected = TestNetworkException.generic,
            repositoryCoroutineBlock = { metaData() }
        )

    @Test
    fun givenApiRequestSuccess_whenRequestingSettings_thenSuccessIsPropagated() =
        givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
            apiCoroutineBlock = { settings() },
            expected = true,
            repositoryCoroutineBlock = { settings() }
        )

    @Test
    fun givenApiRequestFail_whenRequestingSettings_thenNetworkFailureIsPropagated() =
        givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
            apiCoroutineBlock = { settings() },
            expected = TestNetworkException.generic,
            repositoryCoroutineBlock = { settings() }
        )

    private fun <T : Any> givenApiRequestSuccess_whenMakingRequest_thenSuccessIsPropagated(
        apiCoroutineBlock: suspend SSOLoginApi.() -> NetworkResponse<T>,
        expected: T,
        repositoryCoroutineBlock: suspend SSOLoginRepository.() -> Either<NetworkFailure, T>
    ) = runTest {
        given(ssoLoginApi).coroutine { apiCoroutineBlock(this) }.then { NetworkResponse.Success(expected, mapOf(), 200) }
        val actual = repositoryCoroutineBlock(ssoLoginRepository)
        assertIs<Either.Right<T>>(actual)
        assertEquals(expected, actual.value)
        verify(ssoLoginApi).coroutine { apiCoroutineBlock(this) }.wasInvoked(exactly = once)
    }

    private fun <T : Any> givenApiRequestFail_whenMakingRequest_thenNetworkFailureIsPropagated(
        apiCoroutineBlock: suspend SSOLoginApi.() -> NetworkResponse<T>,
        expected: KaliumException,
        repositoryCoroutineBlock: suspend SSOLoginRepository.() -> Either<NetworkFailure, T>
    ) = runTest {
        given(ssoLoginApi).coroutine { apiCoroutineBlock(this) }.then { NetworkResponse.Error(expected) }
        val actual = repositoryCoroutineBlock(ssoLoginRepository)
        assertIs<Either.Left<NetworkFailure.ServerMiscommunication>>(actual)
        assertEquals(expected, actual.value.kaliumException)
        verify(ssoLoginApi).coroutine { apiCoroutineBlock(this) }.wasInvoked(exactly = once)
    }

    private companion object {
        const val TEST_CODE = "code"
        const val TEST_COOKIE = "cookie"
        const val TEST_SUCCESS = "wire/success"
        const val TEST_ERROR = "wire/error"
        val TEST_SERVER_CONFIG = newServerConfig(1)
    }
}
