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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ClientRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ClientRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.IsAllowedToRegisterMLSClientUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.IsAllowedToRegisterMLSClientUseCaseArrangementImpl
import com.wire.kalium.logic.util.arrangement.usecase.RegisterMLSClientUseCaseArrangement
import com.wire.kalium.logic.util.arrangement.usecase.RegisterMLSClientUseCaseArrangementImpl
import com.wire.kalium.util.DelicateKaliumApi
import io.mockative.any
<<<<<<< HEAD
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
=======
import io.mockative.verify
>>>>>>> c7792e365a (fix: Stuck on Setting up Wire after canceling E2EI during login [WPB-10046] (#2882))
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VerifyExistingClientUseCaseTest {

    @Test
    fun givenRegisteredClientIdAndNoMLS_whenInvoking_thenReturnSuccess() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (_, useCase) = arrange {
            withSelfClientsResult(Either.Right(listOf(client)))
            withIsAllowedToRegisterMLSClient(false)
        }
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Success>(result)
        assertEquals(client, result.client)
    }

    @Test
    fun givenRegisteredClientIdAndMLSAllowed_whenRegisterMLSFails_thenReturnFailure() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (_, useCase) = arrange {
            withSelfClientsResult(Either.Right(listOf(client)))
            withIsAllowedToRegisterMLSClient(true)
            withRegisterMLSClient(Either.Left(CoreFailure.Unknown(null)))
        }
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Failure.Generic>(result)
    }

    @Test
    fun givenRegisteredClientIdAndMLSAllowed_whenE2EIRequired_thenReturnE2EIRequiredFailure() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (_, useCase) = arrange {
            withSelfClientsResult(Either.Right(listOf(client)))
            withIsAllowedToRegisterMLSClient(true)
            withRegisterMLSClient(Either.Right(RegisterMLSClientResult.E2EICertificateRequired))
        }
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Failure.E2EICertificateRequired>(result)
    }

    @Test
    fun givenRegisteredClientIdAndMLSAllowed_whenRegisterMLSSucceed_thenReturnSuccess() = runTest {
        val clientId = ClientId("clientId")
        val client = TestClient.CLIENT.copy(id = clientId)
        val (_, useCase) = arrange {
            withSelfClientsResult(Either.Right(listOf(client)))
            withIsAllowedToRegisterMLSClient(true)
            withRegisterMLSClient(Either.Right(RegisterMLSClientResult.Success))
        }
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Success>(result)
        assertEquals(client, result.client)
    }

    @Test
    fun givenNotRegisteredClientId_whenInvoking_thenReturnClientNotRegisteredFailure() = runTest {
        val clientId = ClientId("clientId")
        val (arrangement, useCase) = arrange {
            withSelfClientsResult(Either.Right(listOf()))
        }
        val result = useCase.invoke(clientId)
        assertIs<VerifyExistingClientResult.Failure.ClientNotRegistered>(result)
        coVerify {
            arrangement.clientRepository.persistClientId(any())
        }.wasNotInvoked()
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

<<<<<<< HEAD
        @Mock
        val clientRepository = mock(ClientRepository::class)
=======
    @OptIn(DelicateKaliumApi::class)
    private class Arrangement(private val block: Arrangement.() -> Unit) :
        RegisterMLSClientUseCaseArrangement by RegisterMLSClientUseCaseArrangementImpl(),
        ClientRepositoryArrangement by ClientRepositoryArrangementImpl(),
        IsAllowedToRegisterMLSClientUseCaseArrangement by IsAllowedToRegisterMLSClientUseCaseArrangementImpl() {
>>>>>>> c7792e365a (fix: Stuck on Setting up Wire after canceling E2EI during login [WPB-10046] (#2882))

        fun arrange() = run {
            block()

<<<<<<< HEAD
        suspend fun withSelfClientsResult(result: Either<NetworkFailure, List<Client>>): Arrangement {
            coEvery {
                clientRepository.selfListOfClients()
            }.returns(result)
            return this
=======
            this@Arrangement to VerifyExistingClientUseCaseImpl(
                TestUser.USER_ID,
                clientRepository,
                isAllowedToRegisterMLSClientUseCase,
                registerMLSClientUseCase
            )
>>>>>>> c7792e365a (fix: Stuck on Setting up Wire after canceling E2EI during login [WPB-10046] (#2882))
        }
    }
}
