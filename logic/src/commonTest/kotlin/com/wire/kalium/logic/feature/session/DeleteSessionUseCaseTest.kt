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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.common.functional.Either
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeleteSessionUseCaseTest {

    // TODO: re-enable when we have the ability to mock the UserSessionScopeProvider
    @Ignore
    @Test
    fun givenSuccess_WhenDeletingSessionLocally_thenSuccessAndResourcesAreFreed() = runTest {

        val userId = UserId("userId", "domain")
        val (arrange, deleteSessionUseCase) = Arrangement()
            .withSessionDeleteSuccess(userId)
            .arrange()

        deleteSessionUseCase(userId).also { result ->
            assertEquals(DeleteSessionUseCase.Result.Success, result)
        }

        coVerify {
            arrange.sessionRepository.deleteSession(eq(userId))
        }.wasInvoked(exactly = once)

        coVerify {
            arrange.userSessionScopeProvider.delete(eq(userId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenFailure_WhenDeletingSessionLocally_thenReturnFailureAndResourcesAreNotFreed() = runTest {

        val userId = UserId("userId", "domain")
        val error = StorageFailure.Generic(IOException("Failed to delete session"))
        val (arrange, deleteSessionUseCase) = Arrangement()
            .withSessionDeleteFailure(userId, error)
            .arrange()

        deleteSessionUseCase(userId).also { result ->
            assertIs<DeleteSessionUseCase.Result.Failure>(result)
            assertEquals(error, result.cause)
        }

        coVerify {
            arrange.sessionRepository.deleteSession(any())
        }.wasInvoked(exactly = once)

        coVerify {
            arrange.userSessionScopeProvider.delete(any())
        }.wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val userSessionScopeProvider = mock(UserSessionScopeProvider::class)

        val deleteSessionUseCase = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider)

        suspend fun withSessionDeleteSuccess(userId: UserId): Arrangement = apply {
            coEvery {
                sessionRepository.deleteSession(eq(userId))
            }.returns(Either.Right(Unit))

            coEvery {
                userSessionScopeProvider.delete(eq(userId))
            }.returns(Unit)
        }

        suspend fun withSessionDeleteFailure(userId: UserId, error: StorageFailure): Arrangement = apply {
            coEvery {
                sessionRepository.deleteSession(eq(userId))
            }.returns(Either.Left(error))
        }

        fun arrange() = this to deleteSessionUseCase
    }
}
