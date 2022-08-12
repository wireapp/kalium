package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.client.OtherUserClients
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class GetOtherUsersClientsUseCase {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val otherUserClients = listOf(
            OtherUserClients(DeviceType.Phone, "111"), OtherUserClients(DeviceType.Desktop, "2222")
        )
        val (arrangement, getOtherUsersClientsUseCase) = Arrangement()
            .withSuccessfulResponse(otherUserClients)
            .arrange()

        // When
        val result = getOtherUsersClientsUseCase.invoke(userId)

        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::otherUserClients).with(any())
            .wasInvoked(exactly = once)

        assertTrue(result is GetOtherUserClientsResult.Success)
    }

    @Test
    fun givenRepositoryCallFailWithInvaliUserId_thenNoUserFoundReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val noUserFoundException = TestNetworkException.noTeam
        val (arrangement, getOtherUsersClientsUseCase) = Arrangement()
            .withGetOtherUserClientsErrorResponse(noUserFoundException)
            .arrange()

        // When
        val result = getOtherUsersClientsUseCase.invoke(userId)

        // Then
        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::otherUserClients).with(any())
            .wasInvoked(exactly = once)

        assertTrue(result is GetOtherUserClientsResult.Failure.UserNotFound)
    }

    private class Arrangement {

        @Mock
        val clientRemoteRepository = mock(classOf<ClientRemoteRepository>())

        val getOtherUserClientsUseCase = GetOtherUserClientsUseCaseImpl(clientRemoteRepository)

        fun withSuccessfulResponse(expectedResponse: List<OtherUserClients>): Arrangement {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::otherUserClients).whenInvokedWith(any())
                .thenReturn(Either.Right(expectedResponse))
            return this
        }

        fun withGetOtherUserClientsErrorResponse(exception: KaliumException): Arrangement {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::otherUserClients).whenInvokedWith(any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to getOtherUserClientsUseCase
    }
}
