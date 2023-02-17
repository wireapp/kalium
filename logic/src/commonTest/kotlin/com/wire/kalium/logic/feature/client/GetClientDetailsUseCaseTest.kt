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

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.Client
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.ClientType
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetClientDetailsUseCaseTest {

    @Mock
    private val clientRepository = configure(mock(classOf<ClientRepository>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())
    private lateinit var getClientDetailsUseCase: GetClientDetailsUseCase

    @BeforeTest
    fun setup() {
        getClientDetailsUseCase = GetClientDetailsUseCaseImpl(clientRepository, provideClientId = currentClientIdProvider)
        given(currentClientIdProvider)
            .suspendFunction(currentClientIdProvider::invoke)
            .whenInvoked()
            .then { Either.Right(CLIENT.id) }
    }

    @Test
    fun givenAClientIdSuccess_thenTheSuccessPropagated() = runTest {
        val expected = CLIENT_RESULT
        given(clientRepository)
            .suspendFunction(clientRepository::clientInfo)
            .whenInvokedWith(any())
            .thenReturn(Either.Right(expected))

        val actual = getClientDetailsUseCase.invoke(CLIENT_ID)
        assertIs<GetClientDetailsResult.Success>(actual)
    }

    @Test
    fun givenClientDetailsFail_thenTheErrorPropagated() = runTest {
        val expected = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("some error")))
        given(clientRepository)
            .suspendFunction(clientRepository::clientInfo)
            .whenInvokedWith(any())
            .thenReturn(Either.Left(expected))

        val actual = getClientDetailsUseCase.invoke(CLIENT_ID)
        assertIs<GetClientDetailsResult.Failure.Generic>(actual)
        assertEquals(expected, actual.genericFailure)
    }

    private companion object {
        val CLIENT_ID = PlainId(value = "client_id_1")
        val CLIENT = Client(
            id = CLIENT_ID,
            type = ClientType.Permanent,
            registrationTime = "2022.01.01",
            location = null,
            deviceType = DeviceType.Desktop,
            label = null,
            cookie = null,
            capabilities = null,
            model = "Mac ox",
            emptyMap()
        )
        val CLIENT_RESULT = CLIENT.copy(id = PlainId(value = "client_id_1"))
    }

}
