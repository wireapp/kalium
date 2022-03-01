package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeleteClientParam
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
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
            .suspendFunction(clientRepository::deleteClient)
            .whenInvokedWith(anything())
            .then { Either.Left(TEST_FAILURE) }

        deleteClient(params)

        verify(clientRepository)
            .suspendFunction(clientRepository::deleteClient)
            .with(eq(params))
            .wasInvoked(once)
    }

    @Test
    fun givenRepositoryDeleteClientFailsDueToGenericError_whenDeleting_thenGenericErrorShouldBeReturned() = runTest {
        val genericFailure = TEST_FAILURE
        given(clientRepository)
            .suspendFunction(clientRepository::deleteClient)
            .whenInvokedWith(anything())
            .then { Either.Left(genericFailure) }

        val result = deleteClient(DELETE_CLIENT_PARAMETERS)

        assertIs<DeleteClientResult.Failure.Generic>(result)
        assertSame(genericFailure, result.genericFailure)
    }

    private companion object {
        val CLIENT = TestClient.CLIENT
        val DELETE_CLIENT_PARAMETERS = DeleteClientParam("pass", CLIENT.clientId)
        val TEST_FAILURE = NetworkFailure.NoNetworkConnection(KaliumException.NetworkUnavailableError(IOException("no internet")))

    }
}
