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
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.repository.ClientRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ClientRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.UserRepositoryArrangementImpl
import io.mockative.any
import io.mockative.eq
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import okio.FileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UpdateClientVerificationStatusUseCaseTest {

    @Test
    fun givenAClientIdAndAUserId_whenUpdatingTheVerificationStatus_thenTheClientRepositoryIsCalled() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val (arrangement, useCase) = arrange {
            withUpdateClientProteusVerificationStatus(Either.Right(Unit))
        }

        useCase(userId, clientID, true)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::updateClientProteusVerificationStatus)
            .with(eq(userId), eq(clientID), eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSuccess_whenUpdatingTheVerificationStatus_thenReturnSuccess() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val (arrangement, useCase) = arrange {
            withUpdateClientProteusVerificationStatus(Either.Right(Unit))
        }

        useCase(userId, clientID, true).also {
            assertIs<UpdateClientVerificationStatusUseCase.Result.Success>(it)
        }

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::updateClientProteusVerificationStatus)
            .with(eq(userId), eq(clientID), eq(true))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenError_whenUpdatingTheVerificationStatus_thenReturnSuccess() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val expectedError = StorageFailure.Generic(FileNotFoundException())

        val (arrangement, useCase) = arrange {
            withUpdateClientProteusVerificationStatus(Either.Left(expectedError))
        }

        useCase(userId, clientID, true).also {
            assertIs<UpdateClientVerificationStatusUseCase.Result.Failure>(it)
            assertEquals(expectedError, it.error)
        }

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::updateClientProteusVerificationStatus)
            .with(eq(userId), eq(clientID), eq(true))
            .wasInvoked(exactly = once)
    }

    private fun arrange(block: Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: Arrangement.() -> Unit
    ) : ClientRepositoryArrangement by ClientRepositoryArrangementImpl() {

        fun arrange() = block().run {
            this@Arrangement to UpdateClientVerificationStatusUseCase(
                clientRepository = clientRepository
            )
        }
    }

    companion object {
        private val OTHER_USER_CLIENT = OtherUserClient(
            deviceType = DeviceType.Phone,
            id = "some_id",
            isValid = true,
            isProteusVerified = true
        )
    }
}
