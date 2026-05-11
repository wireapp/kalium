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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.data.client.OtherUserClient
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.updateClientProteusVerificationStatus(eq(userId), eq(clientID), eq(true))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.updateClientProteusVerificationStatus(eq(userId), eq(clientID), eq(true))
        }
    }

    @Test
    fun givenError_whenUpdatingTheVerificationStatus_thenReturnSuccess() = runTest {
        val userId = UserId("userId", "domain")
        val clientID = ClientId("clientId")

        val expectedError = StorageFailure.Generic(FileNotFoundException("Oopsie"))

        val (arrangement, useCase) = arrange {
            withUpdateClientProteusVerificationStatus(Either.Left(expectedError))
        }

        useCase(userId, clientID, true).also {
            assertIs<UpdateClientVerificationStatusUseCase.Result.Failure>(it)
            assertEquals(expectedError, it.error)
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.clientRepository.updateClientProteusVerificationStatus(eq(userId), eq(clientID), eq(true))
        }
    }

    private fun arrange(block: suspend Arrangement.() -> Unit) = Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) {
        val clientRepository = mock<ClientRepository>(mode = MockMode.autoUnit)

        fun arrange() = run {
            runBlocking { block() }
            this@Arrangement to UpdateClientVerificationStatusUseCase(
                clientRepository = clientRepository
            )
        }

        suspend fun withUpdateClientProteusVerificationStatus(result: Either<StorageFailure, Unit>) = apply {
            everySuspend {
                clientRepository.updateClientProteusVerificationStatus(any(), any(), any())
            } returns result
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
