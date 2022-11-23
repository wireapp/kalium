package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
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
            .withSuccessfulResponse()
            .arrange()

        val actual = persistPersistentWebSocketConnectionStatusUseCase(true)
        assertEquals(expectedValue, actual)

    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val (arrangement, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withPersistWebSocketErrorResponse()
            .arrange()

        // When
        persistPersistentWebSocketConnectionStatusUseCase(true)

        verify(arrangement.sessionRepository)
            .function(arrangement.sessionRepository::updatePersistentWebSocketStatus).with()
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        val persistPersistentWebSocketConnectionStatusUseCaseImpl =
            PersistPersistentWebSocketConnectionStatusUseCaseImpl(sessionRepository)

        fun withSuccessfulResponse(): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::updatePersistentWebSocketStatus)
                .whenInvokedWith(any(), any()).thenReturn(Unit)

            return this
        }

        fun withPersistWebSocketErrorResponse(): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::updatePersistentWebSocketStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
            return this
        }

        fun arrange() = this to persistPersistentWebSocketConnectionStatusUseCaseImpl
    }

}
