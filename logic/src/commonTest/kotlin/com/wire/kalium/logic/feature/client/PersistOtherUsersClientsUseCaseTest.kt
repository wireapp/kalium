package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.fun2
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class PersistOtherUsersClientsUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val otherUserClients = listOf(
            OtherUserClient(DeviceType.Phone, "111"), OtherUserClient(DeviceType.Desktop, "2222")
        )
        val (arrangement, getOtherUsersClientsUseCase) = Arrangement()
            .withSuccessfulResponse(userId, otherUserClients)
            .arrange()

        // When
        getOtherUsersClientsUseCase(userId)

        verify(arrangement.clientRemoteRepository)
            .suspendFunction(arrangement.clientRemoteRepository::fetchOtherUserClients).with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::storeUserClientListAndRemoveRedundantClients, fun2())
            .with(any(), any())
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

        val persistOtherUserClientsUseCase =
            PersistOtherUserClientsUseCaseImpl(clientRemoteRepository, clientRepository)

        suspend fun withSuccessfulResponse(userId: UserId, expectedResponse: List<OtherUserClient>): Arrangement {
            given(clientRemoteRepository)
                .suspendFunction(clientRemoteRepository::fetchOtherUserClients).whenInvokedWith(any())
                .thenReturn(Either.Right(listOf(userId to expectedResponse)))

            given(clientRepository)
                .coroutine { clientRepository.storeUserClientListAndRemoveRedundantClients(userId, expectedResponse) }
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
