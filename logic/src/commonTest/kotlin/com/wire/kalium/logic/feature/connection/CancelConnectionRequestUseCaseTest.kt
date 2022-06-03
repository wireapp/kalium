package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CancelConnectionRequestUseCaseTest {

    @Mock
    private val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    lateinit var cancelConnectionRequestUseCase: CancelConnectionRequestUseCase

    @BeforeTest
    fun setUp() {
        cancelConnectionRequestUseCase = CancelConnectionRequestUseCaseImpl(connectionRepository)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingCancelConnectionRequestAndOk_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.CANCELLED))
            .thenReturn(Either.Right(Unit))

        // when
        val resultOk = cancelConnectionRequestUseCase(userId)

        // then
        assertEquals(CancelConnectionRequestUseCaseResult.Success, resultOk)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.CANCELLED))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingCancelConnectionRequestAndFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.CANCELLED))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

        // when
        val resultFailure = cancelConnectionRequestUseCase(userId)

        // then
        assertEquals(CancelConnectionRequestUseCaseResult.Failure::class, resultFailure::class)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.CANCELLED))
            .wasInvoked(once)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}
