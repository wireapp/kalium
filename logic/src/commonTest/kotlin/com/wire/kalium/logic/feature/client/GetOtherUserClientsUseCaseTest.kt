package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class GetOtherUserClientsUseCaseTest {
    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val otherUserClients = listOf(
            OtherUserClient(DeviceType.Phone, "111"), OtherUserClient(DeviceType.Desktop, "2222")
        )
        val (arrangement, getOtherUsersClientsUseCase) = Arrangement()
            .withSuccessfulResponse(otherUserClients)
            .arrange()

        // When
        val result = getOtherUsersClientsUseCase.invoke(userId)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::getClientsByUserId).with(any())
            .wasInvoked(exactly = once)

        assertTrue(result is GetOtherUserClientsResult.Success)
    }

    @Test
    fun givenRepositoryCallFailWithInvaliUserId_thenNoUserFoundReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val (arrangement, getOtherUsersClientsUseCase) = Arrangement()
            .withGetOtherUserClientsErrorResponse()
            .arrange()

        // When
        val result = getOtherUsersClientsUseCase.invoke(userId)

        // Then
        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::getClientsByUserId).with(any())
            .wasInvoked(exactly = once)

        assertTrue(result is GetOtherUserClientsResult.Failure.UserNotFound)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        val getOtherUserClientsUseCaseImpl = GetOtherUserClientsUseCaseImpl(clientRepository)

        fun withSuccessfulResponse(expectedResponse: List<OtherUserClient>): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::getClientsByUserId).whenInvokedWith(any())
                .thenReturn(Either.Right(expectedResponse))

            given(clientRepository)
                .suspendFunction(clientRepository::storeUserClientListAndRemoveRedundantClients)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
            return this
        }

        fun withGetOtherUserClientsErrorResponse(): Arrangement {
            given(clientRepository)
                .suspendFunction(clientRepository::getClientsByUserId).whenInvokedWith(any())
                .thenReturn(Either.Left(StorageFailure.DataNotFound))
            return this
        }

        fun arrange() = this to getOtherUserClientsUseCaseImpl
    }
}
