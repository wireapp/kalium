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
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.functional.Either
import io.ktor.utils.io.errors.IOException
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
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

        verify(arrange.sessionRepository)
            .suspendFunction(arrange.sessionRepository::deleteSession)
            .with(eq(userId))
            .wasInvoked(exactly = once)

        verify(arrange.userSessionScopeProvider)
            .function(arrange.userSessionScopeProvider::delete)
            .with(eq(userId))
            .wasInvoked(exactly = once)
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

        verify(arrange.sessionRepository)
            .suspendFunction(arrange.sessionRepository::deleteSession)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrange.userSessionScopeProvider)
            .suspendFunction(arrange.userSessionScopeProvider::delete)
            .with(any())
            .wasNotInvoked()
    }

    private class Arrangement {
        @Mock
        val sessionRepository = mock(classOf<SessionRepository>())

        @Mock
        val userSessionScopeProvider = mock(classOf<UserSessionScopeProvider>())

        val deleteSessionUseCase = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider)

        fun withSessionDeleteSuccess(userId: UserId): Arrangement = apply {
            given(sessionRepository)
                .suspendFunction(sessionRepository::deleteSession)
                .whenInvokedWith(eq(userId))
                .thenReturn(Either.Right(Unit))

            given(userSessionScopeProvider)
                .function(userSessionScopeProvider::delete)
                .whenInvokedWith(eq(userId))
                .thenReturn(Unit)
        }

        fun withSessionDeleteFailure(userId: UserId, error: StorageFailure): Arrangement = apply {
            given(sessionRepository)
                .suspendFunction(sessionRepository::deleteSession)
                .whenInvokedWith(eq(userId))
                .thenReturn(Either.Left(error))
        }

        fun arrange() = this to deleteSessionUseCase
    }
}
