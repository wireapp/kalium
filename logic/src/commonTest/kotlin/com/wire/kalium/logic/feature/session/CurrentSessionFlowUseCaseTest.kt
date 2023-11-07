/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentSessionFlowUseCaseTest {
    @Mock
    val sessionRepository = mock(classOf<SessionRepository>())

    lateinit var currentSessionFlowUseCase: CurrentSessionFlowUseCase

    @BeforeTest
    fun setup() {
        currentSessionFlowUseCase = CurrentSessionFlowUseCase(sessionRepository)
    }

    @Test
    fun givenAUserID_whenCurrentSessionFlowEmitsSuccess_thenTheSuccessIsPropagated() = runTest {
        val expected: AccountInfo = TEST_ACCOUNT_INFO

        given(sessionRepository).invocation { currentSessionFlow() }.then { flow { emit(Either.Right(expected)) } }

        currentSessionFlowUseCase().test {
            awaitItem().run {
                assertIs<CurrentSessionResult.Success>(this)
                assertEquals(expected, this.accountInfo)
            }
            awaitComplete()
        }

        verify(sessionRepository).invocation { currentSessionFlow() }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserID_whenCurrentSessionFlowEmitsFailWithNoSessionFound_thenTheErrorIsPropagated() = runTest {
        val expected: StorageFailure = StorageFailure.DataNotFound

        given(sessionRepository).invocation { currentSessionFlow() }.then { flow { emit(Either.Left(expected)) } }

        currentSessionFlowUseCase().test {
            assertIs<CurrentSessionResult.Failure.SessionNotFound>(awaitItem())
            awaitComplete()
        }

        verify(sessionRepository).invocation { currentSessionFlow() }.wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_ACCOUNT_INFO: AccountInfo = AccountInfo.Valid(
            userId = UserId("user_id", "domain")
        )
    }
}
