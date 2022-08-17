package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
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

class PersistOtherUsersClientsUseCaseTest {

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
        getOtherUsersClientsUseCase.invoke(userId)

        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::fetchOtherUserClients).with(any())
            .wasInvoked(exactly = once)

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
        getOtherUsersClientsUseCase.invoke(userId)

        // Then
        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::fetchOtherUserClients).with(any())
            .wasInvoked(exactly = once)

    }

    private class Arrangement {

        @Mock
        val clientRemoteRepository = mock(classOf<ClientRemoteRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        val persistOtherUserClientsUseCase = PersistOtherUserClientsUseCaseImpl(clientRemoteRepository, clientRepository)

        fun withSuccessfulResponse(expectedResponse: List<OtherUserClients>): Arrangement {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::fetchOtherUserClients).whenInvokedWith(any())
                .thenReturn(Either.Right(expectedResponse))

            given(clientRepository)
                .suspendFunction(clientRepository::saveNewClients).whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withGetOtherUserClientsErrorResponse(exception: KaliumException): Arrangement {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::fetchOtherUserClients).whenInvokedWith(any())
                .thenReturn(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to persistOtherUserClientsUseCase
    }
}
