/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetAllSessionsUseCaseTest {

    @Test
    fun givenValidAndInvalidSessions_whenGettingAllSessions_thenReturnAllSessions() = runTest {
        // given
        val validSession = AccountInfo.Valid(UserId("id-valid", "domain"))
        val invalidSession = AccountInfo.Invalid(
            UserId("id-invalid", "domain"),
            LogoutReason.SELF_SOFT_LOGOUT,
        )
        val (_, useCase) = Arrangement()
            .withAllSessions(Either.Right(listOf(validSession, invalidSession)))
            .arrange()
        // when
        val result = useCase()
        // then
        assertIs<GetAllSessionsResult.Success>(result).also {
            assertEquals(listOf(validSession, invalidSession), it.sessions)
        }
    }

    @Test
    fun givenDataNotFound_whenGettingAllSessions_thenReturnNoSessionFound() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withAllSessions(Either.Left(StorageFailure.DataNotFound))
            .arrange()
        // when
        val result = useCase()
        // then
        assertIs<GetAllSessionsResult.Failure.NoSessionFound>(result)
    }

    @Test
    fun givenFailure_whenGettingAllSessions_thenReturnFailure() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withAllSessions(Either.Left(StorageFailure.Generic(Throwable("error"))))
            .arrange()
        // when
        val result = useCase()
        // then
        assertIs<GetAllSessionsResult.Failure.Generic>(result)
    }

    class Arrangement {

        private val sessionRepository = mock(SessionRepository::class)
        private val useCase: GetAllSessionsUseCase by lazy {
            GetAllSessionsUseCase(sessionRepository)
        }

        suspend fun withAllSessions(result: Either<StorageFailure, List<AccountInfo>>) = apply {
            coEvery {
                sessionRepository.allSessions()
            }.returns(result)
        }

        fun arrange() = this to useCase
    }
}
