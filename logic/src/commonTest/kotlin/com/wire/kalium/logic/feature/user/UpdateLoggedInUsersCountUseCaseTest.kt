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
package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.data.AccessRepository
import io.mockative.Mock
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UpdateLoggedInUsersCountUseCaseTest {

    @Test
    fun givenAccessRepository_whenRunningUseCase_thenUpdateValueOnce() = runTest {
        val expectedValue = 5
        val (arrangement, updateLoggedInUsersCount) = Arrangement()
            .arrange()

        updateLoggedInUsersCount(expectedValue)

        verify(arrangement.accessRepository)
            .coroutine { arrangement.accessRepository.updateLoggedInUsersCount(expectedValue) }
            .wasInvoked(exactly = once)
    }

    private class Arrangement {

        @Mock
        val accessRepository: AccessRepository = mock(AccessRepository::class)

        fun arrange() = this to UpdateLoggedInUsersCountUseCaseImpl(accessRepository)
    }
}
