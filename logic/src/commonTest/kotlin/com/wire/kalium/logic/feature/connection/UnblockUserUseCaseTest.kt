package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UnblockUserUseCaseTest {

    @Test
    fun givenUnblockingFailed_thenBlockResultIsError() = runTest {
        val (arrangement, unblockUser) = Arrangement()
            .withBlockResult(Either.Left(InvalidMappingFailure))
            .arrange()

        val result = unblockUser(TestUser.USER_ID)

        assertTrue(result is UnblockUserResult.Failure)
        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::updateConnectionStatus)
            .with(eq(TestUser.USER_ID), eq(ConnectionState.ACCEPTED))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUnblockingSuccessful_thenBlockResultIsSuccess() = runTest {
        val (arrangement, unblockUser) = Arrangement()
            .withBlockResult(Either.Right(TestConnection.CONNECTION))
            .arrange()

        val result = unblockUser(TestUser.USER_ID)

        assertTrue(result is UnblockUserResult.Success)
        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::updateConnectionStatus)
            .with(eq(TestUser.USER_ID), eq(ConnectionState.ACCEPTED))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val connectionRepository: ConnectionRepository = mock(classOf<ConnectionRepository>())

        val unblockUser = UnblockUserUseCaseImpl(connectionRepository)

        fun withBlockResult(result: Either<CoreFailure, Connection>) = apply {
            given(connectionRepository)
                .suspendFunction(connectionRepository::updateConnectionStatus)
                .whenInvokedWith(any(), any())
                .thenReturn(result)
        }

        fun arrange() = this to unblockUser
    }
}
