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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VerifyExistingClientUseCaseTest {

    @Test
    fun givenRegisteredClientId_whenInvoking_thenReturnSuccess() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (_, useCase) = Arrangement()
            .withSelfClientsResult(Either.Right(listOf(client)))
            .arrange()
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Success>(result)
        assertEquals(client, result.client)
    }

    @Test
    fun givenNotRegisteredClientId_whenInvoking_thenReturnClientNotRegisteredFailure() = runTest {
        val clientId = ClientId("clientId")
        val (arrangement, useCase) = Arrangement()
            .withSelfClientsResult(Either.Right(listOf()))
            .arrange()
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Failure.ClientNotRegistered>(result)
        coVerify {
            arrangement.clientRepository.persistClientId(any())
        }.wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        val verifyExistingClientUseCase: VerifyExistingClientUseCase = VerifyExistingClientUseCaseImpl(clientRepository)

        suspend fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>): Arrangement {
            coEvery {
                clientRepository.selfListOfClients()
            }.returns(result)
            return this
        }

        fun arrange() = this to verifyExistingClientUseCase
    }
}
