package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
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

class SendConnectionRequestUseCaseTest {

    @Mock
    private val connectionRepository: ConnectionRepository = mock(ConnectionRepository::class)

    lateinit var sendConnectionRequestUseCase: SendConnectionRequestUseCase

    @BeforeTest
    fun setUp() {
        sendConnectionRequestUseCase = SendConnectionRequestUseCaseImpl(connectionRepository)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingASendAConnectionRequest_thenShouldReturnsASuccessResult() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::sendUserConnection)
            .whenInvokedWith(eq(userId))
            .thenReturn(Either.Right(Unit))

        // when
        val resultOk = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Success, resultOk)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::sendUserConnection)
            .with(eq(userId))
            .wasInvoked(once)
    }

    @Test
    fun givenAConnectionRequest_whenInvokingASendAConnectionRequestFails_thenShouldReturnsASuccessFailure() = runTest {
        // given
        given(connectionRepository)
            .suspendFunction(connectionRepository::sendUserConnection)
            .whenInvokedWith(eq(userId))
            .thenReturn(Either.Left(CoreFailure.Unknown(RuntimeException("Some error"))))

        // when
        val resultFailure = sendConnectionRequestUseCase(userId)

        // then
        assertEquals(SendConnectionRequestResult.Failure::class, resultFailure::class)
        verify(connectionRepository)
            .suspendFunction(connectionRepository::sendUserConnection)
            .with(eq(userId))
            .wasInvoked(once)
    }

    private companion object {
        val userId = UserId("some_user", "some_domain")
    }
}
