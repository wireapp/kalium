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

import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@ExperimentalCoroutinesApi
class ObserveClientsByUserIdUseCaseTest {
    @Test
    fun givenASuccessfulRepositoryResponse_whenInvokingTheUseCase_thenSuccessResultIsReturned() = runTest {
        // Given
        val userId = UserId("123", "wire.com")
        val clients = listOf(
            TestClient.CLIENT.copy(
                id = ClientId("1111")
            ),
            TestClient.CLIENT.copy(
                id = ClientId("2222")
            )
        )
        val (arrangement, getOtherUsersClientsUseCase) = Arrangement()
            .withSuccessfulResponse(clients)
            .arrange()

        // When
        val result = getOtherUsersClientsUseCase(userId).first()

        assertIs<ObserveClientsByUserIdUseCase.Result.Success>(result)
        assertEquals(clients, result.clients)

        coVerify {
            arrangement.clientRepository.observeClientsByUserId(any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        val getOtherUserClientsUseCaseImpl = ObserveClientsByUserIdUseCase(clientRepository)

        suspend fun withSuccessfulResponse(expectedResponse: List<Client>) = apply {
            coEvery {
                clientRepository.observeClientsByUserId(any())
            }.returns(flowOf(Either.Right(expectedResponse)))
        }

        fun arrange() = this to getOtherUserClientsUseCaseImpl
    }
}
