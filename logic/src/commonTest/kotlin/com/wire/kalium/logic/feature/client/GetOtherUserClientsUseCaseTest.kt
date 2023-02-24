/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

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
            OtherUserClient(DeviceType.Phone, "111", true, isVerified = false),
            OtherUserClient(DeviceType.Desktop, "2222", true, isVerified = true)
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
                .whenInvokedWith(any())
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
