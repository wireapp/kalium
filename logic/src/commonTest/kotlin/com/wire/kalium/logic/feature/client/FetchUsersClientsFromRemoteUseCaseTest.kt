/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.api.authenticated.client.DeviceTypeDTO
import com.wire.kalium.network.api.authenticated.client.SimpleClientResponse
import com.wire.kalium.network.exceptions.KaliumException
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import com.wire.kalium.network.api.model.UserId as UserIdDTO

@ExperimentalCoroutinesApi
class FetchUsersClientsFromRemoteUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")

        val userIdDTO = UserIdDTO(userId.value, userId.domain)
        val otherUserClients = listOf(
            SimpleClientResponse("111", DeviceTypeDTO.Phone), SimpleClientResponse("2222", DeviceTypeDTO.Desktop)
        )
        val (arrangement, useCase) = Arrangement()
            .withSuccessfulResponse(userIdDTO, otherUserClients)
            .arrange()

        // When
        useCase(listOf(userId))

        coVerify {
            arrangement.clientRemoteRepository.fetchOtherUserClients(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.clientRepository.storeUserClientListAndRemoveRedundantClients(any())
        }.wasInvoked(exactly = once)

    }

    @Test
    fun givenRepositoryCallFailWithInvalidUserId_thenNoUserFoundReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val noUserFoundException = TestNetworkException.noTeam
        val (arrangement, useCase) = Arrangement()
            .withGetOtherUserClientsErrorResponse(noUserFoundException)
            .arrange()

        // When
        useCase.invoke(listOf(userId))

        // Then
        coVerify {
            arrangement.clientRemoteRepository.fetchOtherUserClients(any())
        }.wasInvoked(exactly = once)

    }

    private class Arrangement {

        val clientRemoteRepository = mock(ClientRemoteRepository::class)
        val clientRepository = mock(ClientRepository::class)

        val clientMapper = MapperProvider.clientMapper()

        val fetchUsersClientsFromRemoteUseCase =
            FetchUsersClientsFromRemoteUseCaseImpl(clientRemoteRepository, clientRepository)

        suspend fun withSuccessfulResponse(userIdDTO: UserIdDTO, expectedResponse: List<SimpleClientResponse>): Arrangement {
            coEvery {
                clientRemoteRepository.fetchOtherUserClients(any())
            }.returns(Either.Right(mapOf(userIdDTO to expectedResponse)))

            coEvery {
                clientRepository.storeUserClientListAndRemoveRedundantClients(
                    clientMapper.toInsertClientParam(
                        userIdDTO = userIdDTO,
                        simpleClientResponse = expectedResponse
                    )
                )
            }.returns(Either.Right(Unit))

            return this
        }

        suspend fun withGetOtherUserClientsErrorResponse(exception: KaliumException): Arrangement {
            coEvery {
                clientRemoteRepository.fetchOtherUserClients(any())
            }.returns(Either.Left(NetworkFailure.ServerMiscommunication(exception)))
            return this
        }

        fun arrange() = this to fetchUsersClientsFromRemoteUseCase
    }
}
