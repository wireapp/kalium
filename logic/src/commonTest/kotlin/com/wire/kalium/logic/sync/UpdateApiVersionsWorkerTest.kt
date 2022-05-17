package com.wire.kalium.logic.sync

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.feature.server.UpdateApiVersionsUseCase
import com.wire.kalium.logic.test_util.TestNetworkException
import io.mockative.ConfigurationApi
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ConfigurationApi::class, ExperimentalCoroutinesApi::class)
class UpdateApiVersionsWorkerTest {

    @Mock
    private val updateApiVersionsUseCase = mock(classOf<UpdateApiVersionsUseCase>())

    @Mock
    val apiVersionCheckManager = configure(mock(ApiVersionCheckManager::class)) { stubsUnitByDefault = true }

    private lateinit var updateApiVersionsWorker: UpdateApiVersionsWorker

    @BeforeTest
    fun setup() {
        updateApiVersionsWorker = UpdateApiVersionsWorker(apiVersionCheckManager, updateApiVersionsUseCase)
    }

    @Test
    fun givenUpdateReturnsSuccess_whenExecutingAWorker_thenReturnSuccess() = runTest {
        given(updateApiVersionsUseCase)
            .suspendFunction(updateApiVersionsUseCase::invoke)
            .whenInvoked()
            .thenReturn(Unit)

        val result = updateApiVersionsWorker.doWork()

        assertEquals(result, Result.Success)
        verify(apiVersionCheckManager)
            .function(apiVersionCheckManager::changeState)
            .with(eq(ApiVersionCheckState.Running))
            .wasInvoked(exactly = once)
        verify(apiVersionCheckManager)
            .function(apiVersionCheckManager::changeState)
            .with(eq(ApiVersionCheckState.Completed))
            .wasInvoked(exactly = once)

    }

    @Ignore
    @Test
    fun givenUpdateReturnsFailure_whenExecutingAWorker_thenReturnRetry() = runTest {
        val failure = NetworkFailure.ServerMiscommunication(TestNetworkException.generic)
        given(updateApiVersionsUseCase)
            .suspendFunction(updateApiVersionsUseCase::invoke)
            .whenInvoked()
            .thenReturn(Unit)

        val result = updateApiVersionsWorker.doWork()

        assertEquals(result, Result.Retry)
        verify(apiVersionCheckManager)
            .function(apiVersionCheckManager::changeState)
            .with(eq(ApiVersionCheckState.Running))
            .wasInvoked(exactly = once)
        verify(apiVersionCheckManager)
            .function(apiVersionCheckManager::changeState)
            .with(eq(ApiVersionCheckState.Failed(failure)))
            .wasInvoked(exactly = once)
    }
}
