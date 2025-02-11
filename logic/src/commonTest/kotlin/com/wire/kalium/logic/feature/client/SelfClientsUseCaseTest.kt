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
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SelfClientsUseCaseTest {

    @Mock
    private val clientRepository = mock(ClientRepository::class)

    @Mock
    private val currentClientIdProvider = mock(CurrentClientIdProvider::class)
    private lateinit var fetchSelfClientsFromRemoteUseCase: FetchSelfClientsFromRemoteUseCase

    @BeforeTest
    fun setup() = runBlocking {
        fetchSelfClientsFromRemoteUseCase =
            FetchSelfClientsFromRemoteUseCaseImpl(clientRepository, provideClientId = currentClientIdProvider)
        coEvery {
            currentClientIdProvider.invoke()
        }.returns(Either.Right(CLIENT.id))
    }

    @Test
    fun givenSelfListOfClientsSuccess_thenTheSuccessPropagated() = runTest {
        val expected = CLIENTS_LIST
        coEvery {
            clientRepository.selfListOfClients()
        }.returns(Either.Right(expected))

        val actual = fetchSelfClientsFromRemoteUseCase.invoke()
        assertIs<SelfClientsResult.Success>(actual)
    }

    @Test
    fun givenSelfListOfClientsSuccess_whenGettingListOfSelfClients_thenTheListIsSortedReverseChronologically() = runTest {
        // given
        val list = listOf(
            CLIENT.copy(registrationTime = Instant.parse("2021-05-12T10:52:02.671Z")),
            CLIENT.copy(registrationTime = Instant.parse("2022-05-12T10:52:02.671Z"))
        )
        val sorted = listOf(list[1], list[0])
        coEvery {
            clientRepository.selfListOfClients()
        }.returns(Either.Right(list))
        // when
        val actual = (fetchSelfClientsFromRemoteUseCase.invoke() as SelfClientsResult.Success)
        // then
        assertEquals(sorted, actual.clients)
    }

    @Test
    fun givenSelfListOfClientsFail_thenTheErrorPropagated() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("some error")))
        coEvery {
            clientRepository.selfListOfClients()
        }.returns(Either.Left(expected))

        val actual = fetchSelfClientsFromRemoteUseCase.invoke()
        assertIs<SelfClientsResult.Failure.Generic>(actual)
        assertEquals(expected, actual.genericFailure)
    }

    private companion object {
        val CLIENT = TestClient.CLIENT
        val CLIENTS_LIST = listOf(
            CLIENT.copy(id = PlainId(value = "client_id_1")),
            CLIENT.copy(id = PlainId(value = "client_id_2"))
        )
    }

}
