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
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.PlainId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveClientDetailsUseCaseTest {

    @Mock
    private val clientRepository = mock(ClientRepository::class)

    @Mock
    private val currentClientIdProvider = mock(CurrentClientIdProvider::class)
    private lateinit var observeClientDetailsUseCase: ObserveClientDetailsUseCase
    private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher

    @BeforeTest
    fun setup() = runBlocking {
        observeClientDetailsUseCase = ObserveClientDetailsUseCaseImpl(clientRepository, currentClientIdProvider)
        coEvery {
            currentClientIdProvider.invoke()
        }.returns(Either.Right(CLIENT.id))
    }

    @Test
    fun givenAClientIdSuccess_thenTheSuccessPropagated() = runTest(testDispatchers.io) {
        val expected = CLIENT_RESULT
        coEvery {
            clientRepository.observeClientsByUserIdAndClientId(any(), any())
        }.returns(flowOf(Either.Right(expected)))

        val actual = observeClientDetailsUseCase.invoke(USER_ID, CLIENT_ID).first()
        assertIs<GetClientDetailsResult.Success>(actual)
    }

    @Test
    fun givenClientDetailsFail_thenTheErrorPropagated() = runTest(testDispatchers.io) {
        val expected = StorageFailure.DataNotFound
        coEvery {
            clientRepository.observeClientsByUserIdAndClientId(any(), any())
        }.returns(flowOf(Either.Left(expected)))

        val actual = observeClientDetailsUseCase.invoke(USER_ID, CLIENT_ID).first()
        assertIs<GetClientDetailsResult.Failure.Generic>(actual)
        assertEquals(expected, actual.genericFailure)
    }

    private companion object {
        val USER_ID = UserId("user_id", "domain")
        val CLIENT_ID = PlainId(value = "client_id_1")
        val CLIENT = TestClient.CLIENT
        val CLIENT_RESULT = CLIENT.copy(id = PlainId(value = "client_id_1"))
    }

}
