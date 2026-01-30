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
package com.wire.kalium.logic.feature.applock

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

internal class MarkTeamAppLockStatusAsNotifiedUseCaseTest {

    @Test
    fun givenAppLockStatusChanged_whenMarkingAsNotified_thenSetAppLockAsNotified() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withSuccess()
            .arrange()

        useCase.invoke()

        coVerify {
            arrangement.userConfigRepository.setTeamAppLockAsNotified()
        }.wasInvoked(once)
    }

    class Arrangement {

        val userConfigRepository = mock(UserConfigRepository::class)

        fun arrange() = this to MarkTeamAppLockStatusAsNotifiedUseCaseImpl(
            userConfigRepository = userConfigRepository
        )

        suspend fun withSuccess() = apply {
            coEvery {
                userConfigRepository.setTeamAppLockAsNotified()
            }.returns(Either.Right(Unit))
        }
    }
}
