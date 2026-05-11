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
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import com.wire.kalium.network.api.model.UserId as UserIdDTO

@ExperimentalCoroutinesApi
class FetchUsersClientsFromRemoteUseCaseTest {

    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        val userId = UserId("123", "wire.com")

        val userIdDTO = UserIdDTO(userId.value, userId.domain)
        val otherUserClients = listOf(
            SimpleClientResponse("111", DeviceTypeDTO.Phone), SimpleClientResponse("2222", DeviceTypeDTO.Desktop)
        )
        val (arrangement, useCase) = Arrangement()
            .withSuccessfulResponse(userIdDTO, otherUserClients)
            .arrange()

        useCase(listOf(userId))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRemoteRepository.fetchOtherUserClients(any())
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.storeUserClientListAndRemoveRedundantClients(any())
        }

    }

    @Test
    fun givenRepositoryCallFailWithInvalidUserId_thenNoUserFoundReturned() = runTest {
        val userId = UserId("123", "wire.com")
        val noUserFoundException = TestNetworkException.noTeam
        val (arrangement, useCase) = Arrangement()
            .withGetOtherUserClientsErrorResponse(noUserFoundException)
            .arrange()

        useCase.invoke(listOf(userId))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRemoteRepository.fetchOtherUserClients(any())
        }

    }

    private class Arrangement {

        val clientRemoteRepository = mock<ClientRemoteRepository>(mode = MockMode.autoUnit)
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)

        val clientMapper = MapperProvider.clientMapper()

        val fetchUsersClientsFromRemoteUseCase =
            FetchUsersClientsFromRemoteUseCaseImpl(clientRemoteRepository, clientRepository)

        suspend fun withSuccessfulResponse(userIdDTO: UserIdDTO, expectedResponse: List<SimpleClientResponse>): Arrangement {
            everySuspend {
                clientRemoteRepository.fetchOtherUserClients(any())
            } returns Either.Right(mapOf(userIdDTO to expectedResponse))

            everySuspend {
                clientRepository.storeUserClientListAndRemoveRedundantClients(
                    clientMapper.toInsertClientParam(
                        userIdDTO = userIdDTO,
                        simpleClientResponse = expectedResponse
                    )
                )
            } returns Either.Right(Unit)

            return this
        }

        suspend fun withGetOtherUserClientsErrorResponse(exception: KaliumException): Arrangement {
            everySuspend {
                clientRemoteRepository.fetchOtherUserClients(any())
            } returns Either.Left(NetworkFailure.ServerMiscommunication(exception))
            return this
        }

        fun arrange() = this to fetchUsersClientsFromRemoteUseCase
    }
}
