package com.wire.kalium.logic.feature.client

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ImportClientUseCaseTest {

    @Test
    fun givenClientId_whenInvoking_thenPersistClientIdAsRetainedAndCallGetOrRegisterClientUseCase() = runTest {
        val (arrangement, importClientUse) = Arrangement()
            .withPersistRetainedClientResult(Either.Right(Unit))
            .withGetOrRegisterClientResult(RegisterClientResult.Success(TestClient.CLIENT))
            .arrange()

        val result = importClientUse.invoke(TestClient.CLIENT_ID, Arrangement.REGISTER_CLIENT_PARAM)

        assertIs<RegisterClientResult.Success>(result)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::persistRetainedClientId)
            .with(eq(TestClient.CLIENT_ID))
            .wasInvoked(exactly = once)

        verify(arrangement.getOrRegisterClientUseCase)
            .suspendFunction(arrangement.getOrRegisterClientUseCase::invoke)
            .with(eq(Arrangement.REGISTER_CLIENT_PARAM))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenPersistClientIdFails_whenInvoking_thenFailureIsPropagated() = runTest {
        val (_, importClientUse) = Arrangement()
            .withPersistRetainedClientResult(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = importClientUse.invoke(TestClient.CLIENT_ID, Arrangement.REGISTER_CLIENT_PARAM)

        assertIs<RegisterClientResult.Failure.Generic>(result)
    }

    private class Arrangement {

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val getOrRegisterClientUseCase = mock(classOf<GetOrRegisterClientUseCase>())

        fun withGetOrRegisterClientResult(result: RegisterClientResult): Arrangement = apply {
            given(getOrRegisterClientUseCase)
                .suspendFunction(getOrRegisterClientUseCase::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withPersistRetainedClientResult(result: Either<CoreFailure, Unit>): Arrangement = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::persistRetainedClientId)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to ImportClientUseCaseImpl(clientRepository, getOrRegisterClientUseCase)

        companion object {
            val REGISTER_CLIENT_PARAM = RegisterClientUseCase.RegisterClientParam(
                password = null,
                capabilities = null
            )
        }
    }
}
