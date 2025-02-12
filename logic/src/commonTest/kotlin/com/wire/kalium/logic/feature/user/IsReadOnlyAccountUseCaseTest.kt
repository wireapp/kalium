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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class IsReadOnlyAccountUseCaseTest {
    @Test
    fun givenAUser_NotManagedByWireOrFailure_thenReturnTrue() = runTest(testDispatchers.io) {
        val (arrangement, isReadOnlyAccountUseCase) = Arrangement()
            .withIsReadOnlyAccountRepository(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = isReadOnlyAccountUseCase()
        assertTrue(result)
        coVerify {
            arrangement.sessionRepository.isAccountReadOnly(arrangement.selfUserId)
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUIsNotReadOnlyAccount_ManagedByWire_thenReturnTheValue() = runTest(testDispatchers.io) {
        val (arrangement, isReadOnlyAccountUseCase) = Arrangement()
            .withIsReadOnlyAccountRepository(Either.Right(true))
            .arrange()

        val result = isReadOnlyAccountUseCase()
        assertTrue(result)
        coVerify {
            arrangement.sessionRepository.isAccountReadOnly(arrangement.selfUserId)
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val sessionRepository: SessionRepository = mock(SessionRepository::class)

        val selfUserId = UserId("user_id", "domain")

        suspend fun withIsReadOnlyAccountRepository(isReadOnlyResult: Either<StorageFailure, Boolean>) = apply {
            coEvery {
                sessionRepository.isAccountReadOnly(any())
            }.returns(isReadOnlyResult)
        }

        fun arrange() = this to IsReadOnlyAccountUseCaseImpl(selfUserId, sessionRepository, testDispatchers)
    }

    companion object {
        private val testDispatchers: KaliumDispatcher = TestKaliumDispatcher
    }
}
