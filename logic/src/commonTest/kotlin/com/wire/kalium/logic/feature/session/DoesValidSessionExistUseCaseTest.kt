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

package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DoesValidSessionExistUseCaseTest {

    @Test
    fun givenAUserId_whenValidSessionExists_thenReturnSuccessTrue() = runTest {
        val userId = Arrangement.TEST_USER_ID
        val (_, useCase) = Arrangement()
            .withDoesValidSessionExist(userId, true)
            .arrange()
        val result = useCase(userId)
        assertIs<DoesValidSessionExistResult.Success>(result)
        assertTrue { result.doesValidSessionExist }
    }

    @Test
    fun givenAUserId_whenValidSessionDoesNotExists_thenReturnSuccessFalse() = runTest {
        val userId = Arrangement.TEST_USER_ID
        val (_, useCase) = Arrangement()
            .withDoesValidSessionExist(userId, false)
            .arrange()
        val result = useCase(userId)
        assertIs<DoesValidSessionExistResult.Success>(result)
        assertFalse { result.doesValidSessionExist }
    }

    @Test
    fun givenAUserId_whenValidSessionReturnsDataNotFoundFailure_thenReturnSuccessFalse() = runTest {
        val userId = Arrangement.TEST_USER_ID
        val (_, useCase) = Arrangement()
            .withDoesValidSessionExistFailure(StorageFailure.DataNotFound)
            .arrange()
        val result = useCase(userId)
        assertIs<DoesValidSessionExistResult.Success>(result)
        assertFalse { result.doesValidSessionExist }
    }

    @Test
    fun givenAUserId_whenValidSessionReturnsOtherFailure_thenReturnFailure() = runTest {
        val userId = Arrangement.TEST_USER_ID
        val (_, useCase) = Arrangement()
            .withDoesValidSessionExistFailure(StorageFailure.Generic(IOException()))
            .arrange()
        val result = useCase(userId)
        assertIs<DoesValidSessionExistResult.Failure>(result)
    }

    class Arrangement {

        private val sessionRepository = mock(SessionRepository::class)
        private val doesValidSessionExistUseCase: DoesValidSessionExistUseCase by lazy {
            DoesValidSessionExistUseCase(sessionRepository)
        }

        suspend fun withDoesValidSessionExist(userId: UserId, exists: Boolean) = apply {
            coEvery {
                sessionRepository.doesValidSessionExist(eq(userId))
            }.returns(Either.Right(exists))
        }

        suspend fun withDoesValidSessionExistFailure(failure: StorageFailure) = apply {
            coEvery {
                sessionRepository.doesValidSessionExist(any())
            }.returns(Either.Left(failure))
        }

        fun arrange() = this to doesValidSessionExistUseCase

        companion object {
            val TEST_USER_ID = UserId("test", "domain")
        }
    }
}
