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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.feature.user.UpdateSupportedProtocolsAndResolveOneOnOnesUseCase
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class DeleteClientUseCaseTest {

    @Test
    fun givenDeleteClientParams_whenDeleting_thenTheRepositoryShouldBeCalledWithCorrectParameters() = runTest {
        val params = DELETE_CLIENT_PARAMETERS

        val (arrangement, deleteClient) = arrange {
            withDeleteClient(Either.Left(TEST_FAILURE))
        }

        deleteClient(params)

        coVerify {
            arrangement.clientRepository.deleteClient(eq(params))
        }.wasInvoked(once)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToGenericError_whenDeleting_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE
        val (_, deleteClient) = arrange {
            withDeleteClient(Either.Left(genericFailure))
        }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToWrongPassword_whenDeleting_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val wrongPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.invalidCredentials)
        val (_, deleteClient) = arrange {
            withDeleteClient(Either.Left(wrongPasswordFailure))
        }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.InvalidCredentials>(result)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToMissingPassword_whenDeleting_thenPasswordAuthRequiredErrorShouldBeReturned() = runTest {
        val missingPasswordFailure = NetworkFailure.ServerMiscommunication(TestNetworkException.missingAuth)
        val (_, deleteClient) = arrange {
            withDeleteClient(Either.Left(missingPasswordFailure))
        }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.PasswordAuthRequired>(result)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToBadRequest_whenDeleting_thenInvalidCredentialsErrorShouldBeReturned() = runTest {
        val badRequest = NetworkFailure.ServerMiscommunication(TestNetworkException.badRequest)
        val (_, deleteClient) = arrange {
            withDeleteClient(Either.Left(badRequest))
        }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.InvalidCredentials>(result)
    }

    @Test
    fun givenRepositoryDeleteClientSucceeds_whenDeleting_thenUpdateSupportedProtocols() = runTest {
        val (arrangement, deleteClient) = arrange {
            withDeleteClient(Either.Right(Unit))
            withUpdateSupportedProtocolsAndResolveOneOnOnes(Either.Right(Unit))
        }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Success>(result)
        coVerify {
            arrangement.updateSupportedProtocolsAndResolveOneOnOnes.invoke(eq(true))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement(private inline val block: suspend Arrangement.() -> Unit) :
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl() {
        @Mock
        val clientRepository = mock(ClientRepository::class)

        @Mock
        val updateSupportedProtocolsAndResolveOneOnOnes = mock(UpdateSupportedProtocolsAndResolveOneOnOnesUseCase::class)

        suspend fun withDeleteClient(result: Either<NetworkFailure, Unit>) {
            coEvery {
                clientRepository.deleteClient(any())
            }.returns(result)
        }

        suspend fun withUpdateSupportedProtocolsAndResolveOneOnOnes(result: Either<CoreFailure, Unit>) {
            coEvery {
                updateSupportedProtocolsAndResolveOneOnOnes.invoke(any())
            }.returns(result)
        }

        suspend fun arrange() = run {
            block()
            this@Arrangement to DeleteClientUseCaseImpl(
                clientRepository = clientRepository,
                updateSupportedProtocolsAndResolveOneOnOnes = updateSupportedProtocolsAndResolveOneOnOnes,
            )
        }
    }

    private companion object {
        suspend fun arrange(configuration: suspend Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        val CLIENT = TestClient.CLIENT
        val DELETE_CLIENT_PARAMETERS = DeleteClientParam("pass", CLIENT.id)
        val TEST_FAILURE = NetworkFailure.ServerMiscommunication(KaliumException.GenericError(IOException("no internet")))
    }
}
