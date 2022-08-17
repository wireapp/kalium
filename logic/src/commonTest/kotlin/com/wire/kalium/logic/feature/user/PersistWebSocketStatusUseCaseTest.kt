package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
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

        val (arrangement, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withSuccessfulResponse(expectedValue)
            .arrange()

        val actual = persistPersistentWebSocketConnectionStatusUseCase(true)
        assertEquals(expectedValue, actual)

        verify(arrangement.userConfigRepository).invocation { persistPersistentWebSocketConnectionStatus(true) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val dataNotFound = StorageFailure.DataNotFound
        val (arrangement, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withPersistWebSocketErrorResponse(dataNotFound)
            .arrange()

        // When
        persistPersistentWebSocketConnectionStatusUseCase(true)

        verify(arrangement.userConfigRepository)
            .function(arrangement.userConfigRepository::persistPersistentWebSocketConnectionStatus).with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userConfigRepository: UserConfigRepository = mock(classOf<UserConfigRepository>())

        val persistPersistentWebSocketConnectionStatusUseCaseImpl =
            PersistPersistentWebSocketConnectionStatusUseCaseImpl(userConfigRepository)

        fun withSuccessfulResponse(expectedValue: Unit): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::persistPersistentWebSocketConnectionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(expectedValue))

            return this
        }

        fun withPersistWebSocketErrorResponse(storageFailure: StorageFailure): Arrangement {
            given(userConfigRepository)
                .function(userConfigRepository::persistPersistentWebSocketConnectionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to persistPersistentWebSocketConnectionStatusUseCaseImpl
    }

}
