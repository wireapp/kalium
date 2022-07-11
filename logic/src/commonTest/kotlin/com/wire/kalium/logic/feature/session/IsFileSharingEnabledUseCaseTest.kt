package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.IsFileSharingEnabledUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsFileSharingEnabledUseCaseTest {

    @Test
    fun givenATrueValue_thenISFileSharingIsEnabled() = runTest {
        val expectedValue = FileSharingStatus(true, false)

        val (arrangement, isFileSharingEnabledUseCase) = Arrangement()
            .withSuccessfulResponse(expectedValue)
            .arrange()

        val actual = isFileSharingEnabledUseCase.invoke()
        assertEquals(expectedValue, actual)

        verify(arrangement.userConfigRepository).invocation { isFileSharingEnabled() }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val dataNotFound = StorageFailure.DataNotFound
        val (arrangement, isFileSharingEnabledUseCase) = Arrangement()
            .withIsFileSharingEnabledErrorResponse(dataNotFound)
            .arrange()

        // When
        isFileSharingEnabledUseCase.invoke()

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isFileSharingEnabled)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(classOf<UserConfigRepository>())

        val isFileSharingEnabledUseCase = IsFileSharingEnabledUseCaseImpl(userConfigRepository)

        fun withSuccessfulResponse(expectedValue: FileSharingStatus): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::isFileSharingEnabled)
                .whenInvoked()
                .thenReturn(Either.Right(expectedValue))

            return this
        }

        fun withIsFileSharingEnabledErrorResponse(storageFailure: StorageFailure): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::isFileSharingEnabled)
                .whenInvoked()
                .thenReturn(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to isFileSharingEnabledUseCase
    }
}
