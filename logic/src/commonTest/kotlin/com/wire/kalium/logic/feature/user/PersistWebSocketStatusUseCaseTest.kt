package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
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
        val (_, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        val actual = persistPersistentWebSocketConnectionStatusUseCase(true)
        assertEquals(expectedValue, actual)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val storageFailure = StorageFailure.DataNotFound
        val (arrangement, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withPersistWebSocketErrorResponse(storageFailure)
            .arrange()

        // When
        persistPersistentWebSocketConnectionStatusUseCase(true)

        verify(arrangement.sessionRepository)
            .suspendFunction(arrangement.sessionRepository::updatePersistentWebSocketStatus).with(any(), any())
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        val persistPersistentWebSocketConnectionStatusUseCaseImpl =
            PersistPersistentWebSocketConnectionStatusUseCaseImpl(UserId("test", "domain"), sessionRepository)

        fun withSuccessfulResponse(): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::updatePersistentWebSocketStatus)
                .whenInvokedWith(any(), any()).thenReturn(Either.Right(Unit))

            return this
        }

        fun withPersistWebSocketErrorResponse(storageFailure: StorageFailure): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::updatePersistentWebSocketStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to persistPersistentWebSocketConnectionStatusUseCaseImpl
    }

}
