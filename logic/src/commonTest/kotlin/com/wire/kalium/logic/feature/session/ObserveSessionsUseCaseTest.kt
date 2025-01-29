/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.session

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveSessionsUseCaseTest {

    @Test
    fun givenValidSession_whenObservingValidSessions_thenEmitThatSession() = runTest {
        // given
        val validSession = AccountInfo.Valid(UserId("id", "domain"))
        val (_, useCase) = Arrangement()
            .withAllValidSessionsFlow(flowOf(Either.Right(listOf(validSession))))
            .arrange()
        // when
        useCase().test {
            val result = awaitItem()
            // then
            assertIs<GetAllSessionsResult.Success>(result).also {
                assertEquals(listOf(validSession), it.sessions)
            }
            cancelAndIgnoreRemainingEvents()
        }

    }

    @Test
    fun givenDataNotFound_whenObservingValidSessions_thenEmitNoSessionFound() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withAllValidSessionsFlow(flowOf(Either.Left(StorageFailure.DataNotFound)))
            .arrange()
        // when
        useCase().test {
            val result = awaitItem()
            // then
            assertIs<GetAllSessionsResult.Failure.NoSessionFound>(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenFailure_whenObservingValidSessions_thenEmitFailure() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withAllValidSessionsFlow(flowOf(Either.Left(StorageFailure.Generic(Throwable("error")))))
            .arrange()
        // when
        useCase().test {
            val result = awaitItem()
            // then
            assertIs<GetAllSessionsResult.Failure.Generic>(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    class Arrangement {

        @Mock
        private val sessionRepository = mock(SessionRepository::class)
        private val useCase: ObserveSessionsUseCase by lazy {
            ObserveSessionsUseCase(sessionRepository)
        }

        suspend fun withAllValidSessionsFlow(result: Flow<Either<StorageFailure, List<AccountInfo.Valid>>>) = apply {
            coEvery {
                sessionRepository.allValidSessionsFlow()
            }.returns(result)
        }

        fun arrange() = this to useCase
    }
}
