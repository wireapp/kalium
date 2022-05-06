package com.wire.kalium.logic.sync

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.configuration.ApiVersionRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.any
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class ApiVersionCheckWorkerTest {

    @Mock
    val apiVersionCheckManager = configure(mock(ApiVersionCheckManager::class)) { stubsUnitByDefault = true }

    @Mock
    val apiVersionRepository = configure(mock(ApiVersionRepository::class)) { stubsUnitByDefault = true }

    private lateinit var apiVersionCheckWorker: ApiVersionCheckWorker

    @BeforeTest
    fun setup() {
        apiVersionCheckWorker = ApiVersionCheckWorker(apiVersionCheckManager, apiVersionRepository)

        given(apiVersionCheckManager)
            .function(apiVersionCheckManager::changeState)
            .whenInvokedWith(any())
            .thenReturn(Unit)
    }

    // TODO implement tests for success responses

    @Test
    fun givenApiVersionReturnsFailure_whenExecutingAWorker_thenSetStateToFailureAndReturnRetry() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)

        given(apiVersionRepository)
            .suspendFunction(apiVersionRepository::fetchApiVersion)
            .whenInvoked()
            .thenReturn(Either.Left(expected))

        val result = apiVersionCheckWorker.doWork()

        assertEquals(result, Result.Retry)
        verify(apiVersionCheckManager)
            .function(apiVersionCheckManager::changeState)
            .with(eq(ApiVersionCheckState.Failed(expected)))
            .wasInvoked(exactly = once)
    }
}
