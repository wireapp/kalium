package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.feature.auth.ValidateUUIDUseCase
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
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class SSOInitiateLoginUseCaseTest {

    @Mock
    val ssoLoginRepository = mock(classOf<SSOLoginRepository>())
    @Mock
    val validateUUIDUseCase = mock(classOf<ValidateUUIDUseCase>())
    lateinit var ssoInitiateLoginUseCase: SSOInitiateLoginUseCase

    @BeforeTest
    fun setup() {
        ssoInitiateLoginUseCase = SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateUUIDUseCase)
    }

    @Test
    fun givenCodeIsInvalid_whenInitiating_thenReturnInvalidCode() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { false }
            val result = ssoInitiateLoginUseCase(TEST_CODE, TestServerConfig.generic)
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidCode)
            verify(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenApiReturnsInvalidCode_whenInitiating_thenReturnInvalidCode() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { true }
            given(ssoLoginRepository).coroutine { initiate(TEST_CODE, TestServerConfig.generic) }
                .then { Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.NotFound.value)) }
            val result = ssoInitiateLoginUseCase(TEST_CODE, TestServerConfig.generic)
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidCode)
        }

    @Test
    fun givenApiReturnsInvalidRedirect_whenInitiating_thenReturnInvalidRedirect() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { true }
            given(ssoLoginRepository).coroutine { initiate(TEST_CODE, TestServerConfig.generic) }
                .then { Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.BadRequest.value)) }
            val result = ssoInitiateLoginUseCase(TEST_CODE, TestServerConfig.generic)
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidRedirect)
        }

    @Test
    fun givenApiReturnsOtherError_whenInitiating_thenReturnGenericFailure() =
        runTest {
            val expected = serverMiscommunicationFailure(code = HttpStatusCode.Forbidden.value)
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { true }
            given(ssoLoginRepository).coroutine { initiate(TEST_CODE, TestServerConfig.generic) }.then { Either.Left(expected) }
            val result = ssoInitiateLoginUseCase(TEST_CODE, TestServerConfig.generic)
            assertIs<SSOInitiateLoginResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenInitiatingWithoutRedirect_thenReturnSuccess() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { true }
            given(ssoLoginRepository).coroutine { initiate(TEST_CODE, TestServerConfig.generic) }.then { Either.Right(TEST_RESPONSE) }
            val result = ssoInitiateLoginUseCase(TEST_CODE, TestServerConfig.generic)
            assertEquals(result, SSOInitiateLoginResult.Success(TEST_RESPONSE))
        }

    @Test
    fun givenApiReturnsSuccess_whenInitiatingWitRedirect_thenReturnSuccess() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { true }
            given(ssoLoginRepository).coroutine { initiate(TEST_CODE, TEST_SUCCESS, TEST_ERROR, TestServerConfig.generic) }
                .then { Either.Right(TEST_RESPONSE) }
            val result = ssoInitiateLoginUseCase(TEST_CODE, TestServerConfig.generic, Redirects(TEST_SUCCESS, TEST_ERROR))
            assertEquals(result, SSOInitiateLoginResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_CODE = "fd994b20-b9af-11ec-ae36-00163e9b33ca"
        const val TEST_SUCCESS = "wire/success"
        const val TEST_ERROR = "wire/error"
        const val TEST_RESPONSE = "wire/response"
    }
}
