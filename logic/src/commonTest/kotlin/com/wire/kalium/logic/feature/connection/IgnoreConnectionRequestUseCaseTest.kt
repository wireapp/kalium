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

class IgnoreConnectionRequestUseCaseTest {

    @Mock
    private val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    lateinit var ignoreConnectionRequestUseCase: IgnoreConnectionRequestUseCase

    @BeforeTest
    fun setUp() {
        ignoreConnectionRequestUseCase = IgnoreConnectionRequestUseCaseImpl(connectionRepository)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingIgnoreConnectionRequestAndOk_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.IGNORED))
            .thenReturn(Either.Right(Unit))

        // when
        val resultOk = ignoreConnectionRequestUseCase(userId)

        // then
        assertEquals(IgnoreConnectionRequestUseCaseResult.Success, resultOk)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.IGNORED))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingIgnoreConnectionRequestAndFails_thenShouldReturnsAFailureResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .whenInvokedWith(eq(userId), eq(ConnectionState.IGNORED))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

        // when
        val resultFailure = ignoreConnectionRequestUseCase(userId)

        // then
        assertEquals(IgnoreConnectionRequestUseCaseResult.Failure::class, resultFailure::class)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::updateConnectionStatus)
            .with(eq(userId), eq(ConnectionState.IGNORED))
            .wasInvoked(once)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}
