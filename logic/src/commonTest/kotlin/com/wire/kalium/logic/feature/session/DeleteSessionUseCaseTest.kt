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
import kotlinx.io.IOException
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.sessionRepository.deleteSession(eq(userId))
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.userSessionScopeProvider.delete(eq(userId))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrange.sessionRepository.deleteSession(any())
        }

        verifySuspend(VerifyMode.not) {
            arrange.userSessionScopeProvider.delete(any())
        }
    }

    private class Arrangement {

        val sessionRepository = mock<SessionRepository>(mode = MockMode.autoUnit)
        val userSessionScopeProvider = mock<UserSessionScopeProvider>(mode = MockMode.autoUnit)

        val deleteSessionUseCase = DeleteSessionUseCase(sessionRepository, userSessionScopeProvider)

        suspend fun withSessionDeleteSuccess(userId: UserId): Arrangement = apply {
            everySuspend {
                sessionRepository.deleteSession(eq(userId))
            } returns Either.Right(Unit)

            everySuspend {
                userSessionScopeProvider.delete(eq(userId))
            } returns Unit
        }

        suspend fun withSessionDeleteFailure(userId: UserId, error: StorageFailure): Arrangement = apply {
            everySuspend {
                sessionRepository.deleteSession(eq(userId))
            } returns Either.Left(error)
        }

        fun arrange() = this to deleteSessionUseCase
    }
}
