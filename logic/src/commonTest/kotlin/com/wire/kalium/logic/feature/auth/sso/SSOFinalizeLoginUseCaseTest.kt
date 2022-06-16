package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
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
            given(ssoLoginRepository).coroutine { finalize(TEST_COOKIE) }
                .then { Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.BadRequest.value)) }
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE)
            assertEquals(result, SSOFinalizeLoginResult.Failure.InvalidCookie)
        }

    @Test
    fun givenApiReturnsGenericError_whenFinalizing_thenReturnGenericFailure() =
        runTest {
            val expected = serverMiscommunicationFailure(code = HttpStatusCode.Forbidden.value)
            given(ssoLoginRepository).coroutine { finalize(TEST_COOKIE) }.then { Either.Left(expected) }
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE)
            assertIs<SSOFinalizeLoginResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenFinalizing_thenReturnSuccess() =
        runTest {
            given(ssoLoginRepository).coroutine { finalize(TEST_COOKIE) }.then { Either.Right(TEST_RESPONSE) }
            val result = ssoFinalizeLoginUseCase(TEST_COOKIE)
            assertEquals(result, SSOFinalizeLoginResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_COOKIE = "cookie"
        const val TEST_RESPONSE = "wire/response"
    }
}
