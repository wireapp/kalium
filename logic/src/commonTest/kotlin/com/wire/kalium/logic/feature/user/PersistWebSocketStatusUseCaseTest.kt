package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.GlobalConfigRepository
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
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

        verify(arrangement.globalConfigRepository).invocation { persistPersistentWebSocketConnectionStatus(true) }
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

        verify(arrangement.globalConfigRepository)
            .function(arrangement.globalConfigRepository::persistPersistentWebSocketConnectionStatus).with(any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val globalConfigRepository: GlobalConfigRepository = mock(GlobalConfigRepository::class)

        val persistPersistentWebSocketConnectionStatusUseCaseImpl =
            PersistPersistentWebSocketConnectionStatusUseCaseImpl(globalConfigRepository)

        fun withSuccessfulResponse(expectedValue: Unit): Arrangement {
            given(globalConfigRepository)
                .function(globalConfigRepository::persistPersistentWebSocketConnectionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(expectedValue))

            return this
        }

        fun withPersistWebSocketErrorResponse(storageFailure: StorageFailure): Arrangement {
            given(globalConfigRepository)
                .function(globalConfigRepository::persistPersistentWebSocketConnectionStatus)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to persistPersistentWebSocketConnectionStatusUseCaseImpl
    }

}
