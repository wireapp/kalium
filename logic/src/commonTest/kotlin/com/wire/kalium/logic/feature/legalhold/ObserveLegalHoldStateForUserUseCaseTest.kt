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
package com.wire.kalium.logic.feature.legalhold

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.DeviceType
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveLegalHoldStateForUserUseCaseTest {

    @Test
    fun givenClientRepositoryFailure_whenObserving_thenReturnDisabledState() = runTest {
        val (_, useCase) = Arrangement()
            .withClientRepositoryFailure()
            .arrange()

        val result = useCase(TestUser.USER_ID)

        assertEquals(LegalHoldState.Disabled, result.first())
    }

    @Test
    fun givenNoClients_whenObserving_thenReturnDisabledState() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withEmptyClients()
            .arrange()

        val result = useCase(TestUser.USER_ID)

        assertEquals(LegalHoldState.Disabled, result.first())
    }

    @Test
    fun givenNoClientIsUnderLegalHold_whenObserving_thenReturnDisabledState() = runTest {
        val (_, useCase) = Arrangement()
            .withNoClientUnderLegalHold()
            .arrange()

        val result = useCase(TestUser.USER_ID)

        assertEquals(LegalHoldState.Disabled, result.first())
    }

    @Test
    fun givenAClientIsUnderLegalHold_whenObserving_thenReturnEnabledState() = runTest {
        val (_, useCase) = Arrangement()
            .withClientUnderLegalHold()
            .arrange()

        val result = useCase(TestUser.USER_ID)

        assertEquals(LegalHoldState.Enabled, result.first())
    }

    private class Arrangement {

        @Mock
        val clientRepository: ClientRepository = mock(ClientRepository::class)

        val observeLegalHoldStateForUser: ObserveLegalHoldStateForUserUseCase =
            ObserveLegalHoldStateForUserUseCaseImpl(
                clientRepository = clientRepository
            )

        fun arrange() = this to observeLegalHoldStateForUser

        suspend fun withClientRepositoryFailure() = apply {
            coEvery {
                clientRepository.observeClientsByUserId(any())
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        suspend fun withEmptyClients() = apply {
            coEvery {
                clientRepository.observeClientsByUserId(any())
            }.returns(flowOf(Either.Right(listOf())))
        }

        suspend fun withNoClientUnderLegalHold() = apply {
            coEvery {
                clientRepository.observeClientsByUserId(any())
            }.returns(flowOf(Either.Right(listOf(TestClient.CLIENT))))
        }

        suspend fun withClientUnderLegalHold() = apply {
            coEvery {
                clientRepository.observeClientsByUserId(any())
            }.returns(flowOf(Either.Right(listOf(legalHoldClient))))
        }
    }

    companion object {
        val legalHoldClient = TestClient.CLIENT.copy(deviceType = DeviceType.LegalHold)
    }
}
