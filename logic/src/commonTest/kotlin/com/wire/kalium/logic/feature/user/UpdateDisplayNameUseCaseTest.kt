package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateDisplayNameUseCaseTest {

    @Test
    fun givenValidParams_whenUpdatingDisplayName_thenShouldReturnASuccessResult() = runTest {
        val (arrangement, updateDisplayName) = Arrangement()
            .withSuccessfulUploadResponse()
            .arrange()

        val result = updateDisplayName(NEW_DISPLAY_NAME)

        assertTrue(result is DisplayNameUpdateResult.Success)
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSelfDisplayName)
            .with(any())
            .wasInvoked(once)
    }

    @Test
    fun givenAnError_whenUpdatingDisplayName_thenShouldReturnAMappedCoreFailure() = runTest {
        val (arrangement, updateDisplayName) = Arrangement()
            .withErrorResponse()
            .arrange()

        val result = updateDisplayName(NEW_DISPLAY_NAME)

        assertTrue(result is DisplayNameUpdateResult.Failure)
        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateSelfDisplayName)
            .with(any())
            .wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        fun withSuccessfulUploadResponse() = apply {
            given(userRepository)
                .suspendFunction(userRepository::updateSelfDisplayName)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withErrorResponse() = apply {
            given(userRepository)
                .suspendFunction(userRepository::updateSelfDisplayName)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(CoreFailure.Unknown(Throwable("an error"))))
        }

        fun arrange() = this to UpdateDisplayNameUseCaseImpl(userRepository)
    }

    companion object {
        const val NEW_DISPLAY_NAME = "new display name"
    }
}
