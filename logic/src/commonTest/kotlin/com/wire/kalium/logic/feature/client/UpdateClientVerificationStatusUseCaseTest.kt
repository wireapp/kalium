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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class UpdateClientVerificationStatusUseCaseTest {

    @Test
    fun givenAClientIdAndAUserId_whenUpdatingTheVerificationStatus_thenTheClientRepositoryIsCalled() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val (arrangement, useCase) = Arrangement()
            .withUpdateClientVerification(Either.Right(Unit))
            .arrange()

        useCase(userId, clientID, true)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::updateClientVerificationStatus)
            .with(eq(userId), eq(clientID), eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenUpdatingTheVerificationStatus_thenReturnSuccess() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val (arrangement, useCase) = Arrangement()
            .withUpdateClientVerification(Either.Right(Unit))
            .arrange()

        useCase(userId, clientID, true).also {
            assertIs<UpdateClientVerificationStatusUseCase.Result.Success>(it)
        }

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::updateClientVerificationStatus)
            .with(eq(userId), eq(clientID), eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenUpdatingTheVerificationStatus_thenReturnSuccess() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val expectedError = StorageFailure.Generic(FileNotFoundException())

        val (arrangement, useCase) = Arrangement()
            .withUpdateClientVerification(Either.Left(expectedError))
            .arrange()

        useCase(userId, clientID, true).also {
            assertIs<UpdateClientVerificationStatusUseCase.Result.Failure>(it)
            assertEquals(expectedError, it.error)
        }

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::updateClientVerificationStatus)
            .with(eq(userId), eq(clientID), eq(true))
            .wasInvoked(exactly = once)
    }
    private class Arrangement {

        @Mock
        val clientRepository = mock(ClientRepository::class)

        private val useCase = UpdateClientVerificationStatusUseCase(clientRepository)

        fun withUpdateClientVerification(result: Either<StorageFailure, Unit>) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::updateClientVerificationStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun arrange() = this to useCase
    }
}
