package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.user.readReceipts.PersistReadReceiptsStatusConfigUseCaseImpl
import com.wire.kalium.logic.feature.user.readReceipts.ReadReceiptStatusConfigResult
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PersistReadReceiptsStatusConfigUseCaseTest {

    @Test
    fun givenATrueValue_shouldCallSetEnabledReceipts() = runTest {
        val (arrangement, persistReadReceiptsStatusConfig) = Arrangement()
            .withSuccessfulCall()
            .arrange()

        val actual = persistReadReceiptsStatusConfig(true)

        verify(arrangement.userPropertyRepository)
            .suspendFunction(arrangement.userPropertyRepository::setReadReceiptsEnabled)
            .wasInvoked(once)
        assertTrue(actual is ReadReceiptStatusConfigResult.Success)
    }

    @Test
    fun givenAFalseValue_shouldCallDeleteReadReceipts() = runTest {
        val (arrangement, persistReadReceiptsStatusConfig) = Arrangement()
            .withSuccessfulCallToDelete()
            .arrange()

        val actual = persistReadReceiptsStatusConfig(false)

        verify(arrangement.userPropertyRepository)
            .suspendFunction(arrangement.userPropertyRepository::deleteReadReceiptsProperty)
            .wasInvoked(once)

        assertTrue(actual is ReadReceiptStatusConfigResult.Success)
    }

    @Test
    fun givenAValue_shouldAndFailsShouldReturnACoreFailureResult() = runTest {
        val (arrangement, persistReadReceiptsStatusConfig) = Arrangement()
            .withFailureToCallRepo()
            .arrange()

        val actual = persistReadReceiptsStatusConfig(true)

        verify(arrangement.userPropertyRepository)
            .suspendFunction(arrangement.userPropertyRepository::setReadReceiptsEnabled)
            .wasInvoked(once)

        assertTrue(actual is ReadReceiptStatusConfigResult.Failure)
    }

    private class Arrangement {
        @Mock
        val userPropertyRepository = mock(classOf<UserPropertyRepository>())

        val persistReadReceiptsStatusConfig = PersistReadReceiptsStatusConfigUseCaseImpl(userPropertyRepository)

        fun withSuccessfulCall() = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::setReadReceiptsEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))

            return this
        }

        fun withSuccessfulCallToDelete() = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::deleteReadReceiptsProperty)
                .whenInvoked()
                .thenReturn(Either.Right(Unit))

            return this
        }

        fun withFailureToCallRepo() = apply {
            given(userPropertyRepository)
                .suspendFunction(userPropertyRepository::setReadReceiptsEnabled)
                .whenInvoked()
                .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

            return this
        }

        fun arrange() = this to persistReadReceiptsStatusConfig
    }

}
