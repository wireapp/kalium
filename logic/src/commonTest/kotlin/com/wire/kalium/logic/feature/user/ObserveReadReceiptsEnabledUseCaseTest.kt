package com.wire.kalium.logic.feature.user

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.user.readReceipts.ObserveReadReceiptsEnabledUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class ObserveReadReceiptsEnabledUseCaseTest {

    @Test
    fun givenAReadReceiptsState_whenInvokingObserveReadReceiptsEnabled_thenShouldReturnsSuccessResult() = runTest {
        val (arrangement, observeReadReceiptsEnabled) = Arrangement()
            .withSuccessfulState()
            .arrange()

        val result = observeReadReceiptsEnabled()

        result.test {
            val item = awaitItem()
            assertTrue(item)

            verify(arrangement.userPropertyRepository)
                .function(arrangement.userPropertyRepository::observeReadReceiptsStatus)
                .with()
                .wasInvoked(once)

            awaitComplete()
        }
    }

    @Test
    fun givenAReadReceiptsState_whenFailureInvokingObserveReadReceiptsEnabled_thenShouldReturnsTrueAndSuccessAsFallbackResult() = runTest {
        val (arrangement, observeReadReceiptsEnabled) = Arrangement()
            .withFailureState()
            .arrange()

        val result = observeReadReceiptsEnabled()

        result.test {
            val item = awaitItem()
            assertTrue(item)

            verify(arrangement.userPropertyRepository)
                .function(arrangement.userPropertyRepository::observeReadReceiptsStatus)
                .with()
                .wasInvoked(once)

            awaitComplete()
        }
    }

    private class Arrangement {
        @Mock
        val userPropertyRepository = mock(classOf<UserPropertyRepository>())

        val observeReadReceiptsEnabled = ObserveReadReceiptsEnabledUseCaseImpl(userPropertyRepository)

        fun withSuccessfulState() = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::observeReadReceiptsStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Right(true)))

            return this
        }

        fun withFailureState() = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::observeReadReceiptsStatus)
                .whenInvoked()
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))

            return this
        }

        fun arrange() = this to observeReadReceiptsEnabled
    }
}
