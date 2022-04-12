package com.wire.kalium.logic.feature.auth.sso

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.auth.login.SSOLoginRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.TestServerConfig
import com.wire.kalium.logic.test_util.serverMiscommunicationFailure
import com.wire.kalium.network.api.ErrorResponse
import com.wire.kalium.network.api.user.login.SSOSettingsResponse
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
class SSOSettingsUseCaseTest {

    @Mock
    val ssoLoginRepository = mock(classOf<SSOLoginRepository>())
    lateinit var ssoSettingsUseCase: SSOSettingsUseCase

    @BeforeTest
    fun setup() {
        ssoSettingsUseCase = SSOSettingsUseCaseImpl(ssoLoginRepository)
    }

    @Test
    fun givenApiReturnsGenericError_whenRequestingMetaData_thenReturnGenericFailure() =
        runTest {
            val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
            given(ssoLoginRepository).coroutine { settings(TestServerConfig.generic) }.then { Either.Left(expected) }
            val result = ssoSettingsUseCase(TestServerConfig.generic)
            assertIs<SSOSettingsResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenRequestingMetaData_thenReturnSuccess() =
        runTest {
            given(ssoLoginRepository).coroutine { settings(TestServerConfig.generic) }.then { Either.Right(TEST_RESPONSE) }
            val result = ssoSettingsUseCase(TestServerConfig.generic)
            assertEquals(result, SSOSettingsResult.Success(TEST_RESPONSE))
        }

    private companion object {
        val TEST_RESPONSE = SSOSettingsResponse("default_code")
    }
}
