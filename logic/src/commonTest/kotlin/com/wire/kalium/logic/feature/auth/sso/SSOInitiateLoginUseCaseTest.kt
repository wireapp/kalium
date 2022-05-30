package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
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
    private val ssoLoginRepository = mock(SSOLoginRepository::class)

    @Mock
    private val validateUUIDUseCase = mock(ValidateSSOCodeUseCase::class)

    @Mock
    private val serverConfigRepository: ServerConfigRepository = mock(ServerConfigRepository::class)

    private val serverConfig = newServerConfig(1)


    private lateinit var ssoInitiateLoginUseCase: SSOInitiateLoginUseCase

    @BeforeTest
    fun setup() {
        ssoInitiateLoginUseCase =
            SSOInitiateLoginUseCaseImpl(ssoLoginRepository, validateUUIDUseCase, serverConfig.links, serverConfigRepository)
    }

    @Test
    fun givenCodeFormatIsInvalid_whenInitiating_thenReturnInvalidCodeFormat() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.then { ValidateSSOCodeResult.Invalid }
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidCodeFormat)
            verify(validateUUIDUseCase).invocation { invoke(TEST_CODE) }.wasInvoked(exactly = once)
        }

    @Test
    fun givenApiReturnsInvalidCode_whenInitiating_thenReturnInvalidCode() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }
                .then { ValidateSSOCodeResult.Valid(TEST_UUID) }
            given(ssoLoginRepository).coroutine { initiate(TEST_UUID) }
                .then { Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.NotFound.value)) }
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidCode)
        }

    @Test
    fun givenApiReturnsInvalidRedirect_whenInitiating_thenReturnInvalidRedirect() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }
                .then { ValidateSSOCodeResult.Valid(TEST_UUID) }
            given(ssoLoginRepository).coroutine { initiate(TEST_UUID) }
                .then { Either.Left(serverMiscommunicationFailure(code = HttpStatusCode.BadRequest.value)) }
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Failure.InvalidRedirect)
        }

    @Test
    fun givenApiReturnsOtherError_whenInitiating_thenReturnGenericFailure() =
        runTest {
            val expected = serverMiscommunicationFailure(code = HttpStatusCode.Forbidden.value)
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }
                .then { ValidateSSOCodeResult.Valid(TEST_UUID) }
            given(ssoLoginRepository).coroutine { initiate(TEST_UUID) }.then { Either.Left(expected) }
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertIs<SSOInitiateLoginResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenInitiatingWithoutRedirect_thenReturnSuccess() =
        runTest {
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }
                .then { ValidateSSOCodeResult.Valid(TEST_UUID) }
            given(ssoLoginRepository).coroutine { initiate(TEST_UUID) }.then { Either.Right(TEST_RESPONSE) }
            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithoutRedirect(TEST_CODE))
            assertEquals(result, SSOInitiateLoginResult.Success(TEST_RESPONSE))
        }

    @Test
    fun givenApiReturnsSuccess_whenInitiatingWitRedirect_thenReturnSuccess() =
        runTest {
            val expectedRedirects = SSORedirects(serverConfig.id)
            given(validateUUIDUseCase).invocation { invoke(TEST_CODE) }
                .then { ValidateSSOCodeResult.Valid(TEST_UUID) }
            given(ssoLoginRepository).coroutine { initiate(TEST_UUID, expectedRedirects.success, expectedRedirects.error) }
                .then { Either.Right(TEST_RESPONSE) }
            given(serverConfigRepository)
                .coroutine { getOrFetchMetadata(serverConfig.links) }
                .then { Either.Right(serverConfig) }

            val result = ssoInitiateLoginUseCase(SSOInitiateLoginUseCase.Param.WithRedirect(TEST_CODE))

            assertEquals(result, SSOInitiateLoginResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_UUID = "fd994b20-b9af-11ec-ae36-00163e9b33ca"
        const val TEST_CODE = "wire-$TEST_UUID"
        const val TEST_RESPONSE = "wire/response"
    }
}
