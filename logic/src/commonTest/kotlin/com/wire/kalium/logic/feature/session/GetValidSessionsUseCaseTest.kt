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

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetValidSessionsUseCaseTest {

    @Test
    fun givenValidSession_whenGettingValidSessions_thenReturnThatSession() = runTest {
        // given
        val validSession = AccountInfo.Valid(UserId("id", "domain"))
        val (_, useCase) = Arrangement()
            .withAllValidSessions(Either.Right(listOf(validSession)))
            .arrange()
        // when
        val result = useCase()
        // then
        assertIs<GetAllSessionsResult.Success>(result).also {
            assertEquals(listOf(validSession), it.sessions)
        }
    }

    @Test
    fun givenDataNotFound_whenGettingValidSessions_thenReturnNoSessionFound() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withAllValidSessions(Either.Left(StorageFailure.DataNotFound))
            .arrange()
        // when
        val result = useCase()
        // then
        assertIs<GetAllSessionsResult.Failure.NoSessionFound>(result)
    }

    @Test
    fun givenFailure_whenGettingValidSessions_thenReturnFailure() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withAllValidSessions(Either.Left(StorageFailure.Generic(Throwable("error"))))
            .arrange()
        // when
        val result = useCase()
        // then
        assertIs<GetAllSessionsResult.Failure.Generic>(result)
    }

    class Arrangement {

        @Mock
        private val sessionRepository = mock(SessionRepository::class)
        private val useCase: GetValidSessionsUseCase by lazy {
            GetValidSessionsUseCase(sessionRepository)
        }

        suspend fun withAllValidSessions(result: Either<StorageFailure, List<AccountInfo.Valid>>) = apply {
            coEvery {
                sessionRepository.allValidSessions()
            }.returns(result)
        }

        fun arrange() = this to useCase
    }
}
