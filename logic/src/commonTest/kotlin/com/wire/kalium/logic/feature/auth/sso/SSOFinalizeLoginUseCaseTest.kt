package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.TestServerConfig
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SSOFinalizeLoginUseCaseTest {

    @Mock
    val ssoLoginRepository = mock(classOf<SSOLoginRepository>())
    lateinit var ssoFinalizeLoginUseCase: SSOFinalizeLoginUseCase

    @BeforeTest
    fun setup() {
        ssoFinalizeLoginUseCase = SSOFinalizeLoginUseCaseImpl(ssoLoginRepository)
    }

    @Test
    fun givenApiReturnsInvalidCookie_whenFinalizing_thenReturnInvalidCookie() =
        runTest {
            given(ssoLoginRepository).coroutine { finalize(TEST_COOKIE, TestServerConfig.generic) }
                .then { Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.BadRequest.value)) }
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE, TestServerConfig.generic)
            assertEquals(result, SSOFinalizeLoginResult.Failure.InvalidCookie)
        }

    @Test
    fun givenApiReturnsGenericError_whenFinalizing_thenReturnGenericFailure() =
        runTest {
            val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
            given(ssoLoginRepository).coroutine { finalize(TEST_COOKIE, TestServerConfig.generic) }.then { Either.Left(expected) }
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE, TestServerConfig.generic)
            assertIs<SSOFinalizeLoginResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenFinalizing_thenReturnSuccess() =
        runTest {
            given(ssoLoginRepository).coroutine { finalize(TEST_COOKIE, TestServerConfig.generic) }.then { Either.Right(TEST_RESPONSE) }
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE, TestServerConfig.generic)
            assertEquals(result, SSOFinalizeLoginResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_COOKIE = "cookie"
        const val TEST_RESPONSE = "wire/response"
    }
}
