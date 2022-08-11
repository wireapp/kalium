package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistWebSocketStatusUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistWebSocketStatusUseCaseTest {

    @Test
    fun givenATrueValue_persistWebSocketInvoked() = runTest {
        val expectedValue = Unit

        val (arrangement, persistWebSocketUseCase) = Arrangement()
            .withSuccessfulResponse(expectedValue)
            .arrange()

        val actual = persistWebSocketUseCase(true)
        assertEquals(expectedValue, actual)

        verify(arrangement.userConfigRepository).invocation { persistWebSocketStatus(true) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val dataNotFound = StorageFailure.DataNotFound
        val (arrangement, persistWebSocketUseCase) = Arrangement()
            .withPersistWebSocketErrorResponse(dataNotFound)
            .arrange()

        // When
        persistWebSocketUseCase(true)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::persistWebSocketStatus).with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(classOf<UserConfigRepository>())

        val persistWebSocketStatusUseCaseImpl = PersistWebSocketStatusUseCaseImpl(userConfigRepository)

        fun withSuccessfulResponse(expectedValue: Unit): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::persistWebSocketStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(expectedValue))

            return this
        }

        fun withPersistWebSocketErrorResponse(storageFailure: StorageFailure): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::persistWebSocketStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to persistWebSocketStatusUseCaseImpl
    }

}
