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
class SSOMetaDataUseCaseTest {

    @Mock
    val ssoLoginRepository = mock(classOf<SSOLoginRepository>())
    lateinit var ssoMetaDataUseCase: SSOMetaDataUseCase

    @BeforeTest
    fun setup() {
        ssoMetaDataUseCase = SSOMetaDataUseCaseImpl(ssoLoginRepository)
    }

    @Test
    fun givenApiReturnsGenericError_whenRequestingMetaData_thenReturnGenericFailure() =
        runTest {
            val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
            given(ssoLoginRepository).coroutine { metaData(TestServerConfig.generic) }.then { Either.Left(expected) }
            val result = ssoMetaDataUseCase(TestServerConfig.generic)
            assertIs<SSOMetaDataResult.Failure.Generic>(result)
            assertEquals(expected, result.genericFailure)
        }

    @Test
    fun givenApiReturnsSuccess_whenRequestingMetaData_thenReturnSuccess() =
        runTest {
            given(ssoLoginRepository).coroutine { metaData(TestServerConfig.generic) }.then { Either.Right(TEST_RESPONSE) }
            val result = ssoMetaDataUseCase(TestServerConfig.generic)
            assertEquals(result, SSOMetaDataResult.Success(TEST_RESPONSE))
        }

    private companion object {
        const val TEST_RESPONSE = "wire/response"
    }
}
