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
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class ImportClientUseCaseTest {

    @Test
    fun givenClientId_whenInvoking_thenPersistClientIdAsRetainedAndCallGetOrRegisterClientUseCase() = runTest {
        val (arrangement, importClientUse) = Arrangement()
            .withPersistRetainedClientResult(Either.Right(Unit))
            .withGetOrRegisterClientResult(RegisterClientResult.Success(TestClient.CLIENT))
            .arrange()

        val result = importClientUse.invoke(TestClient.CLIENT_ID, Arrangement.REGISTER_CLIENT_PARAM)

        assertIs<RegisterClientResult.Success>(result)

        coVerify {
            arrangement.clientRepository.persistRetainedClientId(eq(TestClient.CLIENT_ID))
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.getOrRegisterClientUseCase.invoke(eq(Arrangement.REGISTER_CLIENT_PARAM))
        }.wasInvoked(exactly = once)
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
        val clientRepository = mock(ClientRepository::class)
        val getOrRegisterClientUseCase = mock(GetOrRegisterClientUseCase::class)

        suspend fun withGetOrRegisterClientResult(result: RegisterClientResult): Arrangement = apply {
            coEvery {
                getOrRegisterClientUseCase.invoke(any())
            }.returns(result)
        }

        suspend fun withPersistRetainedClientResult(result: Either<CoreFailure, Unit>): Arrangement = apply {
            coEvery {
                clientRepository.persistRetainedClientId(any())
            }.returns(result)
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
