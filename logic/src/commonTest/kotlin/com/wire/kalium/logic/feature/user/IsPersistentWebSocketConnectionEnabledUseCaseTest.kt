package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.webSocketStatus.ObservePersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class IsPersistentWebSocketConnectionEnabledUseCaseTest {

    @Test
    fun givenATrueValue_thenIsWebSocketEnabled() = runTest {
        val expectedValue = true

        val (arrangement, isPersistentWebSocketConnectionEnabledUseCase) = Arrangement()
            .withSuccessfulResponse(expectedValue)
            .arrange()

        isPersistentWebSocketConnectionEnabledUseCase()

        verify(arrangement.userConfigRepository).invocation { isPersistentWebSocketConnectionEnabledFlow() }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val dataNotFound = StorageFailure.DataNotFound
        val (arrangement, isPersistentWebSocketConnectionEnabledUseCase) = Arrangement()
            .withIsWebSocketEnabledErrorResponse(dataNotFound)
            .arrange()

        // When
        isPersistentWebSocketConnectionEnabledUseCase()

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::isPersistentWebSocketConnectionEnabledFlow)
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(classOf<UserConfigRepository>())

        val isPersistentWebSocketConnectionEnabledUseCase = ObservePersistentWebSocketConnectionStatusUseCaseImpl(userConfigRepository)

        fun withSuccessfulResponse(expectedValue: Boolean): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::isPersistentWebSocketConnectionEnabledFlow)
                .whenInvoked()
                .thenReturn(flow { Either.Right(expectedValue) })

            return this
        }

        fun withIsWebSocketEnabledErrorResponse(storageFailure: StorageFailure): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::isPersistentWebSocketConnectionEnabledFlow)
                .whenInvoked()
                .thenReturn(flow { Either.Left(storageFailure) })
            return this
        }

        fun arrange() = this to isPersistentWebSocketConnectionEnabledUseCase
    }
}
