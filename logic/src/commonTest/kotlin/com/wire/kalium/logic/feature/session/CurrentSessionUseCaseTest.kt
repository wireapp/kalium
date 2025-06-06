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
import com.wire.kalium.logic.data.auth.AccountInfo
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CurrentSessionUseCaseTest {

    val sessionRepository = mock(SessionRepository::class)

    lateinit var currentSessionUseCase: CurrentSessionUseCase

    @BeforeTest
    fun setup() {
        currentSessionUseCase = CurrentSessionUseCase(sessionRepository)
    }

    @Test
    fun givenAUserID_whenCurrentSessionSuccess_thenTheSuccessIsPropagated() = runTest {
        val expected: AccountInfo = TEST_Account_INFO

        coEvery {
            sessionRepository.currentSession()
        }.returns(Either.Right(expected))

        val actual = currentSessionUseCase()

        assertIs<CurrentSessionResult.Success>(actual)
        assertEquals(expected, actual.accountInfo)

        coVerify {
            sessionRepository.currentSession()
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserID_whenCurrentSessionFailWithNoSessionFound_thenTheErrorIsPropagated() = runTest {
        val expected: StorageFailure = StorageFailure.DataNotFound

        coEvery {
            sessionRepository.currentSession()
        }.returns(Either.Left(expected))

        val actual = currentSessionUseCase()

        assertIs<CurrentSessionResult.Failure.SessionNotFound>(actual)

        coVerify {
            sessionRepository.currentSession()
        }.wasInvoked(exactly = once)
    }

    private companion object {
        val TEST_Account_INFO = AccountInfo.Valid(userId = UserId("test", "domain"))
    }
}
