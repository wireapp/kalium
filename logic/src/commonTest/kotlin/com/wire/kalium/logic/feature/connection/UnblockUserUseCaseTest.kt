package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.failure.InvalidMappingFailure
import com.wire.kalium.logic.framework.TestConnection
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class UnblockUserUseCaseTest {

    @Test
    fun givenUnblockingFailed_thenBlockResultIsError() = runTest {
        val (_, blockUser) = Arrangement()
            .withBlockResult(Either.Left(InvalidMappingFailure))
            .arrange()

        val result = blockUser(TestUser.USER_ID)

        assertTrue(result is Either.Left)
    }

    @Test
    fun givenUnblockingSuccessful_thenBlockResultIsSuccess() = runTest {
        val (_, blockUser) = Arrangement()
            .withBlockResult(Either.Right(TestConnection.CONNECTION))
            .arrange()

        val result = blockUser(TestUser.USER_ID)

        assertTrue(result is Either.Right)
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
