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

package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.user.webSocketStatus.PersistPersistentWebSocketConnectionStatusUseCaseImpl
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PersistWebSocketStatusUseCaseTest {

    @Test
    fun givenATrueValue_persistWebSocketInvoked() = runTest {
        val expectedValue = Unit
        val (_, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withSuccessfulResponse()
            .arrange()

        val actual = persistPersistentWebSocketConnectionStatusUseCase(true)
        assertEquals(expectedValue, actual)
    }

    @Test
    fun givenStorageFailure_thenDataNotFoundReturned() = runTest {
        // Given
        val storageFailure = StorageFailure.DataNotFound
        val (arrangement, persistPersistentWebSocketConnectionStatusUseCase) = Arrangement()
            .withPersistWebSocketErrorResponse(storageFailure)
            .arrange()

        // When
        persistPersistentWebSocketConnectionStatusUseCase(true)

        coVerify {
            arrangement.sessionRepository.updatePersistentWebSocketStatus(any(), any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(SessionRepository::class)

        val persistPersistentWebSocketConnectionStatusUseCaseImpl =
            PersistPersistentWebSocketConnectionStatusUseCaseImpl(UserId("test", "domain"), sessionRepository)

        suspend fun withSuccessfulResponse(): Arrangement {
            coEvery {
                sessionRepository.updatePersistentWebSocketStatus(any(), any())
            }.returns(Either.Right(Unit))

            return this
        }

        suspend fun withPersistWebSocketErrorResponse(storageFailure: StorageFailure): Arrangement {
            coEvery {
                sessionRepository.updatePersistentWebSocketStatus(any(), any())
            }.returns(Either.Left(storageFailure))
            return this
        }

        fun arrange() = this to persistPersistentWebSocketConnectionStatusUseCaseImpl
    }

}
