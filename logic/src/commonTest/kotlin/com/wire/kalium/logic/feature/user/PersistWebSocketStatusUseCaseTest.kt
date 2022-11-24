package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AccountInfo
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistWebSocketStatusUseCaseTest {

    @Test
    fun givenATrueValue_persistWebSocketInvoked() = runTest {
        val expectedValue = Unit
        val accountInfo = AccountInfo.Valid(userId = UserId("test", "domain"))

        val (arrangement, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withSuccessfulResponse(accountInfo)
            .arrange()

        val actual = persistPersistentWebSocketConnectionStatusUseCase(true)
        assertEquals(expectedValue, actual)
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        val persistPersistentWebSocketConnectionStatusUseCaseImpl =
            PersistPersistentWebSocketConnectionStatusUseCaseImpl(sessionRepository)

        fun withSuccessfulResponse(accountInfo: AccountInfo): Arrangement {
            given(sessionRepository)
                .suspendFunction(sessionRepository::updatePersistentWebSocketStatus)
                .whenInvokedWith(any(), any()).thenReturn(Unit)

            given(sessionRepository).invocation { currentSession() }.then { Either.Right(accountInfo) }

            return this
        }

        fun arrange() = this to persistPersistentWebSocketConnectionStatusUseCaseImpl
    }

}
