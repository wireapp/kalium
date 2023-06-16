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
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class DeleteClientUseCaseTest {

    @Mock
    private val clientRepository = mock(classOf<ClientRepository>())

    private lateinit var deleteClient: DeleteClientUseCase

    @BeforeTest
    fun setup() {
        deleteClient = DeleteClientUseCaseImpl(clientRepository)
    }

    @Test
    fun givenDeleteClientParams_whenDeleting_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = DELETE_CLIENT_PARAMETERS
        given(clientRepository)
            .suspendFunction(clientRepository::revokeClient)
            .whenInvokedWith(anything())
            .then { Either.Left(TEST_FAILURE) }

        deleteClient(params)

        verify(clientRepository)
            .suspendFunction(clientRepository::revokeClient)
            .with(eq(params))
            .wasInvoked(once)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToGenericError_whenDeleting_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE
        given(clientRepository)
            .suspendFunction(clientRepository::revokeClient)
            .whenInvokedWith(anything())
            .then { Either.Left(genericFailure) }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToWrongPassword_whenDeleting_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)
        given(clientRepository)
            .suspendFunction(clientRepository::revokeClient)
            .whenInvokedWith(anything())
            .then { Either.Left(wrongPasswordFailure) }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.InvalidCredentials>(result)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToMissingPassword_whenDeleting_thenPasswordAuthRequiredErrorShouldBeReturned() = runTest {
        val missingPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)
        given(clientRepository)
            .suspendFunction(clientRepository::revokeClient)
            .whenInvokedWith(anything())
            .then { Either.Left(missingPasswordFailure) }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.PasswordAuthRequired>(result)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToBadRequest_whenDeleting_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val badRequest = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)
        given(clientRepository)
            .suspendFunction(clientRepository::revokeClient)
            .whenInvokedWith(anything())
            .then { Either.Left(badRequest) }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.InvalidCredentials>(result)
    }

    private companion object {
        val CLIENT = TestClient.CLIENT
        val DELETE_CLIENT_PARAMETERS = DeleteClientParam("pass", CLIENT.id)
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))

    }
}
