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

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class UpdateCurrentSessionUseCaseTest {

    @Mock
    val sessionRepository: SessionRepository = mock(SessionRepository::class)

    lateinit var updateCurrentSessionUseCase: UpdateCurrentSessionUseCase

    @BeforeTest
    fun setup() {
        updateCurrentSessionUseCase = UpdateCurrentSessionUseCase(sessionRepository)
    }

    @Test
    fun givenAUserId_whenUpdateCurrentSessionUseCaseIsInvoked_thenUpdateCurrentSessionIsCalled() = runTest {
        val userId = UserId("user_id", "domain.de")
        coEvery {
            sessionRepository.updateCurrentSession(userId)
        }.returns(Either.Right(Unit))

        updateCurrentSessionUseCase(userId)

        coVerify {
            sessionRepository.updateCurrentSession(userId)
        }.wasInvoked(exactly = once)
    }
}
